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

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.media.effect.enhancement.Enhancement
import com.google.android.gms.media.effect.enhancement.EnhancementClient
import com.google.android.gms.media.effect.enhancement.EnhancementMode
import com.google.android.gms.media.effect.enhancement.EnhancementOptions
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import com.google.android.gms.media.effect.enhancement.EnhancementStatusCode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "EnhancementViewModel"

/** Attempts at creating a session when the service reports its session limit. */
private const val SESSION_CREATE_ATTEMPTS = 3
private const val SESSION_RETRY_DELAY_MS = 250L

/** Enhancements the user can toggle. Each one maps onto a flag of [EnhancementOptions]. */
enum class EnhancementOption(val label: String) {
    Tonemap("Tonemap"),
    DeblurDenoise("Deblur & DeNoise"),
    Upscale("Upscale"),
}

/** Bundled sample photos so the user can demo the effects immediately without picking from the gallery. */
enum class SampleImage(
    val title: String,
    @param:DrawableRes val resId: Int,
) {
    Sample1("Puppy", R.drawable.sample_1),
    Sample2("Cat", R.drawable.sample_2),
    Sample3("Desert", R.drawable.sample_3),
    Sample4("Night", R.drawable.sample_4),
    Sample6("Portrait", R.drawable.sample_6),
}

/** One pass through the enhancement service, applying [options] together. */
data class EnhancementStep(
    val options: Set<EnhancementOption>,
    /** [SystemClock.elapsedRealtime] when the pass started, or null while it is still queued. */
    val startedAt: Long? = null,
    /** Processing time in milliseconds, once the pass has finished. */
    val latency: Long? = null,
) {
    val label: String
        get() = options.sortedBy { it.ordinal }.joinToString(" + ") { it.label }
}

/**
 * What is running while an enhancement is in flight. The service reports no progress of its own,
 * so this tracks which pass is running and since when.
 */
data class EnhancementProgress(
    val steps: List<EnhancementStep>,
    /** [SystemClock.elapsedRealtime] when the run started. */
    val startedAt: Long,
    /** Latest intermediate bitmap produced so far in a multi-step run. */
    val previewBitmap: Bitmap? = null,
)

/**
 * Splits the selected effects into passes: all of them in a single pass, or one effect per pass
 * in [order] (by default Tonemap, then Deblur & DeNoise, then Upscale) when [stepByStep] is set.
 */
internal fun enhancementSteps(
    selected: Set<EnhancementOption>,
    stepByStep: Boolean,
    order: List<EnhancementOption> = EnhancementOption.entries,
): List<Set<EnhancementOption>> {
    val ordered = normalizedStepOrder(order).filter { it in selected }
    if (ordered.isEmpty()) return emptyList()
    return if (stepByStep) ordered.map { setOf(it) } else listOf(ordered.toSet())
}

/** [order] without duplicates and with any missing effects appended in their default order. */
internal fun normalizedStepOrder(order: List<EnhancementOption>): List<EnhancementOption> =
    (order + EnhancementOption.entries).distinct()

/** This list with [option] moved to [toIndex] (clamped to the valid range). */
internal fun List<EnhancementOption>.moveStep(option: EnhancementOption, toIndex: Int): List<EnhancementOption> {
    val list = toMutableList()
    if (!list.remove(option)) return this
    list.add(toIndex.coerceIn(0, list.size), option)
    return list
}

// Data class to hold all information about an image
data class ImageInfo(
    /** Bitmap to draw. May be a downscaled preview of a very large result, see [width]/[height]. */
    val bitmap: Bitmap,
    val width: Int = bitmap.width,
    val height: Int = bitmap.height,
    val byteCount: Int = bitmap.byteCount,
    /** Time spent in the enhancement service, in milliseconds (enhanced images only). */
    val latency: Long? = null,
    /** Per-pass breakdown when the image was enhanced step by step. */
    val steps: List<EnhancementStep> = emptyList(),
    /** Number of enhancement runs stacked onto this image. */
    val passes: Int = 1,
)

/** Availability of the on-demand enhancement module. */
sealed interface ModuleState {
    data object Checking : ModuleState
    data object Unsupported : ModuleState
    data class Installing(val progress: Int) : ModuleState
    data object Ready : ModuleState
    data class Failed(val message: String) : ModuleState
}

// Defines the state of the UI
data class UiState(
    val originalImage: ImageInfo? = null,
    val enhancedImage: ImageInfo? = null,
    /** Non-null when the loaded image came from the bundled [SampleImage] presets. */
    val selectedSample: SampleImage? = null,
    val isLoadingImage: Boolean = false,
    val selectedOptions: Set<EnhancementOption> = setOf(EnhancementOption.Tonemap),
    /** Apply the selected effects one pass at a time, showing each pass separately. */
    val stepByStep: Boolean = false,
    /** Order of the passes in step-by-step mode. */
    val stepOrder: List<EnhancementOption> = EnhancementOption.entries,
    /** Non-null while an enhancement is running. */
    val progress: EnhancementProgress? = null,
    val moduleState: ModuleState = ModuleState.Checking,
    /** Debug builds only: whether the device allowlist override can be offered. */
    val canIgnoreDeviceAllowlist: Boolean = false,
    /** Debug builds only: try the module even though the device isn't on the library's allowlist. */
    val ignoreDeviceAllowlist: Boolean = false,
    val imageError: String? = null,
    val enhancementError: String? = null,
) {
    val isEnhancing: Boolean
        get() = progress != null

    /** The selected effects in the order step-by-step mode runs them. */
    val orderedSelection: List<EnhancementOption>
        get() = normalizedStepOrder(stepOrder).filter { it in selectedOptions }

    val canEnhance: Boolean
        get() = moduleState == ModuleState.Ready &&
            originalImage != null &&
            selectedOptions.isNotEmpty() &&
            !isLoadingImage &&
            !isEnhancing

    val canBoost: Boolean
        get() = canEnhance && enhancedImage != null
}

/** Everything a session is created with. A session is only reused for an identical config. */
internal data class SessionConfig(
    val width: Int,
    val height: Int,
    val options: Set<EnhancementOption>,
) {
    fun toEnhancementOptions() = EnhancementOptions(
        width,
        height,
        EnhancementMode.BITMAP,
        EnhancementOption.Tonemap in options, // isTonemappingEnabled
        EnhancementOption.DeblurDenoise in options, // isDeblurAndDenoisePhotoEnabled
        false, // isDeblurAndDenoiseVideoEnabled
        EnhancementOption.Upscale in options, // isUpscalePhotoEnabled
        false, // isUpscaleVideoEnabled
    )
}

class EnhancementViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _strength = MutableStateFlow(1f)

    /**
     * Blend factor (0f = original, 1f = full enhanced result) for the live strength slider. Kept
     * out of [uiState] on purpose: it changes on every frame of a slider drag, and an update to
     * [uiState] recomposes the whole screen.
     */
    val strength: StateFlow<Float> = _strength.asStateFlow()

    // Enhancement.getClient() requires API 30. Every device on the library's allowlist runs a newer
    // release, so older devices are reported as unsupported instead of calling it.
    private val enhancementClient: EnhancementClient? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Enhancement.getClient(application) else null

    // The fields below are only touched on the main thread (viewModelScope's dispatcher), so
    // image selection, enhancement and cancellation can't race each other.
    private var activeSession: ActiveSession? = null
    private var moduleJob: Job? = null
    private var loadJob: Job? = null
    private var enhancementJob: Job? = null

    init {
        _uiState.update { it.copy(canIgnoreDeviceAllowlist = BuildConfig.DEBUG && enhancementClient != null) }
        checkAndInstallModule()
    }

    /** Runs the device check and module installation again, e.g. after a failed download. */
    fun retryModuleSetup() = checkAndInstallModule()

    /**
     * Debug builds only: try the module on a device that isn't on the library's allowlist, using
     * [installModuleIgnoringAllowlist]. Unsupported by Google; the service may still refuse.
     */
    fun setIgnoreDeviceAllowlist(enabled: Boolean) {
        if (!_uiState.value.canIgnoreDeviceAllowlist) return
        _uiState.update { it.copy(ignoreDeviceAllowlist = enabled) }
        moduleJob?.cancel()
        checkAndInstallModule()
    }

    private fun checkAndInstallModule() {
        if (moduleJob?.isActive == true) return
        val client = enhancementClient
        if (client == null) {
            _uiState.update { it.copy(moduleState = ModuleState.Unsupported) }
            return
        }
        val ignoreAllowlist = _uiState.value.ignoreDeviceAllowlist
        moduleJob = viewModelScope.launch {
            _uiState.update { it.copy(moduleState = ModuleState.Checking) }
            val result = try {
                val supported = client.isDeviceSupported().await()
                when {
                    !supported && !ignoreAllowlist -> ModuleState.Unsupported
                    client.isModuleInstalled().await() -> ModuleState.Ready
                    else -> {
                        _uiState.update { it.copy(moduleState = ModuleState.Installing(progress = 0)) }
                        val onProgress: (Int) -> Unit = { progress ->
                            // Called on a Play services thread; update() is thread-safe.
                            _uiState.update { state ->
                                if (state.moduleState is ModuleState.Installing) {
                                    state.copy(moduleState = ModuleState.Installing(progress))
                                } else {
                                    state
                                }
                            }
                        }
                        val installed = if (supported) {
                            client.installModuleAsync(onProgress)
                        } else {
                            client.installModuleIgnoringAllowlist(getApplication(), onProgress)
                        }
                        if (installed) {
                            ModuleState.Ready
                        } else {
                            ModuleState.Failed("Module installation failed or was cancelled.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check or install module", e)
                ModuleState.Failed("Failed to check or install module: ${e.message}")
            }
            _uiState.update { it.copy(moduleState = result) }
        }
    }

    fun onImageSelected(uri: Uri) {
        loadImage(selectedSample = null) {
            val resolver = getApplication<Application>().contentResolver
            ImageInfo(bitmap = resolver.decodeBitmapForEnhancement(uri))
        }
    }

    fun onSampleSelected(sample: SampleImage) {
        loadImage(selectedSample = sample) {
            val resources = getApplication<Application>().resources
            ImageInfo(bitmap = resources.decodeBitmapForEnhancement(sample.resId))
        }
    }

    fun onCameraPhotoCaptured(file: java.io.File) {
        loadImage(selectedSample = null) {
            try {
                ImageInfo(bitmap = decodeFileForEnhancement(file))
            } finally {
                file.delete()
            }
        }
    }

    private fun loadImage(
        selectedSample: SampleImage?,
        decode: suspend () -> ImageInfo,
    ) {
        // Work in flight belongs to the previous image: its result must not show up next to the
        // new one, and the session is configured for the previous image's dimensions.
        enhancementJob?.cancel()
        discardSession()
        loadJob?.cancel()
        _strength.value = 1f
        _uiState.update {
            it.copy(
                originalImage = null,
                enhancedImage = null,
                selectedSample = selectedSample,
                isLoadingImage = true,
                progress = null,
                imageError = null,
                enhancementError = null,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    // Upload to the GPU now, off the main thread, instead of on the first frame
                    // that draws it (up to a 64 MB texture).
                    decode().also { it.bitmap.prepareToDraw() }
                }
                _uiState.update { it.copy(originalImage = image, isLoadingImage = false) }
            } catch (e: CancellationException) {
                throw e // Superseded by a newer selection, which now owns the UI state.
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding bitmap", e)
                _uiState.update { it.copy(isLoadingImage = false, imageError = "Failed to load image.") }
            }
        }
    }

    fun onOptionSelected(option: EnhancementOption) {
        _uiState.update {
            val options = it.selectedOptions
            it.copy(selectedOptions = if (option in options) options - option else options + option)
        }
    }

    fun setStepByStep(enabled: Boolean) {
        _uiState.update { it.copy(stepByStep = enabled) }
    }

    fun setStrength(strength: Float) {
        _strength.value = strength.coerceIn(0f, 1f)
    }

    /**
     * Moves [option] to position [toIndex] among the selected effects, changing the order in
     * which step-by-step mode runs them. Unselected effects keep their relative order after them.
     */
    fun moveStep(option: EnhancementOption, toIndex: Int) {
        _uiState.update { state ->
            if (state.isEnhancing) return@update state
            val selected = state.orderedSelection.moveStep(option, toIndex)
            val rest = normalizedStepOrder(state.stepOrder).filter { it !in selected }
            state.copy(stepOrder = selected + rest)
        }
    }

    fun enhanceImage() = runEnhancement(stackOnResult = false)

    /**
     * Feeds the current [UiState.enhancedImage] back through the selected effects for an extra
     * pass to amplify their effect while keeping [UiState.originalImage] as the Before reference.
     */
    fun boostImage() = runEnhancement(stackOnResult = true)

    private fun runEnhancement(stackOnResult: Boolean) {
        val state = _uiState.value
        val previousEnhanced = state.enhancedImage
        val source = (if (stackOnResult) previousEnhanced?.bitmap else state.originalImage?.bitmap) ?: return
        if (!state.canEnhance) return
        val steps = enhancementSteps(state.selectedOptions, state.stepByStep, state.stepOrder)
        val passNumber = if (stackOnResult && previousEnhanced != null) previousEnhanced.passes + 1 else 1
        val previousLatency = if (stackOnResult && previousEnhanced != null) previousEnhanced.latency ?: 0L else 0L

        enhancementJob = viewModelScope.launch {
            _strength.value = 1f
            _uiState.update {
                it.copy(
                    progress = EnhancementProgress(
                        steps = steps.map { options -> EnhancementStep(options) },
                        startedAt = SystemClock.elapsedRealtime(),
                    ),
                    enhancedImage = if (stackOnResult) previousEnhanced else null,
                    enhancementError = null,
                )
            }
            var session: ActiveSession? = null
            var stepIndex = 0
            try {
                // Each pass feeds the previous pass's output into the next one.
                var image = source
                for ((index, options) in steps.withIndex()) {
                    stepIndex = index
                    val input = image
                    val config = SessionConfig(input.width, input.height, options)
                    val active = obtainSession(config).also { session = it }
                    updateStep(index) { it.copy(startedAt = SystemClock.elapsedRealtime()) }
                    val (output, latency) = measureTimedValue {
                        withContext(Dispatchers.Default) {
                            active.session.processBitmapAsync(
                                input,
                                config.toEnhancementOptions(),
                                active.lifecycle,
                            )
                        }
                    }
                    updateStep(index) { it.copy(latency = latency.inWholeMilliseconds) }
                    image = output
                    if (index < steps.lastIndex) {
                        val preview = withContext(Dispatchers.Default) {
                            output.scaledForDisplay().also { it.prepareToDraw() }
                        }
                        _uiState.update { s ->
                            s.copy(progress = s.progress?.copy(previewBitmap = preview))
                        }
                    }
                }

                val completedSteps = _uiState.value.progress?.steps.orEmpty()
                val enhanced = image
                val enhancedImage = withContext(Dispatchers.Default) {
                    ImageInfo(
                        // Uploaded to the GPU here so the reveal animation doesn't start with a
                        // stalled frame.
                        bitmap = enhanced.scaledForDisplay().also { it.prepareToDraw() },
                        width = enhanced.width,
                        height = enhanced.height,
                        byteCount = enhanced.byteCount,
                        latency = previousLatency + completedSteps.sumOf { it.latency ?: 0L },
                        steps = if (completedSteps.size > 1) completedSteps else emptyList(),
                        passes = passNumber,
                    )
                }
                _uiState.update { it.copy(enhancedImage = enhancedImage, progress = null) }
            } catch (e: CancellationException) {
                session?.let(::discardSession)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Enhancement failed: ${e.message}", e)
                // Start from a fresh session next time instead of reusing one in an unknown state.
                session?.let(::discardSession)
                val failedStep = if (steps.size > 1) " at ${EnhancementStep(steps[stepIndex]).label}" else ""
                _uiState.update {
                    it.copy(progress = null, enhancementError = "Enhancement failed$failedStep: ${e.message}")
                }
            }
        }
    }

    /** Stops the running enhancement. The request is forwarded to [EnhancementSession.cancel]. */
    fun cancelEnhancement() {
        val job = enhancementJob ?: return
        if (!job.isActive) return
        job.cancel()
        // Don't hand a session that may still be busy with the cancelled request to the next run.
        discardSession()
        _uiState.update { it.copy(progress = null) }
    }

    private fun updateStep(index: Int, transform: (EnhancementStep) -> EnhancementStep) {
        _uiState.update { state ->
            val progress = state.progress ?: return@update state
            val steps = progress.steps.mapIndexed { i, step -> if (i == index) transform(step) else step }
            state.copy(progress = progress.copy(steps = steps))
        }
    }

    private suspend fun obtainSession(config: SessionConfig): ActiveSession {
        activeSession?.let { current ->
            if (current.config == config && current.isUsable) return current
            // Image size or options changed, or Play services dropped the session.
            discardSession(current)
        }
        // Only reachable once the module is Ready, which requires a client.
        val client = checkNotNull(enhancementClient) { "Enhancement is unavailable on this device" }
        // release() doesn't wait for the service, so right after switching sessions (between the
        // passes of a step-by-step run) the old one may still count towards the session limit.
        var attempt = 1
        while (true) {
            val lifecycle = SessionLifecycle()
            try {
                val session = client.createSessionAsync(config.toEnhancementOptions(), lifecycle)
                return ActiveSession(config, session, lifecycle).also { activeSession = it }
            } catch (e: ApiException) {
                val limitReached = e.statusCode == EnhancementStatusCode.MAX_SESSIONS_REACHED
                if (!limitReached || attempt >= SESSION_CREATE_ATTEMPTS) throw e
                delay(SESSION_RETRY_DELAY_MS * attempt)
                attempt++
            }
        }
    }

    /** Releases [target] (the current session by default) and forgets it if it is still current. */
    private fun discardSession(target: ActiveSession? = activeSession) {
        if (target == null) return
        if (activeSession === target) activeSession = null
        target.release()
    }

    override fun onCleared() {
        discardSession()
    }

    /** A live session plus the callback that reports when Play services drops it. */
    private class ActiveSession(
        val config: SessionConfig,
        val session: EnhancementSession,
        val lifecycle: SessionLifecycle,
    ) {
        private val released = AtomicBoolean(false)

        val isUsable: Boolean get() = !released.get() && !lifecycle.isLost

        fun release() {
            if (!released.compareAndSet(false, true)) return
            try {
                session.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release enhancement session", e)
            }
        }
    }
}
