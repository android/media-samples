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
package com.android.example.studiosound.ui.audio

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.example.studiosound.audio.AudioDecoder
import com.android.example.studiosound.audio.PcmFile
import com.android.example.studiosound.audio.StemPlayer
import com.android.example.studiosound.audio.WavWriter
import com.android.example.studiosound.audio.Waveform
import com.android.example.studiosound.samples.SampleAsset
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
import com.android.example.studiosound.ui.mix.matchingPreset
import com.android.example.studiosound.ui.mix.update
import com.android.example.studiosound.ui.mix.withGain
import com.android.example.studiosound.ui.mix.withPreset
import java.io.File
import java.io.IOException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Load an audio file or bundled sample preset → split it into stems with Studio Sound → mix, audition and export them. */
class AudioSplitViewModel(
  application: Application,
  savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

  enum class Status { IDLE, PROCESSING, READY, FAILED }

  enum class Monitor { ORIGINAL, MIX }

  data class SourceInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val mimeType: String?,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
  )

  data class UiState(
    val source: SourceInfo? = null,
    val selectedSample: SampleAsset? = null,
    val isLoadingSource: Boolean = false,
    val status: Status = Status.IDLE,
    val steps: List<PipelineStep> = emptyList(),
    val startedAtElapsedMs: Long = 0L,
    val processingTimeMs: Long? = null,
    val speedFactor: Float? = null,
    val errorMessage: String? = null,
    val stems: List<StemChannel> = emptyList(),
    val originalPeaks: FloatArray = FloatArray(0),
    val selectedPreset: MixPreset = MixPreset.STUDIO,
    val monitor: Monitor = Monitor.MIX,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val summary: String? = null,
    val isExporting: Boolean = false,
  ) {
    val isProcessing: Boolean get() = status == Status.PROCESSING
  }

  sealed interface ExportTarget {
    val suggestedName: String

    data class Mix(override val suggestedName: String) : ExportTarget

    data class SingleStem(val stemId: String, override val suggestedName: String) : ExportTarget
  }

  private data class CachedSample(
    val info: SourceInfo,
    val decoded: AudioDecoder.Result,
    val peaks: FloatArray,
  )

  private val app = application
  private val engine = application.studioSound
  val availability: StateFlow<StudioSoundAvailability> = engine.availability
  val ignoreDeviceAllowlist: StateFlow<Boolean> = engine.ignoreDeviceAllowlist
  val canIgnoreDeviceAllowlist: Boolean = engine.canIgnoreDeviceAllowlist
  val playServicesVersionName: String get() = engine.playServicesVersionName
  private val workDir = File(application.cacheDir, "audio_split")
  private val samplesCacheDir = File(application.cacheDir, "decoded_samples").apply { mkdirs() }
  private val sampleCache = java.util.concurrent.ConcurrentHashMap<String, CachedSample>()

  private val _state = MutableStateFlow(UiState())
  val state: StateFlow<UiState> = _state.asStateFlow()

  private val _positionMs = MutableStateFlow(0L)
  val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

  private val _messages = Channel<String>(Channel.BUFFERED)
  val messages = _messages.receiveAsFlow()

  private var loadJob: Job? = null
  private var processingJob: Job? = null
  private var tickerJob: Job? = null
  private var player: StemPlayer? = null
  private var decodedOriginal: AudioDecoder.Result? = null
  private var originalPcm: PcmFile? = null
  private var pendingExport: ExportTarget? = null

  init {
    if (engine.availability.value !is StudioSoundAvailability.Ready) {
      engine.refresh()
    }
    val requestedSample = savedStateHandle.get<String>(ARG_SAMPLE)?.takeIf { it.isNotBlank() }?.let(SampleAssets::byId)
    val initialSample = requestedSample ?: SampleAssets.DEFAULT
    onSamplePicked(initialSample, autoSplit = requestedSample != null)

    // Pre-decode the remaining bundled presets in the background so switching templates is instant.
    viewModelScope.launch(Dispatchers.IO) {
      SampleAssets.ALL.filter { it.id != initialSample.id }.forEach { sample ->
        runCatching { decodeAndCacheSample(sample) }
      }
    }
  }

  private suspend fun decodeAndCacheSample(sample: SampleAsset): CachedSample {
    sampleCache[sample.id]?.takeIf { it.decoded.pcm.file.exists() && it.decoded.pcm.file.length() > 0L }?.let {
      return it
    }
    return withContext(Dispatchers.IO) {
      val file = SampleAssets.materializeForAudio(app, sample)
      val rawInfo = querySource(Uri.fromFile(file), displayName = sample.title)
      val pcmTarget = File(samplesCacheDir, "${sample.id}.pcm")
      val decoded = AudioDecoder.decode(app, rawInfo.uri, pcmTarget)
      val peaks = Waveform.normalizeTogether(listOf(Waveform.peaks(decoded.pcm))).single()
      val fullInfo = rawInfo.copy(
        durationMs = decoded.pcm.durationMs,
        sampleRateHz = decoded.pcm.sampleRate,
        channelCount = decoded.pcm.channelCount,
      )
      CachedSample(fullInfo, decoded, peaks).also { sampleCache[sample.id] = it }
    }
  }

  fun installModule() = engine.install()

  fun retryModuleSetup() = engine.refresh()

  fun setIgnoreDeviceAllowlist(enabled: Boolean) = engine.setIgnoreDeviceAllowlist(enabled)

  fun onAudioPicked(uri: Uri) {
    loadSource(
      selectedSample = null,
      autoSplit = false,
      resolve = {
        val info = querySource(uri)
        workDir.deleteRecursively()
        workDir.mkdirs()
        val decoded = AudioDecoder.decode(app, info.uri, File(workDir, "original.pcm"))
        val peaks = Waveform.normalizeTogether(listOf(Waveform.peaks(decoded.pcm))).single()
        val fullInfo = info.copy(
          durationMs = decoded.pcm.durationMs,
          sampleRateHz = decoded.pcm.sampleRate,
          channelCount = decoded.pcm.channelCount,
        )
        CachedSample(fullInfo, decoded, peaks)
      },
    )
  }

  fun onSamplePicked(sample: SampleAsset, autoSplit: Boolean = false) {
    val current = _state.value
    if (sample.id == current.selectedSample?.id && !current.isLoadingSource && current.source != null && !autoSplit) {
      return
    }
    val cached = sampleCache[sample.id]?.takeIf { it.decoded.pcm.file.exists() && it.decoded.pcm.file.length() > 0L }
    if (cached != null && !autoSplit) {
      processingJob?.cancel()
      loadJob?.cancel()
      releasePlayer()
      decodedOriginal = cached.decoded
      originalPcm = cached.decoded.pcm
      _positionMs.value = 0L
      val previewPlayer = StemPlayer(listOf(cached.decoded.pcm)) { onPlaybackEnded() }
      previewPlayer.setGains(floatArrayOf(1f))
      player = previewPlayer
      _state.update {
        UiState(
          source = cached.info,
          selectedSample = sample,
          isLoadingSource = false,
          status = Status.IDLE,
          selectedPreset = it.selectedPreset,
          originalPeaks = cached.peaks,
          durationMs = previewPlayer.durationMs,
          monitor = Monitor.ORIGINAL,
        )
      }
      return
    }

    loadSource(
      selectedSample = sample,
      autoSplit = autoSplit,
      resolve = { decodeAndCacheSample(sample) },
    )
  }

  private fun loadSource(
    selectedSample: SampleAsset?,
    autoSplit: Boolean,
    resolve: suspend () -> CachedSample,
  ) {
    processingJob?.cancel()
    loadJob?.cancel()
    releasePlayer()
    decodedOriginal = null
    originalPcm = null
    _positionMs.value = 0L

    _state.update { prev ->
      val placeholderSource = selectedSample?.let { sample ->
        SourceInfo(
          uri = Uri.EMPTY,
          name = sample.title,
          sizeBytes = null,
          durationMs = prev.durationMs.takeIf { it > 0L },
          mimeType = "audio/mp4",
        )
      } ?: prev.source
      UiState(
        source = placeholderSource,
        selectedSample = selectedSample,
        isLoadingSource = true,
        selectedPreset = prev.selectedPreset,
        originalPeaks = selectedSample?.previewPeaks ?: prev.originalPeaks,
        durationMs = prev.durationMs,
      )
    }

    loadJob = viewModelScope.launch {
      try {
        val loaded = withContext(Dispatchers.IO) { resolve() }
        decodedOriginal = loaded.decoded
        originalPcm = loaded.decoded.pcm
        val previewPlayer = StemPlayer(listOf(loaded.decoded.pcm)) { onPlaybackEnded() }
        previewPlayer.setGains(floatArrayOf(1f))
        player = previewPlayer

        _state.update {
          it.copy(
            source = loaded.info,
            isLoadingSource = false,
            originalPeaks = loaded.peaks,
            durationMs = previewPlayer.durationMs,
            monitor = Monitor.ORIGINAL,
          )
        }
        if (autoSplit) split()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load audio source", e)
        _state.update {
          it.copy(
            isLoadingSource = false,
            status = Status.FAILED,
            errorMessage = "Couldn't load audio file: ${e.message ?: e.javaClass.simpleName}",
          )
        }
      }
    }
  }

  fun split() {
    val source = _state.value.source ?: return
    loadJob?.cancel()
    processingJob?.cancel()
    releasePlayer()
    processingJob = viewModelScope.launch {
      var current = 0
      fun step(index: Int, status: StepStatus, progress: Float? = null, detail: String? = null) {
        current = index
        _state.update { s ->
          s.copy(steps = s.steps.update(index) { it.copy(status = status, progress = progress, detail = detail ?: it.detail) })
        }
      }

      val runStartedAt = SystemClock.elapsedRealtime()
      _state.update {
        it.copy(
          status = Status.PROCESSING,
          isLoadingSource = false,
          startedAtElapsedMs = runStartedAt,
          processingTimeMs = null,
          speedFactor = null,
          errorMessage = null,
          stems = emptyList(),
          summary = null,
          steps = listOf(
            PipelineStep("Check Studio Sound module"),
            PipelineStep("Decode input audio"),
            PipelineStep("Separate stems on-device"),
            PipelineStep("Build real-time stem mixer"),
          ),
        )
      }
      try {
        // 1. Make sure the device is supported and the module is installed (downloads it if needed).
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

        // 2. Decode to float PCM (or reuse already decoded original).
        step(1, StepStatus.RUNNING, 0f)
        val existingDecoded = decodedOriginal?.takeIf { it.pcm.file.exists() && it.pcm.file.length() > 0L }
        val decoded = existingDecoded ?: run {
          withContext(Dispatchers.IO) {
            workDir.deleteRecursively()
            workDir.mkdirs()
          }
          AudioDecoder.decode(app, source.uri, File(workDir, "original.pcm")) { p ->
            step(1, StepStatus.RUNNING, p)
          }
        }
        decodedOriginal = decoded
        val channelsLabel = if (decoded.pcm.channelCount == 1) "Mono" else "Stereo"
        step(1, StepStatus.DONE, detail = "${decoded.pcm.sampleRate / 1000.0} kHz $channelsLabel · ${decoded.sourceMime}")

        // 3. Studio Sound separation.
        step(2, StepStatus.RUNNING, 0f)
        val stemsDir = File(workDir, "stems").apply { deleteRecursively(); mkdirs() }
        val result = engine.separate(decoded.pcm, stemsDir) { p ->
          step(2, StepStatus.RUNNING, p.fraction, "Chunk ${minOf(p.completedChunks + 1, p.totalChunks)} of ${p.totalChunks}")
        }
        step(
          2,
          StepStatus.DONE,
          detail = "${result.stems.size} stems in %.1fs (%.1f× real time)".format(result.processingTimeMs / 1000f, result.speedFactor),
        )

        // 4. Waveforms.
        step(3, StepStatus.RUNNING)
        val envelopes = (listOf(decoded.pcm) + result.stems.map { it.pcm })
          .map { pcm -> async { Waveform.peaks(pcm) } }
          .awaitAll()
        val normalized = Waveform.normalizeTogether(envelopes)
        step(3, StepStatus.DONE)

        originalPcm = decoded.pcm
        val hasSpeech = result.stems.any { it.type == StemType.SPEECH }
        val targetPreset = _state.value.selectedPreset.let { chosen ->
          if (!hasSpeech && chosen == MixPreset.STUDIO) MixPreset.ORIGINAL else chosen
        }
        val stems = result.stems
          .mapIndexed { i, stem -> StemChannel(stem.id, stem.type, stem.label, stem.pcm, normalized[i + 1]) }
          .withPreset(targetPreset)
        val newPlayer = StemPlayer(listOf(decoded.pcm) + result.stems.map { it.pcm }) { onPlaybackEnded() }
        player = newPlayer
        _state.update {
          it.copy(
            status = Status.READY,
            stems = stems,
            originalPeaks = normalized[0],
            selectedPreset = stems.matchingPreset() ?: targetPreset,
            durationMs = newPlayer.durationMs,
            processingTimeMs = result.processingTimeMs,
            speedFactor = result.speedFactor,
            monitor = Monitor.MIX,
            summary = "Separated into ${stems.joinToString { s -> s.label }} in %.1fs (%.1f× real time)."
              .format(result.processingTimeMs / 1000f, result.speedFactor),
          )
        }
        _positionMs.value = 0
        applyGains()
      } catch (e: CancellationException) {
        // Restore original preview player if available
        originalPcm?.takeIf { it.file.exists() }?.let { pcm ->
          val previewPlayer = StemPlayer(listOf(pcm)) { onPlaybackEnded() }
          previewPlayer.setGains(floatArrayOf(1f))
          player = previewPlayer
        }
        _state.update { it.copy(status = Status.IDLE, steps = emptyList(), monitor = Monitor.ORIGINAL) }
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Split failed", e)
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

  fun cancel() {
    processingJob?.cancel()
  }

  // region Playback

  fun togglePlayback() {
    val p = player ?: return
    if (p.isPlaying) pause() else {
      p.play()
      _state.update { it.copy(isPlaying = true) }
      startTicker()
    }
  }

  fun pause() {
    val p = player ?: return
    p.pause()
    tickerJob?.cancel()
    _state.update { it.copy(isPlaying = false) }
    _positionMs.value = p.positionMs()
  }

  fun seekTo(positionMs: Long) {
    player?.seekTo(positionMs)
    _positionMs.value = positionMs
  }

  fun setMonitor(monitor: Monitor) {
    if (_state.value.status != Status.READY && monitor == Monitor.MIX) return
    _state.update { it.copy(monitor = monitor) }
    applyGains()
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = viewModelScope.launch {
      while (isActive) {
        player?.let { _positionMs.value = it.positionMs() }
        delay(33)
      }
    }
  }

  private fun onPlaybackEnded() {
    viewModelScope.launch {
      tickerJob?.cancel()
      _state.update { it.copy(isPlaying = false) }
      _positionMs.value = _state.value.durationMs
    }
  }

  // endregion

  // region Mixer

  fun setGain(id: String, gain: Float) = updateStems { it.update(id) { s -> s.withGain(gain) } }

  fun toggleMute(id: String) = updateStems { it.update(id) { s -> s.copy(muted = !s.muted) } }

  fun toggleSolo(id: String) = updateStems { it.update(id) { s -> s.copy(solo = !s.solo) } }

  fun applyPreset(preset: MixPreset) {
    if (_state.value.status == Status.READY) {
      updateStems { it.withPreset(preset) }
      _state.update { it.copy(selectedPreset = preset) }
    } else {
      _state.update { it.copy(selectedPreset = preset) }
    }
  }

  private fun updateStems(transform: (List<StemChannel>) -> List<StemChannel>) {
    // Touching the mixer implies the user wants to hear the mix.
    _state.update {
      val updated = transform(it.stems)
      it.copy(
        stems = updated,
        selectedPreset = updated.matchingPreset() ?: it.selectedPreset,
        monitor = Monitor.MIX,
      )
    }
    applyGains()
  }

  /** Track 0 is the original; tracks 1..n are the stems. */
  private fun applyGains() {
    val s = _state.value
    if (s.stems.isEmpty()) {
      player?.setGains(floatArrayOf(1f))
      return
    }
    val stemGains = s.stems.effectiveGains()
    val gains = FloatArray(stemGains.size + 1)
    if (s.monitor == Monitor.ORIGINAL) gains[0] = 1f else stemGains.copyInto(gains, 1)
    player?.setGains(gains)
  }

  // endregion

  // region Export

  /** Remembers what to export; the UI then asks the user where to save it. */
  fun prepareExport(target: ExportTarget): String {
    pendingExport = target
    return target.suggestedName
  }

  fun mixExportTarget(): ExportTarget = ExportTarget.Mix("${baseName()}_studio_mix.wav")

  fun stemExportTarget(stem: StemChannel): ExportTarget =
    ExportTarget.SingleStem(stem.id, "${baseName()}_${stem.id}.wav")

  fun onExportDestination(uri: Uri) {
    val target = pendingExport ?: return
    pendingExport = null
    val stems = _state.value.stems
    val (tracks, gains, label) = when (target) {
      is ExportTarget.Mix -> Triple(stems.map { it.pcm }, stems.effectiveGains(), "mix")
      is ExportTarget.SingleStem -> {
        val stem = stems.firstOrNull { it.id == target.stemId } ?: return
        Triple(listOf(stem.pcm), floatArrayOf(1f), stem.label)
      }
    }
    viewModelScope.launch {
      _state.update { it.copy(isExporting = true) }
      try {
        withContext(Dispatchers.IO) {
          app.contentResolver.openOutputStream(uri)?.use { out -> WavWriter.write(out, tracks, gains) }
            ?: throw IOException("Couldn't open the destination file")
        }
        _messages.send("Exported $label as WAV")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Export failed", e)
        _messages.send("Export failed: ${e.message}")
      } finally {
        _state.update { it.copy(isExporting = false) }
      }
    }
  }

  private fun baseName(): String =
    _state.value.source?.name?.substringBeforeLast('.')?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "audio"

  // endregion

  private fun querySource(uri: Uri, displayName: String? = null): SourceInfo {
    val resolver = app.contentResolver
    var name = displayName ?: uri.lastPathSegment ?: "Audio"
    var size: Long? = null
    if (uri.scheme == "file") {
      uri.path?.let { path ->
        val file = File(path)
        if (displayName == null) name = file.name
        if (file.exists()) size = file.length()
      }
    } else {
      runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
          if (c.moveToFirst()) {
            c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
            c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
          }
        }
      }
    }
    var mimeType = resolver.getType(uri)
    val duration = runCatching {
      MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(app, uri)
        if (mimeType == null) {
          mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
      }
    }.getOrNull()
    if (mimeType == null) {
      val ext = name.substringAfterLast('.', "").lowercase()
      if (ext.isNotEmpty()) mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
    return SourceInfo(uri, name, size, duration, mimeType)
  }

  private fun releasePlayer() {
    tickerJob?.cancel()
    player?.close()
    player = null
    _state.update { it.copy(isPlaying = false) }
  }

  override fun onCleared() {
    releasePlayer()
  }

  companion object {
    const val ARG_SAMPLE = "sample"
    private const val TAG = "AudioSplitViewModel"
  }
}
