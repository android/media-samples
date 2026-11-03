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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Real-time multi-track player: mixes several [PcmFile]s (same format) with live, click-free gain
 * changes and plays the result through an [AudioTrack].
 *
 * All AudioTrack control happens on the player's own thread; public methods only record the desired
 * state, so a blocking write can never deadlock with pause/seek.
 */
class StemPlayer(
  tracks: List<PcmFile>,
  private val onPlaybackEnded: () -> Unit = {},
) : Closeable {

  private val sampleRate = tracks.first().sampleRate
  private val channelCount = tracks.first().channelCount
  private val blockFrames = (sampleRate / 50).coerceAtLeast(256) // ~20 ms
  private val reader = MixReader(tracks, blockFrames)
  private val trackCount = tracks.size

  val durationMs: Long = framesToMs(reader.frameCount, sampleRate)

  private val lock = ReentrantLock()
  private val wake = lock.newCondition()

  // Desired state, guarded by [lock].
  private val targetGains = FloatArray(trackCount)
  private var wantPlaying = false
  private var seekRequest = -1L
  private var released = false

  @Volatile private var segmentStartFrame = 0L

  @Volatile private var ended = false

  @Volatile var isPlaying: Boolean = false
    private set

  private val audioTrack: AudioTrack = createAudioTrack()
  private val thread = Thread(::runLoop, "StemPlayer").apply { start() }

  /** Sets one gain per track (1.0 = unity). Changes are ramped over ~20 ms. */
  fun setGains(gains: FloatArray) {
    require(gains.size == trackCount) { "Expected $trackCount gains, got ${gains.size}" }
    lock.withLock { gains.copyInto(targetGains) }
  }

  fun play() = lock.withLock {
    if (ended) {
      seekRequest = 0
      ended = false
    }
    wantPlaying = true
    isPlaying = true
    wake.signal()
  }

  fun pause() = lock.withLock {
    wantPlaying = false
    isPlaying = false
    wake.signal()
  }

  fun seekTo(positionMs: Long) = lock.withLock {
    seekRequest = msToFrames(positionMs, sampleRate).coerceIn(0L, reader.frameCount)
    ended = false
    wake.signal()
  }

  fun positionMs(): Long {
    lock.withLock { if (seekRequest >= 0) return framesToMs(seekRequest, sampleRate) }
    if (ended) return durationMs
    val head = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
    return framesToMs((segmentStartFrame + head).coerceAtMost(reader.frameCount), sampleRate)
  }

  override fun close() {
    lock.withLock {
      released = true
      wake.signal()
    }
    thread.join(1_000)
    audioTrack.release()
    reader.close()
  }

  private fun runLoop() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
    val out = FloatArray(blockFrames * channelCount)
    val applied = FloatArray(trackCount)
    val target = FloatArray(trackCount)
    var gainsInitialized = false
    var trackPlaying = false

    while (true) {
      val seek: Long
      val playing: Boolean
      lock.lock()
      try {
        while (!released && !wantPlaying && seekRequest < 0) {
          if (trackPlaying) {
            audioTrack.pause()
            trackPlaying = false
          }
          wake.await()
        }
        if (released) return
        seek = seekRequest
        seekRequest = -1
        playing = wantPlaying
        targetGains.copyInto(target)
      } finally {
        lock.unlock()
      }

      if (seek >= 0) {
        audioTrack.pause()
        audioTrack.flush() // Resets playbackHeadPosition to 0.
        trackPlaying = false
        reader.seek(seek)
        segmentStartFrame = seek
      }
      if (!playing) {
        if (trackPlaying) {
          audioTrack.pause()
          trackPlaying = false
        }
        continue
      }
      if (!trackPlaying) {
        audioTrack.play()
        trackPlaying = true
      }
      if (!gainsInitialized) {
        target.copyInto(applied)
        gainsInitialized = true
      }

      val frames = reader.read(out, blockFrames, applied, target)
      target.copyInto(applied)
      if (frames == 0) {
        // End of media: let what's buffered drain, then report completion.
        audioTrack.stop()
        trackPlaying = false
        lock.withLock {
          wantPlaying = false
          isPlaying = false
          ended = true
        }
        onPlaybackEnded()
        continue
      }
      softClip(out, frames * channelCount)
      audioTrack.write(out, 0, frames * channelCount, AudioTrack.WRITE_BLOCKING)
    }
  }

  private fun createAudioTrack(): AudioTrack {
    val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
    val minBufferBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_FLOAT)
    val bytesPer100Ms = sampleRate / 10 * channelCount * Float.SIZE_BYTES
    return AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build(),
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
          .setSampleRate(sampleRate)
          .setChannelMask(channelMask)
          .build(),
      )
      .setBufferSizeInBytes(maxOf(minBufferBytes, bytesPer100Ms))
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
  }
}
