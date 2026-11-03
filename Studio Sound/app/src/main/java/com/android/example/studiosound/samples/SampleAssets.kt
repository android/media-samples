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
package com.android.example.studiosound.samples

import android.content.Context
import com.android.example.studiosound.video.VideoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class SampleKind {
  VIDEO,
  AUDIO,
}

data class SampleAsset(
  val id: String,
  val shortTitle: String,
  val title: String,
  val subtitle: String,
  val description: String,
  val badge: String,
  val assetPath: String,
  val fileName: String,
  val kind: SampleKind,
  val previewPeaks: FloatArray,
)

object SampleAssets {
  val SAMPLE_1 = SampleAsset(
    id = "sample_1",
    shortTitle = "Office",
    title = "Office Noise",
    subtitle = "0:26 · 48 kHz Mono M4A",
    description = "Voice recorded with random background noises, footsteps, and desk tapping.",
    badge = "Speech + Noise",
    assetPath = "samples/Sample1.m4a",
    fileName = "Sample1.m4a",
    kind = SampleKind.AUDIO,
    previewPeaks = floatArrayOf(
      0.22f, 0.35f, 0.72f, 0.58f, 0.84f, 0.46f, 0.30f, 0.78f,
      0.92f, 0.64f, 0.40f, 0.88f, 0.52f, 0.68f, 0.95f, 0.44f,
      0.32f, 0.76f, 0.62f, 0.85f, 0.50f, 0.38f, 0.70f, 0.28f,
    ),
  )

  val SAMPLE_2 = SampleAsset(
    id = "sample_2",
    shortTitle = "Concert",
    title = "Concert Music",
    subtitle = "0:16 · 48 kHz Mono M4A",
    description = "Voice recorded over loud background concert music to test vocal isolation.",
    badge = "Speech + Music",
    assetPath = "samples/Sample2.m4a",
    fileName = "Sample2.m4a",
    kind = SampleKind.AUDIO,
    previewPeaks = floatArrayOf(
      0.68f, 0.82f, 0.90f, 0.76f, 0.94f, 0.85f, 0.72f, 0.88f,
      0.96f, 0.80f, 0.74f, 0.92f, 0.86f, 0.78f, 0.91f, 0.84f,
      0.70f, 0.88f, 0.95f, 0.79f, 0.83f, 0.75f, 0.66f, 0.54f,
    ),
  )

  val SAMPLE_3 = SampleAsset(
    id = "sample_3",
    shortTitle = "Traffic",
    title = "Traffic & Wind",
    subtitle = "0:20 · 48 kHz Mono M4A",
    description = "Outdoor speech test recorded through heavy street traffic and wind turbulence.",
    badge = "Speech + Wind",
    assetPath = "samples/Sample3.m4a",
    fileName = "Sample3.m4a",
    kind = SampleKind.AUDIO,
    previewPeaks = floatArrayOf(
      0.48f, 0.62f, 0.55f, 0.78f, 0.86f, 0.60f, 0.52f, 0.82f,
      0.74f, 0.90f, 0.66f, 0.58f, 0.84f, 0.72f, 0.64f, 0.88f,
      0.76f, 0.54f, 0.68f, 0.92f, 0.62f, 0.50f, 0.58f, 0.42f,
    ),
  )

  val DEFAULT: SampleAsset = SAMPLE_1

  val ALL: List<SampleAsset> = listOf(
    SAMPLE_1,
    SAMPLE_2,
    SAMPLE_3,
  )

  val AUDIO_SAMPLES: List<SampleAsset> = ALL

  fun byId(id: String): SampleAsset? =
    ALL.firstOrNull {
      it.id.equals(id, ignoreCase = true) ||
        it.fileName.equals(id, ignoreCase = true) ||
        it.fileName.substringBeforeLast('.').equals(id, ignoreCase = true)
    }

  /** Copies [sample] from bundled assets into the cache directory so MediaExtractor / MediaMetadataRetriever can read it as a [File]. */
  suspend fun materializeForAudio(context: Context, sample: SampleAsset): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "samples").apply { mkdirs() }
    val target = File(dir, sample.fileName)
    copyAssetIfNeeded(context, sample.assetPath, target)
    target
  }

  /** Copies [sample] from bundled assets into [VideoStorage.recordingsDir] so VideoResultScreen can play and process it. */
  suspend fun materializeForVideo(context: Context, sample: SampleAsset): File = withContext(Dispatchers.IO) {
    val dir = VideoStorage.recordingsDir(context)
    val target = File(dir, sample.fileName)
    copyAssetIfNeeded(context, sample.assetPath, target)
    target
  }

  private fun copyAssetIfNeeded(context: Context, assetPath: String, target: File) {
    if (target.exists() && target.length() > 0L) return
    val temp = File(target.parentFile, "${target.name}.tmp")
    context.assets.open(assetPath).use { input ->
      temp.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    if (!temp.renameTo(target)) {
      temp.copyTo(target, overwrite = true)
      temp.delete()
    }
  }
}
