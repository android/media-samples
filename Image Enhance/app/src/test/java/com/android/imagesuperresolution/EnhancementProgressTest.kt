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
package com.android.imagesuperresolution

import com.android.imagesuperresolution.EnhancementOption.DeblurDenoise
import com.android.imagesuperresolution.EnhancementOption.Tonemap
import com.android.imagesuperresolution.EnhancementOption.Upscale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancementProgressTest {

    @Test
    fun combinedModeAppliesEverythingInOnePass() {
        val steps = enhancementSteps(setOf(Upscale, Tonemap), stepByStep = false)

        assertEquals(listOf(setOf(Tonemap, Upscale)), steps)
        assertEquals("Tonemap + Upscale", EnhancementStep(steps.single()).label)
    }

    @Test
    fun stepByStepRunsOneEffectPerPassInChipOrder() {
        val steps = enhancementSteps(setOf(Upscale, DeblurDenoise, Tonemap), stepByStep = true)

        assertEquals(listOf(setOf(Tonemap), setOf(DeblurDenoise), setOf(Upscale)), steps)
    }

    @Test
    fun noSelectionMeansNoPasses() {
        assertTrue(enhancementSteps(emptySet(), stepByStep = true).isEmpty())
        assertTrue(enhancementSteps(emptySet(), stepByStep = false).isEmpty())
    }

    @Test
    fun stepByStepFollowsACustomOrder() {
        val steps = enhancementSteps(
            setOf(Tonemap, Upscale),
            stepByStep = true,
            order = listOf(Upscale, DeblurDenoise, Tonemap),
        )

        assertEquals(listOf(setOf(Upscale), setOf(Tonemap)), steps)
    }

    @Test
    fun incompleteOrderFallsBackToDefaultForMissingEffects() {
        assertEquals(listOf(Upscale, Tonemap, DeblurDenoise), normalizedStepOrder(listOf(Upscale, Upscale)))
    }

    @Test
    fun moveStepRepositionsAnEffect() {
        val order = listOf(Tonemap, DeblurDenoise, Upscale)

        assertEquals(listOf(DeblurDenoise, Upscale, Tonemap), order.moveStep(Tonemap, 2))
        assertEquals(listOf(Upscale, Tonemap, DeblurDenoise), order.moveStep(Upscale, 0))
        // Out-of-range targets are clamped, and unknown effects leave the list alone.
        assertEquals(listOf(DeblurDenoise, Upscale, Tonemap), order.moveStep(Tonemap, 99))
        assertEquals(listOf(Tonemap, Upscale), listOf(Tonemap, Upscale).moveStep(DeblurDenoise, 0))
    }

    @Test
    fun shortDurationsShowTenthsOfASecond() {
        assertEquals("0.0 s", formatDuration(0, Locale.US))
        assertEquals("8.4 s", formatDuration(8_449, Locale.US))
        // Truncates like a stopwatch, so it never shows "60.0 s".
        assertEquals("59.9 s", formatDuration(59_999, Locale.US))
    }

    @Test
    fun longDurationsShowMinutesAndSeconds() {
        assertEquals("1:00", formatDuration(60_000, Locale.US))
        // The real Tonemap/Upscale run on a Pixel 10 Pro.
        assertEquals("2:11", formatDuration(131_539, Locale.US))
    }

    @Test
    fun negativeDurationsAreClamped() {
        assertEquals("0.0 s", formatDuration(-5, Locale.US))
    }
}
