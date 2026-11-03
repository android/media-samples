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
package com.android.example.studiosound.studiosound

import com.google.android.gms.media.effect.studiosound.StudioSoundAudioType

/** The sound categories Studio Sound can isolate, mapped from [StudioSoundAudioType]. */
enum class StemType(val audioType: Int, val label: String, val description: String) {
  SPEECH(StudioSoundAudioType.SPEECH, "Speech", "Voices & dialogue"),
  MUSIC(StudioSoundAudioType.MUSIC, "Music", "Songs & instruments"),
  CROWD(StudioSoundAudioType.CROWD, "Crowd", "Chatter, cheering & murmur"),
  NATURE(StudioSoundAudioType.NATURE, "Nature", "Birds, water & leaves"),
  WIND(StudioSoundAudioType.WIND, "Wind", "Wind & air turbulence"),
  NOISE(StudioSoundAudioType.NOISE, "Noise", "Ambient background noise"),
  OTHER(StudioSoundAudioType.UNSPECIFIED, "Other", "Unclassified sound");

  companion object {
    fun fromAudioType(audioType: Int): StemType = entries.firstOrNull { it.audioType == audioType } ?: OTHER
  }
}
