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

import com.android.example.studiosound.samples.SampleAssets
import com.android.example.studiosound.samples.SampleKind
import com.android.example.studiosound.ui.mix.MixPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun samplePresets_containsThreeNewM4aSamples() {
    assertEquals(3, SampleAssets.ALL.size)
    assertEquals(listOf("Sample1.m4a", "Sample2.m4a", "Sample3.m4a"), SampleAssets.ALL.map { it.fileName })
    assertEquals(listOf("samples/Sample1.m4a", "samples/Sample2.m4a", "samples/Sample3.m4a"), SampleAssets.ALL.map { it.assetPath })
    assertTrue(SampleAssets.ALL.all { it.kind == SampleKind.AUDIO && it.previewPeaks.isNotEmpty() })
    assertEquals(SampleAssets.SAMPLE_1, SampleAssets.DEFAULT)
    assertNotNull(SampleAssets.byId("sample_1"))
    assertNotNull(SampleAssets.byId("Sample2.m4a"))
    assertNotNull(SampleAssets.byId("Sample3"))
  }

  @Test
  fun mixPresets_haveShortTitlesAndDescriptions() {
    assertTrue(MixPreset.entries.all { it.shortTitle.isNotBlank() && it.description.isNotBlank() })
  }
}
