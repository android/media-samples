/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.example.studiosound.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Decodes the first audio track of anything MediaExtractor/MediaCodec can read (MP3, AAC/M4A, WAV,
 * FLAC, Ogg/Opus, MP4 video, ...) into a [PcmFile] that satisfies Studio Sound's input constraints:
 * 32-bit float, 1 or 2 channels and a sample rate between 8 and 48 kHz.
 */
object AudioDecoder {

  /** Studio Sound accepts sample rates in this range. */
  const val MIN_SAMPLE_RATE = 8_000
  const val MAX_SAMPLE_RATE = 48_000

  private const val TIMEOUT_US = 10_000L

  data class Result(
    val pcm: PcmFile,
    /** Presentation time of the first decoded sample. Used to keep A/V sync when re-muxing. */
    val startTimeUs: Long,
    val sourceMime: String,
    val sourceSampleRate: Int,
    val sourceChannelCount: Int,
  )

  suspend fun decode(
    context: Context,
    uri: Uri,
    output: File,
    onProgress: (Float) -> Unit = {},
  ): Result = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(context, uri, null)
      val trackIndex =
        (0 until extractor.trackCount).firstOrNull { index ->
          extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("This file doesn't contain an audio track.")
      val trackFormat = extractor.getTrackFormat(trackIndex)
      val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
      val durationUs =
        if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) trackFormat.getLong(MediaFormat.KEY_DURATION) else 0L
      extractor.selectTrack(trackIndex)

      val codec = createDecoder(mime, trackFormat)
      var sink: PcmSink? = null
      var startTimeUs = Long.MIN_VALUE
      try {
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        PcmWriter(output).use { writer ->
          while (!outputDone) {
            ensureActive()
            if (!inputDone) {
              val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
              if (inIndex >= 0) {
                val inBuffer = codec.getInputBuffer(inIndex) ?: throw IOException("Decoder input buffer unavailable")
                val size = extractor.readSampleData(inBuffer, 0)
                if (size < 0) {
                  codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                  inputDone = true
                } else {
                  codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                  extractor.advance()
                }
              }
            }

            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
              sink = PcmSink.create(codec.outputFormat, sink, writer)
            } else if (outIndex >= 0) {
              if (info.size > 0) {
                val activeSink =
                  sink ?: PcmSink.create(codec.getOutputFormat(outIndex), null, writer).also { sink = it }
                if (startTimeUs == Long.MIN_VALUE) startTimeUs = info.presentationTimeUs
                val outBuffer = codec.getOutputBuffer(outIndex) ?: throw IOException("Decoder output buffer unavailable")
                outBuffer.position(info.offset)
                outBuffer.limit(info.offset + info.size)
                activeSink.consume(outBuffer.slice().order(ByteOrder.nativeOrder()))
                if (durationUs > 0) {
                  onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                }
              }
              codec.releaseOutputBuffer(outIndex, false)
              if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
          }
          sink?.finish()
        }
      } finally {
        runCatching { codec.stop() }
        codec.release()
      }

      val target = sink?.target ?: throw IOException("The audio track couldn't be decoded.")
      val pcm = PcmFile(output, target.sampleRate, target.channelCount)
      if (pcm.frameCount == 0L) throw IOException("The audio track is empty.")
      onProgress(1f)
      Result(
        pcm = pcm,
        startTimeUs = startTimeUs.coerceAtLeast(0L),
        sourceMime = mime,
        sourceSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        sourceChannelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
      )
    } finally {
      extractor.release()
    }
  }

  private fun createDecoder(mime: String, trackFormat: MediaFormat): MediaCodec {
    // For raw PCM (WAV) the track's KEY_PCM_ENCODING describes the *input*, so leave it untouched.
    if (mime != MediaFormat.MIMETYPE_AUDIO_RAW) {
      // Prefer float output (no lossy 16-bit round-trip) and let the decoder downmix multichannel
      // streams to stereo using the stream's own downmix metadata. Not every decoder supports this,
      // so fall back to the plain track format if configuration fails.
      val preferred = MediaFormat(trackFormat).apply {
        setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
        setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, 2)
      }
      val codec = MediaCodec.createDecoderByType(mime)
      try {
        codec.configure(preferred, null, null, 0)
        return codec
      } catch (e: Exception) {
        codec.release()
      }
    }
    val codec = MediaCodec.createDecoderByType(mime)
    try {
      codec.configure(trackFormat, null, null, 0)
    } catch (e: Exception) {
      codec.release()
      throw IOException("No decoder available for $mime", e)
    }
    return codec
  }
}

/** The PCM format written to disk. Fixed by the first output format the decoder reports. */
internal data class TargetFormat(val sampleRate: Int, val channelCount: Int)

/** Converts decoder output (any PCM encoding, channel count and rate) to the [target] format. */
private class PcmSink(
  private val inputEncoding: Int,
  private val inputChannels: Int,
  inputRate: Int,
  val target: TargetFormat,
  private val writer: PcmWriter,
) {
  private val resampler =
    if (inputRate != target.sampleRate) LinearResampler(target.channelCount, inputRate, target.sampleRate) else null
  private var decoded = FloatArray(0)
  private var remixed = FloatArray(0)

  fun consume(data: ByteBuffer) {
    val frames = data.remaining() / (bytesPerSample(inputEncoding) * inputChannels)
    if (frames == 0) return
    val sampleCount = frames * inputChannels
    if (decoded.size < sampleCount) decoded = FloatArray(sampleCount)
    readSamples(data, inputEncoding, decoded, sampleCount)

    val output =
      if (inputChannels == target.channelCount) {
        decoded
      } else {
        val needed = frames * target.channelCount
        if (remixed.size < needed) remixed = FloatArray(needed)
        downmixToStereo(decoded, frames, inputChannels, remixed)
        remixed
      }
    if (resampler != null) {
      resampler.process(output, frames, writer)
    } else {
      writer.write(output, 0, frames * target.channelCount)
    }
  }

  fun finish() {
    resampler?.flush(writer)
  }

  companion object {
    fun create(format: MediaFormat, previous: PcmSink?, writer: PcmWriter): PcmSink {
      val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      val encoding =
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
          format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
          AudioFormat.ENCODING_PCM_16BIT
        }
      previous?.finish()
      // Keep writing in the format chosen first, even if the decoder changes its output mid-stream.
      val target =
        previous?.target
          ?: TargetFormat(
            sampleRate = rate.coerceIn(AudioDecoder.MIN_SAMPLE_RATE, AudioDecoder.MAX_SAMPLE_RATE),
            channelCount = channels.coerceAtMost(2),
          )
      return PcmSink(encoding, channels, rate, target, writer)
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
      AudioFormat.ENCODING_PCM_8BIT -> 1
      AudioFormat.ENCODING_PCM_16BIT -> 2
      AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
      AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
      else -> throw IOException("Unsupported PCM encoding: $encoding")
    }

    /** Reads [count] samples from [data] (native byte order) as floats in [-1, 1]. */
    private fun readSamples(data: ByteBuffer, encoding: Int, out: FloatArray, count: Int) {
      when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> data.asFloatBuffer().get(out, 0, count)
        AudioFormat.ENCODING_PCM_16BIT -> {
          val shorts = data.asShortBuffer()
          for (i in 0 until count) out[i] = shorts.get(i) / 32768f
        }
        AudioFormat.ENCODING_PCM_8BIT -> {
          val base = data.position()
          for (i in 0 until count) out[i] = ((data.get(base + i).toInt() and 0xFF) - 128) / 128f
        }
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
          val base = data.position()
          for (i in 0 until count) {
            val p = base + i * 3
            val value =
              (data.get(p).toInt() and 0xFF) or
                ((data.get(p + 1).toInt() and 0xFF) shl 8) or
                (data.get(p + 2).toInt() shl 16)
            out[i] = value / 8388608f
          }
        }
        AudioFormat.ENCODING_PCM_32BIT -> {
          val ints = data.asIntBuffer()
          for (i in 0 until count) out[i] = ints.get(i) / 2147483648f
        }
        else -> throw IOException("Unsupported PCM encoding: $encoding")
      }
    }

    /** Downmixes 3+ channels to stereo (ITU-style coefficients for 5.1, a simple fold otherwise). */
    private fun downmixToStereo(input: FloatArray, frames: Int, inChannels: Int, output: FloatArray) {
      if (inChannels == 6) {
        // 5.1 order: FL, FR, FC, LFE, BL, BR. LFE is dropped.
        val c = 0.7071f
        val norm = 1f / (1f + c + c)
        for (f in 0 until frames) {
          val i = f * 6
          output[f * 2] = (input[i] + c * input[i + 2] + c * input[i + 4]) * norm
          output[f * 2 + 1] = (input[i + 1] + c * input[i + 2] + c * input[i + 5]) * norm
        }
      } else {
        // First two channels are L/R; everything else is folded equally into both.
        val norm = 1f / (1f + 0.5f * (inChannels - 2))
        for (f in 0 until frames) {
          val i = f * inChannels
          var rest = 0f
          for (ch in 2 until inChannels) rest += input[i + ch]
          output[f * 2] = (input[i] + 0.5f * rest) * norm
          output[f * 2 + 1] = (input[i + 1] + 0.5f * rest) * norm
        }
      }
    }
  }
}

/**
 * Streaming linear-interpolation resampler. Only used for sources outside Studio Sound's supported
 * 8–48 kHz range (e.g. 96 kHz FLAC), which are rare.
 */
private class LinearResampler(private val channels: Int, inputRate: Int, outputRate: Int) {
  private val step = inputRate.toDouble() / outputRate

  /** Input-frame position of the next output frame, relative to the current block (may be in [-1, 0)). */
  private var position = 0.0

  /** Last input frame of the previous block (index -1 of the current block). */
  private val previous = FloatArray(channels)
  private var out = FloatArray(0)

  fun process(input: FloatArray, frames: Int, writer: PcmWriter) {
    if (frames <= 0) return
    val maxOutput = ((frames + 1) / step).toInt() + 2
    if (out.size < maxOutput * channels) out = FloatArray(maxOutput * channels)
    var produced = 0
    var p = position
    while (p < frames - 1) {
      val i0 = floor(p).toInt()
      val frac = (p - i0).toFloat()
      for (c in 0 until channels) {
        val a = if (i0 < 0) previous[c] else input[i0 * channels + c]
        val b = input[(i0 + 1) * channels + c]
        out[produced * channels + c] = a + (b - a) * frac
      }
      produced++
      p += step
    }
    for (c in 0 until channels) previous[c] = input[(frames - 1) * channels + c]
    position = p - frames
    writer.write(out, 0, produced * channels)
  }

  fun flush(writer: PcmWriter) {
    // Emit the (at most one or two) output frames that fall after the very last input frame.
    while (position < 0) {
      writer.write(previous, 0, channels)
      position += step
    }
  }
}
