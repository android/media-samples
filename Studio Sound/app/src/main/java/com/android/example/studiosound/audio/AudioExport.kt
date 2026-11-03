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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Computes compact peak envelopes of PCM files for waveform previews. */
object Waveform {
  const val DEFAULT_BUCKETS = 120

  suspend fun peaks(pcm: PcmFile, buckets: Int = DEFAULT_BUCKETS): FloatArray = withContext(Dispatchers.IO) {
    val peaks = FloatArray(buckets)
    val totalFrames = pcm.frameCount
    if (totalFrames == 0L) return@withContext peaks
    val channels = pcm.channelCount
    val blockFrames = 32_768
    val buffer = ByteBuffer.allocateDirect(blockFrames * pcm.bytesPerFrame).order(ByteOrder.nativeOrder())
    val samples = FloatArray(blockFrames * channels)
    var frameIndex = 0L
    RandomAccessFile(pcm.file, "r").channel.use { channel ->
      while (true) {
        ensureActive()
        buffer.clear()
        while (buffer.hasRemaining()) {
          if (channel.read(buffer) < 0) break
        }
        val frames = buffer.position() / pcm.bytesPerFrame
        if (frames == 0) break
        buffer.flip()
        buffer.asFloatBuffer().get(samples, 0, frames * channels)
        for (f in 0 until frames) {
          val bucket = ((frameIndex + f) * buckets / totalFrames).toInt().coerceAtMost(buckets - 1)
          var peak = peaks[bucket]
          for (c in 0 until channels) {
            val v = abs(samples[f * channels + c])
            if (v > peak) peak = v
          }
          peaks[bucket] = peak
        }
        frameIndex += frames
      }
    }
    peaks
  }

  /**
   * Normalizes several envelopes against their shared maximum (so relative loudness is preserved)
   * and applies a square-root curve so quiet stems remain visible.
   */
  fun normalizeTogether(envelopes: List<FloatArray>): List<FloatArray> {
    val max = envelopes.maxOfOrNull { env -> env.maxOrNull() ?: 0f }?.takeIf { it > 0f } ?: return envelopes
    return envelopes.map { env -> FloatArray(env.size) { i -> sqrt((env[i] / max).coerceIn(0f, 1f)) } }
  }
}

/** Writes 16-bit PCM WAV files from one or more mixed [PcmFile]s. */
object WavWriter {

  suspend fun write(out: OutputStream, tracks: List<PcmFile>, gains: FloatArray) = withContext(Dispatchers.IO) {
    MixReader(tracks).use { reader ->
      val channels = reader.channelCount
      val stream = BufferedOutputStream(out, 64 * 1024)
      writeHeader(stream, reader.sampleRate, channels, reader.frameCount * channels * 2)
      val block = FloatArray(reader.maxBlockFrames * channels)
      val bytes = ByteArray(block.size * 2)
      while (true) {
        ensureActive()
        val frames = reader.read(block, reader.maxBlockFrames, gains)
        if (frames == 0) break
        val count = frames * channels
        softClip(block, count)
        var j = 0
        for (i in 0 until count) {
          val s = (block[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
          bytes[j++] = (s and 0xFF).toByte()
          bytes[j++] = ((s shr 8) and 0xFF).toByte()
        }
        stream.write(bytes, 0, j)
      }
      stream.flush()
    }
  }

  private fun writeHeader(out: OutputStream, sampleRate: Int, channels: Int, dataBytes: Long) {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt((36 + dataBytes).toInt())
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // fmt chunk size
    header.putShort(1.toShort()) // PCM
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(sampleRate * channels * 2) // byte rate
    header.putShort((channels * 2).toShort()) // block align
    header.putShort(16.toShort()) // bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataBytes.toInt())
    out.write(header.array())
  }
}
