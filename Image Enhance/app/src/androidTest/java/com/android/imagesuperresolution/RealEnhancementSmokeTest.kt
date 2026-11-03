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

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.media.effect.enhancement.Enhancement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end check against the real Play services implementation, using a generated image.
 *
 * Skipped on devices that aren't on the library's allowlist (including emulators) unless the
 * `allowUnsupportedDevice` instrumentation argument is set, which installs the module with
 * [installModuleIgnoringAllowlist]:
 *
 * ```
 * adb shell am instrument -w -e allowUnsupportedDevice true \
 *     -e class com.android.imagesuperresolution.RealEnhancementSmokeTest \
 *     com.android.imagesuperresolution.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Every Play services call has its own timeout and each stage is logged under [TAG], so a device
 * where the service never answers fails with the stage that hung instead of blocking forever.
 */
@RunWith(AndroidJUnit4::class)
class RealEnhancementSmokeTest {

    @Test
    fun tonemapsAGeneratedImage() = runBlocking {
        assumeTrue("Enhancement.getClient() requires API 30", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val allowUnsupported =
            InstrumentationRegistry.getArguments().getString("allowUnsupportedDevice").toBoolean()

        val client = Enhancement.getClient(context)
        val supported = stage("isDeviceSupported", 30.seconds) { client.isDeviceSupported().await() }
        assumeTrue("${Build.MODEL} isn't on the enhancement allowlist", supported || allowUnsupported)

        if (!stage("isModuleInstalled", 30.seconds) { client.isModuleInstalled().await() }) {
            val installed = stage("installModule", 5.minutes) {
                if (supported) {
                    client.installModuleAsync {}
                } else {
                    client.installModuleIgnoringAllowlist(context) {}
                }
            }
            assertTrue("Module installation failed", installed)
        }

        val width = 320
        val height = 240
        val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(width * height) { i ->
                Color.rgb((i % width) * 255 / width, (i / width) * 255 / height, 128)
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        for (effect in EnhancementOption.entries) {
            val options = SessionConfig(width, height, setOf(effect)).toEnhancementOptions()
            val lifecycle = SessionLifecycle()
            val session = stage("createSession(${effect.name})", 15.seconds) {
                withContext(Dispatchers.Main) { client.createSessionAsync(options, lifecycle) }
            }
            try {
                val output = stage("process(${effect.name})", 2.minutes) {
                    withContext(Dispatchers.Default) {
                        session.processBitmapAsync(input, options, lifecycle)
                    }
                }
                Log.i(TAG, "${Build.MODEL} ${effect.name}: ${output.width}x${output.height}")
                val scale = if (effect == EnhancementOption.Upscale) 2 else 1
                assertEquals(width * scale, output.width)
                assertEquals(height * scale, output.height)
            } finally {
                session.release()
            }
        }
    }

    /** Runs one step with a timeout, logging how long it took or that it timed out. */
    private suspend fun <T> stage(name: String, timeout: Duration, block: suspend () -> T): T {
        Log.i(TAG, "$name: started")
        try {
            val (result, took) = measureTimedValue { withTimeout(timeout) { block() } }
            Log.i(TAG, "$name: finished in $took -> $result")
            return result
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$name: no answer within $timeout")
            fail("$name: no answer from Play services within $timeout")
            throw e
        }
    }

    private companion object {
        const val TAG = "RealEnhancementSmokeTest"
    }
}
