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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/**
 * A file of raw, interleaved, 32-bit float PCM samples in the device's native byte order.
 *
 * This is the interchange format used throughout the app: decoded sources, every stem returned by
 * Studio Sound and rendered mixes are all stored this way, so long recordings never have to be held
 * in memory. Native byte order matches Studio Sound's shared-memory buffers, which lets us copy
 * bytes between files and the service without any conversion.
 */
data class PcmFile(
  val file: File,
  val sampleRate: Int,
  val channelCount: Int,
) {
  init {
    require(sampleRate > 0) { "sampleRate must be > 0" }
    require(channelCount > 0) { "channelCount must be > 0" }
  }

  val bytesPerFrame: Int get() = channelCount * Float.SIZE_BYTES
  val frameCount: Long get() = file.length() / bytesPerFrame
  val durationMs: Long get() = framesToMs(frameCount, sampleRate)
}

fun framesToMs(frames: Long, sampleRate: Int): Long = frames * 1_000L / sampleRate

fun framesToUs(frames: Long, sampleRate: Int): Long = frames * 1_000_000L / sampleRate

fun msToFrames(ms: Long, sampleRate: Int): Long = ms * sampleRate / 1_000L

/** Streams float samples into a [PcmFile]-formatted file (interleaved, native byte order). */
class PcmWriter(file: File) : Closeable {
  private val channel: FileChannel = FileOutputStream(file).channel
  private val buffer: ByteBuffer =
    ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder())

  fun write(samples: FloatArray, offset: Int = 0, count: Int = samples.size - offset) {
    var off = offset
    var remaining = count
    while (remaining > 0) {
      if (buffer.remaining() < Float.SIZE_BYTES) flush()
      val n = minOf(remaining, buffer.remaining() / Float.SIZE_BYTES)
      buffer.asFloatBuffer().put(samples, off, n)
      buffer.position(buffer.position() + n * Float.SIZE_BYTES)
      off += n
      remaining -= n
    }
  }

  private fun flush() {
    buffer.flip()
    while (buffer.hasRemaining()) channel.write(buffer)
    buffer.clear()
  }

  override fun close() {
    try {
      flush()
    } finally {
      channel.close()
    }
  }

  private companion object {
    const val BUFFER_BYTES = 256 * 1024
  }
}

private const val SOFT_CLIP_KNEE = 0.9f

/**
 * Transparent below [SOFT_CLIP_KNEE]; smoothly saturates louder samples so that boosted stems don't
 * hard-clip.
 */
internal fun softClip(samples: FloatArray, count: Int) {
  for (i in 0 until count) {
    val x = samples[i]
    val a = abs(x)
    if (a > SOFT_CLIP_KNEE) {
      val headroom = 1f - SOFT_CLIP_KNEE
      samples[i] = sign(x) * (SOFT_CLIP_KNEE + headroom * tanh((a - SOFT_CLIP_KNEE) / headroom))
    }
  }
}
