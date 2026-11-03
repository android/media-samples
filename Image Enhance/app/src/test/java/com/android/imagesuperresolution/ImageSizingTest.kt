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

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {

    @Test
    fun sizeThatAlreadyFitsIsUnchanged() {
        // A typical 12 MP photo must keep its full resolution.
        assertEquals(PixelSize(4032, 3024), fitWithin(4032, 3024, MAX_IMAGE_DIMENSION))
        assertEquals(PixelSize(4096, 4096), fitWithin(4096, 4096, MAX_IMAGE_DIMENSION))
    }

    @Test
    fun landscapeImageIsScaledToMaxWidth() {
        assertEquals(PixelSize(4096, 3072), fitWithin(8160, 6120, MAX_IMAGE_DIMENSION))
    }

    @Test
    fun portraitImageIsScaledToMaxHeight() {
        assertEquals(PixelSize(3072, 4096), fitWithin(6120, 8160, MAX_IMAGE_DIMENSION))
    }

    @Test
    fun upscaledResultIsScaledForDisplay() {
        // 2x upscale of a 12 MP photo would be ~195 MB, which can't be drawn.
        assertEquals(PixelSize(4096, 3072), fitWithin(8064, 6048, MAX_IMAGE_DIMENSION))
    }

    @Test
    fun extremeAspectRatioKeepsAtLeastOnePixel() {
        assertEquals(PixelSize(4096, 1), fitWithin(100_000, 2, MAX_IMAGE_DIMENSION))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySizeIsRejected() {
        fitWithin(0, 100, MAX_IMAGE_DIMENSION)
    }
}
