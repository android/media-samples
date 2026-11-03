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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageLoadingTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After
    fun deleteFiles() {
        files.forEach(File::delete)
    }

    private fun writeJpeg(width: Int, height: Int, exifOrientation: Int? = null): Uri {
        val file = File.createTempFile("image-loading-test", ".jpg", context.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (exifOrientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    @Test
    fun exifOrientationIsApplied() {
        // Stored as 400x200 landscape pixels, tagged "rotate 90" like a portrait camera photo.
        val uri = writeJpeg(400, 200, ExifInterface.ORIENTATION_ROTATE_90)

        val bitmap = context.contentResolver.decodeBitmapForEnhancement(uri)

        assertEquals(200, bitmap.width)
        assertEquals(400, bitmap.height)
    }

    @Test
    fun largeImageIsDownsampled() {
        val uri = writeJpeg(3000, 1500)

        val bitmap = context.contentResolver.decodeBitmapForEnhancement(uri, maxDimension = 1000)

        assertEquals(1000, bitmap.width)
        assertEquals(500, bitmap.height)
    }

    @Test
    fun decodedBitmapIsSoftwareArgb8888() {
        val bitmap = context.contentResolver.decodeBitmapForEnhancement(writeJpeg(64, 48))

        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun oversizedResultIsScaledForDisplay() {
        val large = Bitmap.createBitmap(5000, 2500, Bitmap.Config.ARGB_8888)
        val display = large.scaledForDisplay(maxDimension = 4096)
        assertEquals(4096, display.width)
        assertEquals(2048, display.height)

        val small = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        assertSame(small, small.scaledForDisplay())
    }
}
