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

import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Reads several [PcmFile]s that share one format in lock-step and mixes them with per-track gains.
 *
 * Used both for real-time playback ([StemPlayer]) and offline rendering (WAV export and the Studio
 * Sound video soundtrack). Tracks whose gain is zero are skipped entirely (no I/O).
 */
class MixReader(
  private val tracks: List<PcmFile>,
  val maxBlockFrames: Int = DEFAULT_BLOCK_FRAMES,
) : Closeable {

  init {
    require(tracks.isNotEmpty()) { "At least one track is required" }
    val first = tracks.first()
    require(tracks.all { it.sampleRate == first.sampleRate && it.channelCount == first.channelCount }) {
      "All tracks must share the same sample rate and channel count"
    }
  }

  val sampleRate: Int = tracks.first().sampleRate
  val channelCount: Int = tracks.first().channelCount
  val trackCount: Int = tracks.size
  val frameCount: Long = tracks.maxOf { it.frameCount }

  private val bytesPerFrame = channelCount * Float.SIZE_BYTES
  private val channels: List<FileChannel> = tracks.map { RandomAccessFile(it.file, "r").channel }
  private val readBuffer: ByteBuffer =
    ByteBuffer.allocateDirect(maxBlockFrames * bytesPerFrame).order(ByteOrder.nativeOrder())
  private val scratch = FloatArray(maxBlockFrames * channelCount)

  var position: Long = 0
    private set

  fun seek(frame: Long) {
    position = frame.coerceIn(0L, frameCount)
  }

  /**
   * Mixes up to [maxFrames] frames into [out] (interleaved). Gains ramp linearly from [startGains]
   * to [endGains] across the block, which makes live gain changes click-free.
   *
   * @return the number of frames produced; 0 at the end of the stream.
   */
  fun read(out: FloatArray, maxFrames: Int, startGains: FloatArray, endGains: FloatArray = startGains): Int {
    val frames = minOf(maxFrames.toLong(), maxBlockFrames.toLong(), frameCount - position).toInt()
    if (frames <= 0) return 0
    val samples = frames * channelCount
    out.fill(0f, 0, samples)
    val byteOffset = position * bytesPerFrame
    for (t in 0 until trackCount) {
      val g0 = startGains[t]
      val g1 = endGains[t]
      if (g0 == 0f && g1 == 0f) continue
      readBuffer.clear()
      readBuffer.limit(samples * Float.SIZE_BYTES)
      var filePosition = byteOffset
      while (readBuffer.hasRemaining()) {
        val read = channels[t].read(readBuffer, filePosition)
        if (read <= 0) break
        filePosition += read
      }
      val validFrames = readBuffer.position() / bytesPerFrame
      if (validFrames == 0) continue
      readBuffer.flip()
      val validSamples = validFrames * channelCount
      readBuffer.asFloatBuffer().get(scratch, 0, validSamples)
      if (g0 == g1) {
        for (i in 0 until validSamples) out[i] += scratch[i] * g0
      } else {
        val delta = (g1 - g0) / frames
        var i = 0
        for (f in 0 until validFrames) {
          val g = g0 + delta * f
          for (c in 0 until channelCount) {
            out[i] += scratch[i] * g
            i++
          }
        }
      }
    }
    position += frames
    return frames
  }

  override fun close() {
    channels.forEach { runCatching { it.close() } }
  }

  companion object {
    const val DEFAULT_BLOCK_FRAMES = 4_096
  }
}
