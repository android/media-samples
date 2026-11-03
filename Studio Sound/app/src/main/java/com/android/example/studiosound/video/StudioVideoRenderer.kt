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
package com.android.example.studiosound.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.android.example.studiosound.audio.MixReader
import com.android.example.studiosound.audio.PcmFile
import com.android.example.studiosound.audio.framesToUs
import com.android.example.studiosound.audio.softClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Produces "the video with Studio Sound applied": copies the original video track untouched (no
 * re-encode) and replaces the soundtrack with an AAC encode of the Studio Sound mix.
 */
object StudioVideoRenderer {

  private const val TIMEOUT_US = 10_000L
  private const val INPUT_FRAMES = 2_048

  /**
   * @param tracks stems to mix (same format), with one gain per stem in [gains].
   * @param audioStartTimeUs presentation time of the original audio's first sample, to keep A/V sync.
   */
  suspend fun render(
    sourceVideo: File,
    tracks: List<PcmFile>,
    gains: FloatArray,
    audioStartTimeUs: Long,
    output: File,
    onProgress: (Float) -> Unit = {},
  ) = withContext(Dispatchers.IO) {
    val audio = MixReader(tracks).use { reader -> encodeAac(reader, gains) { onProgress(it * 0.85f) } }
    // Write to a temp file and rename, so a player reading the previous render never sees a
    // half-written file.
    val temp = File(output.parentFile, "${output.name}.tmp")
    try {
      mux(sourceVideo, audio, audioStartTimeUs, temp) { onProgress(0.85f + it * 0.15f) }
      if (!temp.renameTo(output)) throw IOException("Couldn't write ${output.name}")
    } finally {
      temp.delete()
    }
    onProgress(1f)
  }

  private class EncodedSample(val data: ByteArray, val presentationTimeUs: Long, val flags: Int)

  private class EncodedAudio(val format: MediaFormat, val samples: List<EncodedSample>)

  private fun CoroutineScope.encodeAac(reader: MixReader, gains: FloatArray, onProgress: (Float) -> Unit): EncodedAudio {
    val sampleRate = reader.sampleRate
    val channels = reader.channelCount
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, if (channels == 1) 128_000 else 192_000)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_FRAMES * channels * 2)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    try {
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      codec.start()
      val mix = FloatArray(INPUT_FRAMES * channels)
      val pcm16 = ShortArray(INPUT_FRAMES * channels)
      val samples = ArrayList<EncodedSample>()
      var outputFormat: MediaFormat? = null
      val info = MediaCodec.BufferInfo()
      var framesQueued = 0L
      var inputDone = false
      var outputDone = false
      while (!outputDone) {
        ensureActive()
        if (!inputDone) {
          val index = codec.dequeueInputBuffer(TIMEOUT_US)
          if (index >= 0) {
            val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.nativeOrder())
            buffer.clear()
            val capacityFrames = buffer.remaining() / (channels * 2)
            val frames = reader.read(mix, minOf(capacityFrames, INPUT_FRAMES), gains)
            val ptsUs = framesToUs(framesQueued, sampleRate)
            if (frames == 0) {
              codec.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              val count = frames * channels
              softClip(mix, count)
              for (i in 0 until count) pcm16[i] = (mix[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
              buffer.asShortBuffer().put(pcm16, 0, count)
              codec.queueInputBuffer(index, 0, count * 2, ptsUs, 0)
              framesQueued += frames
              onProgress(framesQueued.toFloat() / reader.frameCount)
            }
          }
        }

        val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          outputFormat = codec.outputFormat
        } else if (index >= 0) {
          val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
          if (info.size > 0 && !isConfig) {
            val buffer = codec.getOutputBuffer(index)!!
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            val bytes = ByteArray(info.size)
            buffer.get(bytes)
            samples += EncodedSample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
          }
          codec.releaseOutputBuffer(index, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
        }
      }
      return EncodedAudio(outputFormat ?: throw IOException("The AAC encoder produced no output"), samples)
    } finally {
      runCatching { codec.stop() }
      codec.release()
    }
  }

  private fun CoroutineScope.mux(
    sourceVideo: File,
    audio: EncodedAudio,
    audioOffsetUs: Long,
    output: File,
    onProgress: (Float) -> Unit,
  ) {
    val extractor = MediaExtractor()
    val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var started = false
    try {
      extractor.setDataSource(sourceVideo.absolutePath)
      val videoTrack =
        (0 until extractor.trackCount).firstOrNull {
          extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: throw IOException("The recording has no video track.")
      val videoFormat = extractor.getTrackFormat(videoTrack)
      extractor.selectTrack(videoTrack)
      val durationUs =
        if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) videoFormat.getLong(MediaFormat.KEY_DURATION) else 0L

      muxer.setOrientationHint(readRotation(sourceVideo, videoFormat))
      val videoIndex = muxer.addTrack(videoFormat)
      val audioIndex = muxer.addTrack(audio.format)
      muxer.start()
      started = true

      var buffer = ByteBuffer.allocateDirect(1 shl 20)
      val info = MediaCodec.BufferInfo()
      var nextAudio = 0
      // Interleave samples by timestamp.
      while (true) {
        ensureActive()
        val videoTimeUs = extractor.sampleTime
        val audioSample = audio.samples.getOrNull(nextAudio)
        val audioTimeUs = audioSample?.let { it.presentationTimeUs + audioOffsetUs } ?: Long.MAX_VALUE
        if (videoTimeUs < 0 && audioSample == null) break

        if (videoTimeUs >= 0 && videoTimeUs <= audioTimeUs) {
          val size = extractor.sampleSize.toInt()
          if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect(size)
          buffer.clear()
          val read = extractor.readSampleData(buffer, 0)
          if (read >= 0) {
            val flags =
              if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            info.set(0, read, videoTimeUs, flags)
            muxer.writeSampleData(videoIndex, buffer, info)
          }
          extractor.advance()
          if (durationUs > 0) onProgress((videoTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
        } else {
          val sample = audioSample!!
          if (sample.data.size > buffer.capacity()) buffer = ByteBuffer.allocateDirect(sample.data.size)
          buffer.clear()
          buffer.put(sample.data)
          buffer.flip()
          info.set(0, sample.data.size, audioTimeUs, sample.flags)
          muxer.writeSampleData(audioIndex, buffer, info)
          nextAudio++
        }
      }
      muxer.stop()
      started = false
    } finally {
      if (started) runCatching { muxer.stop() }
      runCatching { muxer.release() }
      extractor.release()
    }
  }

  private fun readRotation(file: File, format: MediaFormat): Int {
    val degrees =
      if (format.containsKey(MediaFormat.KEY_ROTATION)) {
        format.getInteger(MediaFormat.KEY_ROTATION)
      } else {
        MediaMetadataRetriever().use { retriever ->
          retriever.setDataSource(file.absolutePath)
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        }
      }
    return ((degrees % 360 + 360) % 360).takeIf { it in setOf(0, 90, 180, 270) } ?: 0
  }
}

/** Where recordings and renders live, plus exporting to the shared gallery. */
object VideoStorage {

  fun recordingsDir(context: Context): File = File(context.cacheDir, "recordings").apply { mkdirs() }

  fun newRecordingFile(context: Context): File {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(recordingsDir(context), "VID_$stamp.mp4")
  }

  /** Deletes previous recordings and their renders (keeps the cache from growing unbounded). */
  fun clearRecordings(context: Context) {
    recordingsDir(context).listFiles()?.forEach { it.deleteRecursively() }
  }

  /** Copies [source] into Movies/StudioSound via MediaStore (no storage permission needed). */
  fun saveToGallery(context: Context, source: File): Uri {
    val resolver = context.contentResolver
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
      put(MediaStore.Video.Media.DISPLAY_NAME, "StudioSound_$stamp.mp4")
      put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
      put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/StudioSound")
      put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create a gallery entry")
    try {
      resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
        ?: throw IOException("Couldn't open the gallery entry")
      values.clear()
      values.put(MediaStore.Video.Media.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
      return uri
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      throw e
    }
  }
}
