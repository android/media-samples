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

import com.google.android.gms.media.effect.enhancement.EnhancementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionConfigTest {

    @Test
    fun selectedOptionsMapToPhotoFlags() {
        val options = SessionConfig(
            width = 640,
            height = 480,
            options = setOf(EnhancementOption.DeblurDenoise, EnhancementOption.Upscale),
        ).toEnhancementOptions()

        assertEquals(640, options.width)
        assertEquals(480, options.height)
        assertEquals(EnhancementMode.BITMAP, options.enhancementMode)
        assertFalse(options.isTonemappingEnabled)
        assertTrue(options.isDeblurAndDenoisePhotoEnabled)
        assertTrue(options.isUpscalePhotoEnabled)
        assertFalse(options.isDeblurAndDenoiseVideoEnabled)
        assertFalse(options.isUpscaleVideoEnabled)
    }

    @Test
    fun sessionIsOnlyReusedForAnIdenticalConfig() {
        val config = SessionConfig(640, 480, setOf(EnhancementOption.Tonemap))

        assertEquals(config, SessionConfig(640, 480, setOf(EnhancementOption.Tonemap)))
        assertNotEquals(
            config,
            SessionConfig(640, 480, setOf(EnhancementOption.Tonemap, EnhancementOption.Upscale)),
        )
        assertNotEquals(config, SessionConfig(480, 640, setOf(EnhancementOption.Tonemap)))
    }
}
