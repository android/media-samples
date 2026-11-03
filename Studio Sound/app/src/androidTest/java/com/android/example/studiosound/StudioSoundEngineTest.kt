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
package com.android.example.studiosound

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.example.studiosound.audio.AudioDecoder
import com.android.example.studiosound.audio.MixReader
import com.android.example.studiosound.audio.PcmFile
import com.android.example.studiosound.studiosound.StemType
import com.android.example.studiosound.studiosound.StudioSoundAvailability
import com.android.example.studiosound.studiosound.StudioSoundEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sqrt

/**
 * Runs the real Studio Sound pipeline on-device against a synthetic "speech + noise + music" clip.
 * Skipped (not failed) on devices that don't support Studio Sound.
 */
@RunWith(AndroidJUnit4::class)
class StudioSoundEngineTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  @Test
  fun separatesNoisySpeechIntoAlignedStems() = runBlocking {
    val engine = StudioSoundEngine(context)
    val availability = engine.checkAvailability()
    Log.i(TAG, "Availability: $availability")
    assumeTrue("Studio Sound unavailable: $availability", availability !is StudioSoundAvailability.Unsupported)
    engine.awaitReady()

    val dir = File(context.cacheDir, "engine_test").apply { deleteRecursively(); mkdirs() }
    val source = File(dir, "input.wav")
    instrumentation.context.assets.open("noisy_speech_test.wav").use { input ->
      source.outputStream().use { input.copyTo(it) }
    }

    val decoded = AudioDecoder.decode(context, Uri.fromFile(source), File(dir, "input.pcm"))
    Log.i(TAG, "Decoded ${decoded.pcm.frameCount} frames @ ${decoded.pcm.sampleRate} Hz x${decoded.pcm.channelCount}")
    assertEquals(48_000, decoded.pcm.sampleRate)

    // Small chunks on purpose, to exercise the multi-chunk path.
    val result = engine.separate(decoded.pcm, File(dir, "stems"), chunkDurationMs = 5_000)
    Log.i(TAG, "Stems: ${result.stems.map { "${it.id}(rms=%.4f)".format(rms(it.pcm)) }}")
    Log.i(TAG, "Chunks=${result.chunkCount} time=${result.processingTimeMs} ms speed=${result.speedFactor}x")

    assertTrue("Expected at least one stem", result.stems.isNotEmpty())
    assertTrue("Expected multiple chunks", result.chunkCount > 1)
    result.stems.forEach { stem ->
      assertEquals("${stem.id} must be aligned with the input", decoded.pcm.frameCount, stem.pcm.frameCount)
    }
    val speech = result.stems.firstOrNull { it.type == StemType.SPEECH }
    assertTrue("Expected a speech stem", speech != null)
    assertTrue("Speech stem shouldn't be silent", rms(speech!!.pcm) > 1e-3)

    // Studio Sound's separation is additive, so the stems must sum back to the input sample-for-
    // sample. If chunk outputs weren't trimmed/aligned correctly, later chunks would drift and this
    // residual would jump.
    val residual = reconstructionResidual(decoded.pcm, result.stems.map { it.pcm })
    Log.i(TAG, "Reconstruction residual (sum of stems vs input) = %.5f".format(residual))
    assertTrue("Stems don't line up with the input (residual=$residual)", residual < 0.02)
  }

  /** ||input − Σ stems||² / ||input||². */
  private fun reconstructionResidual(input: PcmFile, stems: List<PcmFile>): Double =
    MixReader(listOf(input) + stems).use { reader ->
      val gains = FloatArray(reader.trackCount) { if (it == 0) 1f else -1f } // input − Σ stems
      val diff = FloatArray(reader.maxBlockFrames * reader.channelCount)
      var err = 0.0
      while (true) {
        val frames = reader.read(diff, reader.maxBlockFrames, gains)
        if (frames == 0) break
        for (i in 0 until frames * reader.channelCount) err += diff[i] * diff[i]
      }
      val energy = MixReader(listOf(input)).use { r ->
        val block = FloatArray(r.maxBlockFrames * r.channelCount)
        var e = 0.0
        while (true) {
          val frames = r.read(block, r.maxBlockFrames, floatArrayOf(1f))
          if (frames == 0) break
          for (i in 0 until frames * r.channelCount) e += block[i] * block[i]
        }
        e
      }
      if (energy == 0.0) 0.0 else err / energy
    }

  private fun rms(pcm: PcmFile): Double = MixReader(listOf(pcm)).use { reader ->
    val block = FloatArray(reader.maxBlockFrames * reader.channelCount)
    var sum = 0.0
    var count = 0L
    while (true) {
      val frames = reader.read(block, reader.maxBlockFrames, floatArrayOf(1f))
      if (frames == 0) break
      for (i in 0 until frames * reader.channelCount) sum += block[i] * block[i]
      count += frames * reader.channelCount
    }
    if (count == 0L) 0.0 else sqrt(sum / count)
  }

  private companion object {
    const val TAG = "StudioSoundEngineTest"
  }
}
