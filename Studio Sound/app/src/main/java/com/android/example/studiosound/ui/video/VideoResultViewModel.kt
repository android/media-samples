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

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.android.example.studiosound.audio.AudioDecoder
import com.android.example.studiosound.audio.Waveform
import com.android.example.studiosound.samples.SampleAssets
import com.android.example.studiosound.studioSound
import com.android.example.studiosound.studiosound.StemType
import com.android.example.studiosound.studiosound.StudioSoundAvailability
import com.android.example.studiosound.studiosound.describeStudioSoundError
import com.android.example.studiosound.ui.components.PipelineStep
import com.android.example.studiosound.ui.components.StepStatus
import com.android.example.studiosound.ui.components.update
import com.android.example.studiosound.ui.mix.MixPreset
import com.android.example.studiosound.ui.mix.StemChannel
import com.android.example.studiosound.ui.mix.effectiveGains
import com.android.example.studiosound.ui.mix.update
import com.android.example.studiosound.ui.mix.withGain
import com.android.example.studiosound.ui.mix.withPreset
import com.android.example.studiosound.video.StudioVideoRenderer
import com.android.example.studiosound.video.VideoStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Takes a CameraX recording (or bundled video sample) → separates its audio with Studio Sound →
 * renders a new MP4 with the Studio Sound mix as its soundtrack → plays it back, with instant
 * Original/Studio Sound switching.
 */
@OptIn(UnstableApi::class)
class VideoResultViewModel(
  application: Application,
  savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

  enum class Status { PROCESSING, READY, FAILED }

  enum class Monitor { ORIGINAL, STUDIO }

  data class UiState(
    val status: Status = Status.PROCESSING,
    val steps: List<PipelineStep> = emptyList(),
    val startedAtElapsedMs: Long = 0L,
    val processingTimeMs: Long? = null,
    val errorMessage: String? = null,
    val stems: List<StemChannel> = emptyList(),
    /** Gains baked into the current studio_sound.mp4. */
    val appliedGains: List<Float> = emptyList(),
    val monitor: Monitor = Monitor.STUDIO,
    val isRendering: Boolean = false,
    val renderProgress: Float = 0f,
    val isSaving: Boolean = false,
    val summary: String? = null,
  ) {
    val studioReady: Boolean get() = status == Status.READY
    val mixChanged: Boolean get() = stems.effectiveGains().toList() != appliedGains
  }

  private val app = application
  private val engine = application.studioSound
  val availability: StateFlow<StudioSoundAvailability> = engine.availability
  val ignoreDeviceAllowlist: StateFlow<Boolean> = engine.ignoreDeviceAllowlist
  val canIgnoreDeviceAllowlist: Boolean = engine.canIgnoreDeviceAllowlist
  val playServicesVersionName: String get() = engine.playServicesVersionName
  private val fileName = requireNotNull(savedStateHandle.get<String>(ARG_FILE))
  private val recording = File(VideoStorage.recordingsDir(application), fileName)
  private val workDir = File(recording.parentFile, recording.nameWithoutExtension)
  private val studioFile = File(workDir, "studio_sound.mp4")
  private var decoded: AudioDecoder.Result? = null

  private val _state = MutableStateFlow(UiState())
  val state: StateFlow<UiState> = _state.asStateFlow()

  private val _messages = Channel<String>(Channel.BUFFERED)
  val messages = _messages.receiveAsFlow()

  fun installModule() = engine.install()

  fun retryModuleSetup() = engine.refresh()

  fun setIgnoreDeviceAllowlist(enabled: Boolean) = engine.setIgnoreDeviceAllowlist(enabled)

  private var processingJob: Job? = null
  private var renderJob: Job? = null

  val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
    repeatMode = Player.REPEAT_MODE_ONE
    addListener(
      object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) = applyMonitorSelection(tracks)

        override fun onPlayerError(error: PlaybackException) {
          Log.e(TAG, "Playback error", error)
          _messages.trySend("Playback error: ${error.errorCodeName}")
        }
      },
    )
  }

  init {
    process()
  }

  fun process() {
    processingJob?.cancel()
    processingJob = viewModelScope.launch {
      // Ensure bundled video sample is materialized on disk if opened by sample filename.
      if (!recording.exists()) {
        SampleAssets.ALL.firstOrNull { it.fileName == fileName || it.id == fileName }?.let { sample ->
          SampleAssets.materializeForVideo(app, sample)
        }
      }
      if (recording.exists() && player.mediaItemCount == 0) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(recording)))
        player.prepare()
      }

      var current = 0
      fun step(index: Int, status: StepStatus, progress: Float? = null, detail: String? = null) {
        current = index
        _state.update { s ->
          s.copy(steps = s.steps.update(index) { it.copy(status = status, progress = progress, detail = detail ?: it.detail) })
        }
      }

      _state.update {
        it.copy(
          status = Status.PROCESSING,
          startedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
          processingTimeMs = null,
          errorMessage = null,
          steps = listOf(
            PipelineStep("Prepare Studio Sound"),
            PipelineStep("Extract audio from video"),
            PipelineStep("Separate audio into stems"),
            PipelineStep("Render video with Studio Sound"),
          ),
        )
      }
      try {
        // 1. Availability (+ module download if needed).
        step(0, StepStatus.RUNNING)
        val installWatcher = launch {
          engine.availability.collect { availability ->
            if (availability is StudioSoundAvailability.Installing) {
              step(0, StepStatus.RUNNING, availability.progress?.div(100f), "Downloading Studio Sound… ${availability.progress ?: 0}%")
            }
          }
        }
        try {
          engine.awaitReady()
        } finally {
          installWatcher.cancel()
        }
        step(0, StepStatus.DONE, detail = "Ready")

        // 2. Decode the recording's audio track.
        withContext(Dispatchers.IO) {
          workDir.deleteRecursively()
          workDir.mkdirs()
        }
        step(1, StepStatus.RUNNING, 0f)
        val audio = AudioDecoder.decode(app, Uri.fromFile(recording), File(workDir, "original.pcm")) { p ->
          step(1, StepStatus.RUNNING, p)
        }
        decoded = audio
        val channelsLabel = if (audio.pcm.channelCount == 1) "mono" else "stereo"
        step(1, StepStatus.DONE, detail = "${audio.pcm.sampleRate / 1000.0} kHz $channelsLabel")

        // 3. Studio Sound separation.
        step(2, StepStatus.RUNNING, 0f)
        val result = engine.separate(audio.pcm, File(workDir, "stems")) { p ->
          step(2, StepStatus.RUNNING, p.fraction, "Chunk ${minOf(p.completedChunks + 1, p.totalChunks)} of ${p.totalChunks}")
        }
        step(
          2,
          StepStatus.DONE,
          detail = "${result.stems.joinToString { it.label }} • %.1fs (%.1f× real time)"
            .format(result.processingTimeMs / 1000f, result.speedFactor),
        )
        val rawEnvelopes = result.stems.map { stem -> async { Waveform.peaks(stem.pcm) } }.awaitAll()
        val envelopes = Waveform.normalizeTogether(rawEnvelopes)
        val maxPeak = rawEnvelopes.maxOfOrNull { it.maxOrNull() ?: 0f } ?: 0f
        val speechIndex = result.stems.indexOfFirst { it.type == StemType.SPEECH }
        val speechPeak = if (speechIndex >= 0) (rawEnvelopes[speechIndex].maxOrNull() ?: 0f) else 0f
        // Default to Studio Sound (speech isolation) when speech has substantial energy; otherwise
        // keep all stems unmuted so music-centric videos don't start muted.
        val initialPreset =
          if (speechIndex >= 0 && maxPeak > 0f && speechPeak >= maxPeak * 0.25f) {
            MixPreset.STUDIO
          } else {
            MixPreset.ORIGINAL
          }
        val stems = result.stems
          .mapIndexed { i, stem -> StemChannel(stem.id, stem.type, stem.label, stem.pcm, envelopes[i]) }
          .withPreset(initialPreset)

        // 4. Render the MP4 with the Studio Sound mix.
        step(3, StepStatus.RUNNING, 0f)
        val gains = stems.effectiveGains()
        StudioVideoRenderer.render(recording, stems.map { it.pcm }, gains, audio.startTimeUs, studioFile) { p ->
          step(3, StepStatus.RUNNING, p)
        }
        step(3, StepStatus.DONE)

        _state.update {
          it.copy(
            status = Status.READY,
            stems = stems,
            appliedGains = gains.toList(),
            processingTimeMs = result.processingTimeMs,
            monitor = Monitor.STUDIO,
            summary = "Separated ${result.audioDurationMs / 1000f}s of audio into ${result.stems.joinToString { s -> s.label }} in %.1fs."
              .format(result.processingTimeMs / 1000f),
          )
        }
        loadStudioPlayback(positionMs = 0, playWhenReady = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Processing failed", e)
        val message = describeStudioSoundError(e)
        _state.update { s ->
          s.copy(
            status = Status.FAILED,
            errorMessage = message,
            steps = s.steps.update(current) { it.copy(status = StepStatus.FAILED, detail = message) },
          )
        }
      }
    }
  }

  /**
   * Plays studio_sound.mp4 merged with the original recording. MergingMediaSource exposes both audio
   * tracks; switching between them is just a track-selection override, so A/B is near-instant and
   * keeps the video playing.
   */
  private fun loadStudioPlayback(positionMs: Long, playWhenReady: Boolean) {
    val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(app))
    val merged = MergingMediaSource(
      factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(studioFile))),
      factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(recording))),
    )
    player.setMediaSource(merged, positionMs)
    player.prepare()
    player.playWhenReady = playWhenReady
  }

  fun setMonitor(monitor: Monitor) {
    if (!_state.value.studioReady) return
    _state.update { it.copy(monitor = monitor) }
    applyMonitorSelection(player.currentTracks)
  }

  private fun applyMonitorSelection(tracks: Tracks) {
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (audioGroups.size < 2) return
    // MergingMediaPeriod prefixes track group ids with the child index: "0:" = studio, "1:" = original.
    val studio = _state.value.monitor == Monitor.STUDIO
    val target = audioGroups.firstOrNull { it.mediaTrackGroup.id.startsWith(if (studio) "0:" else "1:") }
      ?: audioGroups[if (studio) 0 else 1]
    if (target.isSelected) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
      .setOverrideForType(TrackSelectionOverride(target.mediaTrackGroup, 0))
      .build()
  }

  // region Mixer

  fun setGain(id: String, gain: Float) = updateStems(debounceMs = 250L) { it.update(id) { s -> s.withGain(gain) } }

  fun toggleMute(id: String) = updateStems(debounceMs = 0L) { it.update(id) { s -> s.copy(muted = !s.muted) } }

  fun toggleSolo(id: String) = updateStems(debounceMs = 0L) { it.update(id) { s -> s.copy(solo = !s.solo) } }

  fun applyPreset(preset: MixPreset) = updateStems(debounceMs = 0L) { it.withPreset(preset) }

  private fun updateStems(debounceMs: Long, transform: (List<StemChannel>) -> List<StemChannel>) {
    _state.update { it.copy(stems = transform(it.stems), monitor = Monitor.STUDIO) }
    scheduleRenderMix(debounceMs = debounceMs, showSnackbar = false)
  }

  /** Re-renders studio_sound.mp4 with the current mixer settings (stems are reused, so it's quick). */
  fun applyMix() {
    scheduleRenderMix(debounceMs = 0L, showSnackbar = true)
  }

  private fun scheduleRenderMix(debounceMs: Long, showSnackbar: Boolean) {
    val audio = decoded ?: return
    renderJob?.cancel()
    renderJob = viewModelScope.launch {
      if (debounceMs > 0L) delay(debounceMs)
      val stems = _state.value.stems
      val gains = stems.effectiveGains()
      if (gains.toList() == _state.value.appliedGains) return@launch
      _state.update { it.copy(isRendering = true, renderProgress = 0f) }
      try {
        StudioVideoRenderer.render(recording, stems.map { it.pcm }, gains, audio.startTimeUs, studioFile) { p ->
          _state.update { it.copy(renderProgress = p) }
        }
        val position = player.currentPosition
        val wasPlaying = player.isPlaying
        _state.update { it.copy(appliedGains = gains.toList(), monitor = Monitor.STUDIO) }
        loadStudioPlayback(position, wasPlaying)
        if (showSnackbar) _messages.send("New mix applied")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Render failed", e)
        _messages.send("Couldn't render the mix: ${e.message}")
      } finally {
        _state.update { it.copy(isRendering = false) }
      }
    }
  }

  // endregion

  fun saveToGallery() {
    if (!_state.value.studioReady || _state.value.isSaving) return
    viewModelScope.launch {
      _state.update { it.copy(isSaving = true) }
      try {
        withContext(Dispatchers.IO) { VideoStorage.saveToGallery(app, studioFile) }
        _messages.send("Saved to Movies/StudioSound")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Save failed", e)
        _messages.send("Couldn't save the video: ${e.message}")
      } finally {
        _state.update { it.copy(isSaving = false) }
      }
    }
  }

  override fun onCleared() {
    player.release()
  }

  companion object {
    const val ARG_FILE = "file"
    private const val TAG = "VideoResultViewModel"
  }
}
