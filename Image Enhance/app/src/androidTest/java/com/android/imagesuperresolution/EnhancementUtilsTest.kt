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
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.api.internal.ApiKey
import com.google.android.gms.media.effect.enhancement.EnhancementCallback
import com.google.android.gms.media.effect.enhancement.EnhancementClient
import com.google.android.gms.media.effect.enhancement.EnhancementOptions
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import com.google.android.gms.media.effect.enhancement.EnhancementSessionCallback
import com.google.android.gms.media.effect.enhancement.EnhancementStatusCode
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the coroutine wrappers in EnhancementUtils.kt against fakes, so the edge cases that
 * matter (duplicate callbacks, cancellation, lost sessions) are covered on any device, including
 * ones that aren't on the enhancement allowlist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EnhancementUtilsTest {

    private val input = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    private val output = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    private val options =
        SessionConfig(8, 8, setOf(EnhancementOption.Upscale)).toEnhancementOptions()

    @Test
    fun processReturnsTheEnhancedBitmap() = runTest {
        val session = FakeSession { callback -> callback.onBitmapProcessed(output) }

        assertSame(output, session.processBitmapAsync(input, options))
    }

    @Test
    fun processErrorCarriesTheStatusCode() = runTest {
        val session = FakeSession { callback -> callback.onError(EnhancementStatusCode.INTERNAL_ERROR) }

        val error = runCatching { session.processBitmapAsync(input, options) }.exceptionOrNull()

        assertTrue("Unexpected $error", error is EnhancementFailedException)
        assertEquals(EnhancementStatusCode.INTERNAL_ERROR, (error as EnhancementFailedException).errorCode)
    }

    @Test
    fun duplicateTerminalCallbacksResumeOnlyOnce() = runTest {
        val session = FakeSession { callback ->
            callback.onBitmapProcessed(output)
            callback.onError(EnhancementStatusCode.INTERNAL_ERROR)
            callback.onCancelled(EnhancementStatusCode.PROCESS_CANCELLED)
        }

        assertSame(output, session.processBitmapAsync(input, options))
    }

    @Test
    fun cancellingTheCallerCancelsTheServiceRequest() = runTest {
        val session = FakeSession { /* The service never answers. */ }

        val job = launch { session.processBitmapAsync(input, options) }
        runCurrent()
        assertEquals(1, session.processCalls.get())
        job.cancelAndJoin()

        assertEquals(1, session.cancelCalls.get())
    }

    @Test
    fun serviceSideCancellationIsReportedAsAnError() = runTest {
        val session = FakeSession { callback -> callback.onCancelled(EnhancementStatusCode.PROCESS_CANCELLED) }

        val error = runCatching { session.processBitmapAsync(input, options) }.exceptionOrNull()

        assertTrue("Unexpected $error", error is EnhancementFailedException)
        assertEquals(EnhancementStatusCode.PROCESS_CANCELLED, (error as EnhancementFailedException).errorCode)
    }

    @Test
    fun lostSessionFailsThePendingRequestInsteadOfHanging() = runTest {
        val lifecycle = SessionLifecycle()
        // Mirrors the library: on disconnect only the session callback fires, never the
        // EnhancementCallback of the pending request.
        val session = FakeSession { lifecycle.onSessionDisconnected(Status(CommonStatusCodes.INTERNAL_ERROR)) }

        val error = runCatching { session.processBitmapAsync(input, options, lifecycle) }.exceptionOrNull()

        assertTrue("Unexpected $error", error is EnhancementSessionLostException)
        assertTrue(lifecycle.isLost)
        assertEquals(1, session.cancelCalls.get())
    }

    @Test
    fun createSessionReturnsTheTaskResult() = runTest {
        val session = FakeSession {}
        val client = FakeClient().apply { pendingSession.setResult(session) }

        assertSame(session, client.createSessionAsync(options, SessionLifecycle()))
    }

    @Test
    fun createSessionFailureIsThrown() = runTest {
        val client = FakeClient().apply {
            pendingSession.setException(ApiException(Status(EnhancementStatusCode.SESSION_INIT_FAILED)))
        }

        val error = runCatching { client.createSessionAsync(options, SessionLifecycle()) }.exceptionOrNull()

        assertTrue("Unexpected $error", error is ApiException)
        assertEquals(EnhancementStatusCode.SESSION_INIT_FAILED, (error as ApiException).statusCode)
    }

    @Test
    fun sessionDeliveredAfterCancellationIsReleased() = runTest {
        val client = FakeClient()
        val job = launch { client.createSessionAsync(options, SessionLifecycle()) }
        runCurrent()
        job.cancelAndJoin()

        val lateSession = FakeSession {}
        client.pendingSession.setResult(lateSession)
        // Task success listeners registered without an executor run on the main thread.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(1, lateSession.releaseCalls.get())
    }

    private class FakeSession(private val onProcess: (EnhancementCallback) -> Unit) : EnhancementSession {
        val processCalls = AtomicInteger()
        val cancelCalls = AtomicInteger()
        val releaseCalls = AtomicInteger()

        override fun process(bitmap: Bitmap, options: EnhancementOptions, callback: EnhancementCallback) {
            processCalls.incrementAndGet()
            onProcess(callback)
        }

        override fun cancel() {
            cancelCalls.incrementAndGet()
        }

        override fun release() {
            releaseCalls.incrementAndGet()
        }

        override fun getInputSurface(): Surface = throw UnsupportedOperationException()

        override fun setOutputSurface(
            surface: Surface,
            options: EnhancementOptions,
            callback: EnhancementCallback,
        ) = throw UnsupportedOperationException()
    }

    private class FakeClient : EnhancementClient {
        val pendingSession = TaskCompletionSource<EnhancementSession?>()

        override fun createSession(
            options: EnhancementOptions,
            callback: EnhancementSessionCallback,
        ): Task<EnhancementSession?> = pendingSession.task

        override fun isDeviceSupported(): Task<Boolean> = Tasks.forResult(true)

        override fun isModuleInstalled(): Task<Boolean> = Tasks.forResult(true)

        override fun installModule(
            installStatusCallback: EnhancementClient.InstallStatusCallback?,
        ): Task<Boolean> = Tasks.forResult(true)

        override fun releaseModule(): Task<Void?> = Tasks.forResult(null)

        override fun getApiKey(): ApiKey<Api.ApiOptions.NoOptions> = throw UnsupportedOperationException()

        override fun getOptionalFeatures(): Array<Feature> = emptyArray()
    }
}
