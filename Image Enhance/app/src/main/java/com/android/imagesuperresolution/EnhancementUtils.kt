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
import android.util.Log
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.media.effect.enhancement.EnhancementCallback
import com.google.android.gms.media.effect.enhancement.EnhancementClient
import com.google.android.gms.media.effect.enhancement.EnhancementOptions
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import com.google.android.gms.media.effect.enhancement.EnhancementSessionCallback
import com.google.android.gms.media.effect.enhancement.EnhancementStatusCode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TAG = "EnhancementUtils"
private const val CREATE_SESSION_TIMEOUT_MS = 15_000L

/**
 * Thrown when the enhancement service reports a failure.
 *
 * @property errorCode one of the [EnhancementStatusCode] constants.
 */
class EnhancementFailedException(
    val errorCode: Int,
    message: String = EnhancementStatusCode.getStatusCodeString(errorCode),
) : Exception(message)

/** Thrown when Play services drops a session while a request is still pending on it. */
class EnhancementSessionLostException(message: String) : Exception(message)

/**
 * Observes an [EnhancementSession] once it exists.
 *
 * The outcome of [EnhancementClient.createSession] is read from the Task it returns (see
 * [createSessionAsync]), so the creation callbacks are intentionally ignored here.
 *
 * This callback matters because when Play services disconnects, the library only notifies the
 * [EnhancementSessionCallback]: the [EnhancementCallback] of a pending [EnhancementSession.process]
 * call is never invoked.
 */
class SessionLifecycle : EnhancementSessionCallback {
    private val lost = CompletableDeferred<String>()

    /** True once the session can no longer be used. */
    val isLost: Boolean get() = lost.isCompleted

    /** Suspends until the session is lost and returns a human-readable reason. */
    suspend fun awaitLost(): String = lost.await()

    override fun onSessionCreated(session: EnhancementSession) = Unit

    override fun onSessionCreationFailed(status: Status) = Unit

    override fun onSessionDisconnected(status: Status) {
        Log.w(TAG, "onSessionDisconnected: $status")
        lost.complete("The enhancement service disconnected (${status.statusMessage ?: status.statusCode}).")
    }

    override fun onSessionDestroyed() {
        Log.d(TAG, "onSessionDestroyed")
        lost.complete("The enhancement session was destroyed.")
    }
}

/**
 * Creates an [EnhancementSession], suspending until the service has created it or failed to.
 *
 * The returned Task is the single source of truth for the outcome: the library completes it
 * *before* invoking [EnhancementSessionCallback.onSessionCreated] or
 * [EnhancementSessionCallback.onSessionCreationFailed]. Resuming from both the Task and the
 * callback would resume the coroutine twice and crash with "Already resumed".
 *
 * If the caller is cancelled while creation is in flight, a session that arrives late is
 * released straight away so it can't leak (the service limits concurrent sessions).
 */
suspend fun EnhancementClient.createSessionAsync(
    options: EnhancementOptions,
    lifecycle: SessionLifecycle,
): EnhancementSession {
    val task = createSession(options, lifecycle)
    try {
        val session: EnhancementSession? = withTimeout(CREATE_SESSION_TIMEOUT_MS) { task.await() }
        return session ?: throw EnhancementFailedException(EnhancementStatusCode.SESSION_INIT_FAILED)
    } catch (e: TimeoutCancellationException) {
        task.addOnSuccessListener { lateSession -> lateSession?.release() }
        throw EnhancementFailedException(
            EnhancementStatusCode.SESSION_INIT_FAILED,
            "Play services did not respond (Media Effect Enhancement is not enabled in Play services for this device yet).",
        )
    } catch (e: CancellationException) {
        task.addOnSuccessListener { lateSession -> lateSession?.release() }
        throw e
    }
}

/**
 * Enhances [bitmap] with this session, suspending until the service delivers the result.
 *
 * Cancelling the calling coroutine forwards the request to the service through
 * [EnhancementSession.cancel] (added in 16.0.0-beta05). GMS Core versions without cancellation
 * support ignore that call, in which case the late result is simply dropped.
 *
 * [EnhancementSession.process] writes the whole bitmap into a binder transaction, so call this
 * from a background dispatcher.
 */
suspend fun EnhancementSession.processBitmapAsync(
    bitmap: Bitmap,
    options: EnhancementOptions,
): Bitmap = suspendCancellableCoroutine { continuation ->
    // The service can report more than one terminal event (e.g. onCancelled racing a result) and
    // resuming a continuation twice throws, so only the first event wins.
    val completed = AtomicBoolean(false)
    fun complete(result: Result<Bitmap>) {
        if (completed.compareAndSet(false, true)) continuation.resumeWith(result)
    }

    val callback = object : EnhancementCallback {
        override fun onBitmapProcessed(bitmap: Bitmap) = complete(Result.success(bitmap))

        override fun onError(statusCode: Int) =
            complete(Result.failure(EnhancementFailedException(statusCode)))

        // Only observable for cancellations we didn't request: when the caller cancels, the
        // continuation is already cancelled and this result is ignored.
        override fun onCancelled(statusCode: Int) =
            complete(Result.failure(EnhancementFailedException(statusCode)))

        override fun onSurfaceProcessed(timestamp: Long) = Unit // SURFACE mode only.
    }

    continuation.invokeOnCancellation {
        // Cancellation handlers must never throw, and the binder may already be dead here.
        try {
            this@processBitmapAsync.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel enhancement", e)
        }
    }
    this@processBitmapAsync.process(bitmap, options, callback)
}

/**
 * Like [processBitmapAsync], but fails with [EnhancementSessionLostException] as soon as
 * [lifecycle] reports the session as lost, instead of waiting for a result that will never come.
 */
suspend fun EnhancementSession.processBitmapAsync(
    bitmap: Bitmap,
    options: EnhancementOptions,
    lifecycle: SessionLifecycle,
): Bitmap = coroutineScope {
    val watchdog = launch { throw EnhancementSessionLostException(lifecycle.awaitLost()) }
    try {
        processBitmapAsync(bitmap, options)
    } finally {
        watchdog.cancel()
    }
}

/**
 * Installs the enhancement module on demand, reporting download progress (0-100) to [onProgress].
 *
 * The Task only completes once installation has finished: `true` when the module is installed,
 * `false` when the download failed or was cancelled (the reason is logged below).
 */
suspend fun EnhancementClient.installModuleAsync(onProgress: (Int) -> Unit): Boolean {
    val callback = object : EnhancementClient.InstallStatusCallback {
        override fun onDownloadPending() { Log.d(TAG, "onDownloadPending") }
        override fun onDownloadStart() { Log.d(TAG, "onDownloadStart") }
        override fun onDownloadPaused() { Log.d(TAG, "onDownloadPaused") }
        override fun onDownloadProgressUpdate(progress: Int) {
            Log.d(TAG, "onDownloadProgressUpdate: $progress")
            onProgress(progress)
        }
        override fun onDownloadComplete() { Log.d(TAG, "onDownloadComplete") }
        override fun onInstalled() { Log.d(TAG, "onInstalled") }
        override fun onCancelled() { Log.d(TAG, "onCancelled") }
        override fun onError(description: String) { Log.e(TAG, "onError: $description") }
    }
    return installModule(callback).await()
}

/**
 * Installs the enhancement module through Play services' generic module installer instead of
 * [EnhancementClient.installModule], which refuses any device missing from the library's
 * built-in model allowlist.
 *
 * For trying unsupported devices in debug builds only: Google hasn't validated the effects on
 * those devices, and the service may still reject them when a session is created.
 */
suspend fun EnhancementClient.installModuleIgnoringAllowlist(
    context: Context,
    onProgress: (Int) -> Unit,
): Boolean {
    val moduleInstallClient = ModuleInstall.getClient(context)
    val finished = CompletableDeferred<Boolean>()
    val listener = InstallStatusListener { update ->
        update.progressInfo?.let { info ->
            if (info.totalBytesToDownload > 0) {
                onProgress((info.bytesDownloaded * 100 / info.totalBytesToDownload).toInt())
            }
        }
        when (update.installState) {
            ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> finished.complete(true)
            ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> finished.complete(false)
            ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
                Log.e(TAG, "Module install failed with error code ${update.errorCode}")
                finished.complete(false)
            }
        }
    }
    val request = ModuleInstallRequest.newBuilder().addApi(this).setListener(listener).build()
    return try {
        val response = moduleInstallClient.installModules(request).await()
        response.areModulesAlreadyInstalled() || finished.await()
    } finally {
        moduleInstallClient.unregisterListener(listener)
    }
}
