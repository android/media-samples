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
package com.android.example.studiosound.ui.mix

import androidx.compose.runtime.Immutable
import com.android.example.studiosound.audio.PcmFile
import com.android.example.studiosound.studiosound.StemType
import kotlin.math.abs

/** One isolated stem plus its mixer settings. Shared by the audio and video flows. */
@Immutable
data class StemChannel(
  val id: String,
  val type: StemType,
  /** Display name for the stem (one stem per category, e.g. "Speech"). */
  val label: String,
  val pcm: PcmFile,
  /** Waveform envelope, already normalized for display (0..1). */
  val peaks: FloatArray,
  val gain: Float = 1f,
  val muted: Boolean = false,
  val solo: Boolean = false,
)

const val MAX_STEM_GAIN = 1.5f

/**
 * The gain each stem actually plays at. Solo wins over mute (soloing a muted stem lets you hear
 * it), so "solo" always means "let me hear just this stem".
 */
fun List<StemChannel>.effectiveGains(): FloatArray {
  val anySolo = any { it.solo }
  return FloatArray(size) { i ->
    val stem = this[i]
    when {
      anySolo -> if (stem.solo) stem.gain else 0f
      stem.muted -> 0f
      else -> stem.gain
    }
  }
}

enum class MixPreset(
  val label: String,
  val shortTitle: String,
  val description: String,
  private val gains: (StemType) -> Float,
) {
  STUDIO(
    label = "Studio Sound",
    shortTitle = "Voice Isolate",
    description = "Clear speech, silence noise",
    gains = { if (it == StemType.SPEECH) 1f else 0f },
  ),
  VOICE_AMBIENCE(
    label = "Voice + ambience",
    shortTitle = "Ambience",
    description = "Clear voice + 20% room",
    gains = { if (it == StemType.SPEECH) 1f else 0.2f },
  ),
  NO_MUSIC(
    label = "Remove music",
    shortTitle = "De-Music",
    description = "Strip music, keep speech",
    gains = { if (it == StemType.MUSIC) 0f else 1f },
  ),
  NO_VOICE(
    label = "Remove voice",
    shortTitle = "Instrumental",
    description = "Mute voice, keep background",
    gains = { if (it == StemType.SPEECH) 0f else 1f },
  ),
  ORIGINAL(
    label = "Original balance",
    shortTitle = "All Stems",
    description = "All stems at 100% balance",
    gains = { 1f },
  );

  fun gainFor(type: StemType): Float = gains(type)
}

/**
 * Applies [preset]. Stems a preset turns off are *muted* with their fader left at 100%, rather
 * than faded to 0%, so unmuting or soloing them is immediately audible.
 */
fun List<StemChannel>.withPreset(preset: MixPreset): List<StemChannel> =
  map { stem ->
    val gain = preset.gainFor(stem.type)
    if (gain == 0f) stem.copy(gain = 1f, muted = true, solo = false)
    else stem.copy(gain = gain, muted = false, solo = false)
  }

/** The preset that produces exactly the current effective mix, if any. */
fun List<StemChannel>.matchingPreset(): MixPreset? {
  if (any { it.solo }) return null
  val effective = effectiveGains()
  return MixPreset.entries.firstOrNull { preset ->
    indices.all { i -> abs(effective[i] - preset.gainFor(this[i].type)) < 0.01f }
  }
}

/** Moving a fader above zero also unmutes the stem, so the change is actually heard. */
fun StemChannel.withGain(newGain: Float): StemChannel =
  copy(gain = newGain, muted = if (newGain > 0f) false else muted)

fun List<StemChannel>.update(id: String, transform: (StemChannel) -> StemChannel): List<StemChannel> =
  map { if (it.id == id) transform(it) else it }
