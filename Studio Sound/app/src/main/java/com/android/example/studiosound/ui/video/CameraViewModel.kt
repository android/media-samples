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
package com.android.example.studiosound.ui.video

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import com.android.example.studiosound.video.VideoStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Records a video with sound using CameraX (Preview + VideoCapture with a Recorder). */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

  data class UiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val canFlip: Boolean = false,
    val isRecording: Boolean = false,
    val isFinalizing: Boolean = false,
    val elapsedMs: Long = 0,
    val error: String? = null,
  )

  private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
  val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

  private val _state = MutableStateFlow(UiState())
  val state: StateFlow<UiState> = _state.asStateFlow()

  /** Emits the file name of each successfully finished recording. */
  private val _finished = Channel<String>(Channel.BUFFERED)
  val finishedRecordings = _finished.receiveAsFlow()

  private val preview = Preview.Builder().build().apply {
    setSurfaceProvider { request -> _surfaceRequest.value = request }
  }
  private val recorder = Recorder.Builder()
    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
    .build()
  private val videoCapture = VideoCapture.withOutput(recorder)
  private var recording: Recording? = null

  /** Binds the camera to [lifecycleOwner] until the calling coroutine is cancelled. */
  suspend fun bindToCamera(lifecycleOwner: LifecycleOwner) {
    val provider = ProcessCameraProvider.awaitInstance(getApplication())
    val hasBack = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
    val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
    _state.update {
      it.copy(
        canFlip = hasBack && hasFront,
        lensFacing = if (!hasBack && hasFront) CameraSelector.LENS_FACING_FRONT else it.lensFacing,
      )
    }
    try {
      _state.map { it.lensFacing }.distinctUntilChanged().collectLatest { facing ->
        provider.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()
        try {
          provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
        } catch (e: Exception) {
          Log.e(TAG, "Camera bind failed", e)
          _state.update { it.copy(error = "Couldn't open the camera: ${e.message}") }
        }
      }
    } finally {
      recording?.stop()
      provider.unbindAll()
    }
  }

  fun flipCamera() {
    _state.update { s ->
      if (s.isRecording || !s.canFlip) return@update s
      s.copy(
        lensFacing = if (s.lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
        else CameraSelector.LENS_FACING_BACK,
      )
    }
  }

  fun toggleRecording() {
    if (recording != null) stopRecording() else startRecording()
  }

  // Permissions are checked by the screen before the camera is ever bound.
  @SuppressLint("MissingPermission")
  private fun startRecording() {
    val app = getApplication<Application>()
    VideoStorage.clearRecordings(app)
    val file = VideoStorage.newRecordingFile(app)
    val options = FileOutputOptions.Builder(file).build()
    recording = recorder.prepareRecording(app, options)
      .withAudioEnabled()
      .start(ContextCompat.getMainExecutor(app)) { event -> onRecordEvent(event, file) }
    _state.update { it.copy(isRecording = true, isFinalizing = false, elapsedMs = 0, error = null) }
  }

  private fun stopRecording() {
    _state.update { it.copy(isFinalizing = true) }
    recording?.stop()
  }

  private fun onRecordEvent(event: VideoRecordEvent, file: File) {
    when (event) {
      is VideoRecordEvent.Status ->
        _state.update { it.copy(elapsedMs = event.recordingStats.recordedDurationNanos / 1_000_000) }
      is VideoRecordEvent.Finalize -> {
        recording = null
        _state.update { it.copy(isRecording = false, isFinalizing = false) }
        val usable = file.exists() && file.length() > 0
        val recoverable = event.error in RECOVERABLE_ERRORS
        if (usable && (!event.hasError() || recoverable)) {
          _finished.trySend(file.name)
        } else {
          Log.e(TAG, "Recording failed: error=${event.error}", event.cause)
          file.delete()
          val reason =
            if (event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) "The recording was too short."
            else "Recording failed (error ${event.error})."
          _state.update { it.copy(error = reason) }
        }
      }
    }
  }

  override fun onCleared() {
    recording?.stop()
  }

  private companion object {
    const val TAG = "CameraViewModel"
    val RECOVERABLE_ERRORS = setOf(
      VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
      VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
      VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
      VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
    )
  }
}
