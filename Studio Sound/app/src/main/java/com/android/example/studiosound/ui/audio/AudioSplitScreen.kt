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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.example.studiosound.samples.SampleAsset
import com.android.example.studiosound.studiosound.StudioSoundAvailability
import com.android.example.studiosound.ui.audio.AudioSplitViewModel.Monitor
import com.android.example.studiosound.ui.audio.AudioSplitViewModel.Status
import com.android.example.studiosound.ui.components.AudioStageGlyph
import com.android.example.studiosound.ui.components.CornerLabel
import com.android.example.studiosound.ui.components.EnhancingOverlay
import com.android.example.studiosound.ui.components.ErrorCard
import com.android.example.studiosound.ui.components.GlassPill
import com.android.example.studiosound.ui.components.ModuleNotice
import com.android.example.studiosound.ui.components.ModuleStatusDialog
import com.android.example.studiosound.ui.components.ModuleStatusPill
import com.android.example.studiosound.ui.components.PresetTileGrid
import com.android.example.studiosound.ui.components.SamplePresetRow
import com.android.example.studiosound.ui.components.SparkleGlyph
import com.android.example.studiosound.ui.components.StemRow
import com.android.example.studiosound.ui.components.WaveformBars
import com.android.example.studiosound.ui.components.color
import com.android.example.studiosound.ui.components.formatDuration
import com.android.example.studiosound.ui.components.formatLatency
import com.android.example.studiosound.ui.components.formatMemorySize
import com.android.example.studiosound.ui.mix.StemChannel
import com.android.example.studiosound.ui.mix.effectiveGains

private val AUDIO_MIME_TYPES = arrayOf("audio/*", "video/*")

/**
 * Single-screen Studio Sound editor: a dark rounded stage at the top (or left on wide screens)
 * showing the waveform, preset samples, and live Original vs Studio Sound A/B comparison, paired
 * with the EffectTile preset grid and Stem Mixer underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSplitScreen(
  onBack: (() -> Unit)? = null,
  onRecordVideo: (() -> Unit)? = null,
  viewModel: AudioSplitViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val availability by viewModel.availability.collectAsStateWithLifecycle()
  val ignoreDeviceAllowlist by viewModel.ignoreDeviceAllowlist.collectAsStateWithLifecycle()
  val position = viewModel.positionMs.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var showAudioSheet by remember { mutableStateOf(false) }
  var showStatusDialog by remember { mutableStateOf(false) }

  LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::onAudioPicked)
  }
  val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
    uri?.let(viewModel::onExportDestination)
  }
  val pickFromDevice = { picker.launch(AUDIO_MIME_TYPES) }
  val openAudioSwitcher = { showAudioSheet = true }

  if (showStatusDialog) {
    ModuleStatusDialog(
      availability = availability,
      allowlistIgnored = ignoreDeviceAllowlist,
      canIgnoreDeviceAllowlist = viewModel.canIgnoreDeviceAllowlist,
      playServicesVersion = viewModel.playServicesVersionName,
      isBusy = state.isProcessing,
      onDismiss = { showStatusDialog = false },
      onRetry = viewModel::retryModuleSetup,
      onInstall = viewModel::installModule,
      onSetIgnoreAllowlist = viewModel::setIgnoreDeviceAllowlist,
    )
  }

  if (showAudioSheet) {
    AudioPickerBottomSheet(
      selectedSample = state.selectedSample,
      onDismiss = { showAudioSheet = false },
      onPickFromDevice = {
        showAudioSheet = false
        pickFromDevice()
      },
      onRecordVideo = onRecordVideo?.let { record ->
        {
          showAudioSheet = false
          record()
        }
      },
      onSelectSample = { sample ->
        showAudioSheet = false
        viewModel.onSamplePicked(sample, autoSplit = false)
      },
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbar) },
  ) { paddingValues ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
    ) {
      val wide = maxWidth > maxHeight
      if (wide) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            TopBar(
              availability = availability,
              allowlistIgnored = ignoreDeviceAllowlist,
              isBusy = state.isProcessing,
              onBack = onBack,
              onStatusClick = { showStatusDialog = true },
              onRecordVideo = onRecordVideo,
              onPickAudio = openAudioSwitcher,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AudioStage(
              state = state,
              position = position,
              onPickFromDevice = pickFromDevice,
              onSelectSample = { sample -> viewModel.onSamplePicked(sample, autoSplit = false) },
              onTogglePlay = viewModel::togglePlayback,
              onSeek = viewModel::seekTo,
              onMonitorChange = viewModel::setMonitor,
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            )
          }
          ControlPanel(
            state = state,
            availability = availability,
            position = position,
            viewModel = viewModel,
            onPickAudio = openAudioSwitcher,
            onExportMix = { exporter.launch(viewModel.prepareExport(viewModel.mixExportTarget())) },
            onExportStem = { stem -> exporter.launch(viewModel.prepareExport(viewModel.stemExportTarget(stem))) },
            modifier = Modifier
              .width(380.dp)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
          TopBar(
            availability = availability,
            allowlistIgnored = ignoreDeviceAllowlist,
            isBusy = state.isProcessing,
            onBack = onBack,
            onStatusClick = { showStatusDialog = true },
            onRecordVideo = onRecordVideo,
            onPickAudio = openAudioSwitcher,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            AudioStage(
              state = state,
              position = position,
              onPickFromDevice = pickFromDevice,
              onSelectSample = { sample -> viewModel.onSamplePicked(sample, autoSplit = false) },
              onTogglePlay = viewModel::togglePlayback,
              onSeek = viewModel::seekTo,
              onMonitorChange = viewModel::setMonitor,
              modifier = Modifier.fillMaxWidth(),
            )
            ControlPanel(
              state = state,
              availability = availability,
              position = position,
              viewModel = viewModel,
              onPickAudio = openAudioSwitcher,
              onExportMix = { exporter.launch(viewModel.prepareExport(viewModel.mixExportTarget())) },
              onExportStem = { stem -> exporter.launch(viewModel.prepareExport(viewModel.stemExportTarget(stem))) },
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun TopBar(
  availability: StudioSoundAvailability,
  allowlistIgnored: Boolean,
  isBusy: Boolean,
  onBack: (() -> Unit)?,
  onStatusClick: () -> Unit,
  onRecordVideo: (() -> Unit)?,
  onPickAudio: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Studio Sound",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "On-device AI · Google Play services",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    ModuleStatusPill(
      availability = availability,
      allowlistIgnored = allowlistIgnored,
      onClick = onStatusClick,
    )
    if (onRecordVideo != null) {
      Spacer(modifier = Modifier.width(6.dp))
      FilledTonalIconButton(onClick = onRecordVideo, enabled = !isBusy) {
        Icon(
          Icons.Rounded.Videocam,
          contentDescription = "Record video",
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(6.dp))
    FilledTonalIconButton(onClick = onPickAudio, enabled = !isBusy) {
      AudioStageGlyph(
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Audio stage
// ---------------------------------------------------------------------------------------------

@Composable
private fun AudioStage(
  state: AudioSplitViewModel.UiState,
  position: State<Long>,
  onPickFromDevice: () -> Unit,
  onSelectSample: (SampleAsset) -> Unit,
  onTogglePlay: () -> Unit,
  onSeek: (Long) -> Unit,
  onMonitorChange: (Monitor) -> Unit,
  modifier: Modifier = Modifier,
) {
  val source = state.source
  val colors = MaterialTheme.colorScheme

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(28.dp))
      .background(colors.surfaceContainerLow),
    contentAlignment = Alignment.Center,
  ) {
    when {
      source == null && state.isLoadingSource -> {
        Box(modifier = Modifier.heightIn(min = 260.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }

      source == null -> {
        EmptyAudioStage(
          selectedSample = state.selectedSample,
          onPickFromDevice = onPickFromDevice,
          onSelectSample = onSelectSample,
        )
      }

      else -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Inner waveform display stage
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(
                Brush.verticalGradient(
                  listOf(
                    colors.surfaceContainerLowest,
                    if (state.status == Status.READY && state.monitor == Monitor.MIX) {
                      colors.primaryContainer.copy(alpha = 0.38f)
                    } else {
                      colors.surfaceContainer
                    },
                  ),
                ),
              )
              .border(
                width = 1.dp,
                color = if (state.status == Status.READY && state.monitor == Monitor.MIX) {
                  colors.primary.copy(alpha = 0.45f)
                } else {
                  colors.outline.copy(alpha = 0.18f)
                },
                shape = RoundedCornerShape(18.dp),
              ),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              // Top GlassPill caption inside the stage
              val caption = rememberStageCaption(state, source)
              if (caption != null) {
                GlassPill(text = caption)
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Before / After Monitor Pill Row when separated
              if (state.status == Status.READY) {
                BeforeAfterMonitorSwitch(
                  monitor = state.monitor,
                  presetLabel = state.selectedPreset.shortTitle,
                  onMonitorChange = onMonitorChange,
                )
                Spacer(modifier = Modifier.height(10.dp))
              } else {
                Text(
                  text = state.selectedSample?.description
                    ?: "Tap Play to audition the original audio, or tap Enhance to separate stems.",
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  minLines = 2,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
              }

              // Interactive Waveform Stage + Transport
              StageWaveformPlayer(
                state = state,
                position = position,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
              )
            }

            if (state.isProcessing) {
              EnhancingOverlay(
                steps = state.steps,
                startedAtMs = state.startedAtElapsedMs,
                modifier = Modifier.matchParentSize(),
              )
            }
          }

          // Sample preset switcher row always accessible right inside the stage
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Preset samples",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              val isEnhancedActive = state.status == Status.READY && state.monitor == Monitor.MIX
              Text(
                text = if (isEnhancedActive) "AFTER · ${state.selectedPreset.shortTitle.uppercase()}" else "BEFORE · ORIGINAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isEnhancedActive) colors.primary else colors.outline,
              )
            }
            SamplePresetRow(
              selectedSample = state.selectedSample,
              onSelectSample = onSelectSample,
            )
          }
        }
      }
    }

    if (state.status == Status.FAILED && state.errorMessage != null) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp),
      ) {
        ErrorCard(message = state.errorMessage, onRetry = null)
      }
    }
  }
}

private fun rememberStageCaption(
  state: AudioSplitViewModel.UiState,
  source: AudioSplitViewModel.SourceInfo,
): String? {
  if (state.isProcessing) return null
  val title = state.selectedSample?.title ?: source.name
  return if (state.status == Status.READY) {
    val time = state.processingTimeMs?.let { " · ${formatLatency(it)}" } ?: ""
    val speed = state.speedFactor?.let { " (%.1f×)".format(it) } ?: ""
    "$title → ${state.selectedPreset.label} · ${state.stems.size} stems$time$speed"
  } else {
    val parts = buildList {
      add(title)
      val dur = state.durationMs.takeIf { it > 0 } ?: source.durationMs
      if (dur != null && dur > 0) add(formatDuration(dur))
      source.sampleRateHz?.let { sr ->
        val ch = when (source.channelCount) {
          1 -> "Mono"
          2 -> "Stereo"
          else -> null
        }
        add(if (ch != null) "${sr / 1000} kHz $ch" else "${sr / 1000} kHz")
      }
      source.sizeBytes?.let { add(formatMemorySize(it)) }
    }
    parts.joinToString(" · ")
  }
}

@Composable
private fun BeforeAfterMonitorSwitch(
  monitor: Monitor,
  presetLabel: String,
  onMonitorChange: (Monitor) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = CircleShape,
    color = colors.surfaceContainerHigh,
  ) {
    Row(
      modifier = Modifier.padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MonitorPillOption(
        text = "Original",
        selected = monitor == Monitor.ORIGINAL,
        selectedContainer = colors.secondaryContainer,
        selectedContent = colors.onSecondaryContainer,
        onClick = { onMonitorChange(Monitor.ORIGINAL) },
      )
      MonitorPillOption(
        text = "Studio · $presetLabel",
        selected = monitor == Monitor.MIX,
        selectedContainer = colors.primary,
        selectedContent = colors.onPrimary,
        onClick = { onMonitorChange(Monitor.MIX) },
      )
    }
  }
}

@Composable
private fun MonitorPillOption(
  text: String,
  selected: Boolean,
  selectedContainer: Color,
  selectedContent: Color,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .background(if (selected) selectedContainer else Color.Transparent, CircleShape)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) selectedContent else colors.onSurfaceVariant,
    )
  }
}

@Composable
private fun StageWaveformPlayer(
  state: AudioSplitViewModel.UiState,
  position: State<Long>,
  onTogglePlay: () -> Unit,
  onSeek: (Long) -> Unit,
) {
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val duration = state.durationMs.coerceAtLeast(1L)
  val fraction = dragFraction ?: (position.value.toFloat() / duration).coerceIn(0f, 1f)
  val colors = MaterialTheme.colorScheme

  // Compute an active mix waveform envelope when in MIX mode so the stage visually reflects stem changes
  val activePeaks = remember(state.status, state.monitor, state.stems, state.originalPeaks) {
    if (state.status == Status.READY && state.monitor == Monitor.MIX && state.stems.isNotEmpty()) {
      mixWaveformPeaks(state.stems, state.originalPeaks.size)
    } else {
      state.originalPeaks
    }
  }
  val activeColor = if (state.status == Status.READY && state.monitor == Monitor.MIX) {
    colors.primary
  } else {
    colors.secondary
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(76.dp),
      contentAlignment = Alignment.Center,
    ) {
      // Draw ghosted original envelope behind the Studio Mix envelope so the noise reduction is visible!
      if (state.status == Status.READY && state.monitor == Monitor.MIX && state.originalPeaks.isNotEmpty()) {
        WaveformBars(
          peaks = state.originalPeaks,
          color = colors.onSurfaceVariant.copy(alpha = 0.22f),
          progress = { 1f },
          inactiveAlpha = 0.22f,
          modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        )
      }
      if (activePeaks.isNotEmpty()) {
        WaveformBars(
          peaks = activePeaks,
          color = activeColor,
          progress = { dragFraction ?: (position.value.toFloat() / duration) },
          modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        )
      } else if (state.isLoadingSource) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
      }
    }

    Slider(
      value = fraction,
      onValueChange = { dragFraction = it },
      onValueChangeFinished = {
        dragFraction?.let { onSeek((it * duration).toLong()) }
        dragFraction = null
      },
      enabled = !state.isLoadingSource && !state.isProcessing && state.durationMs > 0,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = formatDuration((fraction * duration).toLong()),
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      FilledIconButton(
        onClick = onTogglePlay,
        enabled = !state.isLoadingSource && !state.isProcessing && state.durationMs > 0,
        modifier = Modifier.size(56.dp),
      ) {
        Icon(
          if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = if (state.isPlaying) "Pause" else "Play",
          modifier = Modifier.size(30.dp),
        )
      }
      Text(
        text = formatDuration(state.durationMs),
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

private fun mixWaveformPeaks(stems: List<StemChannel>, count: Int): FloatArray {
  if (count <= 0 || stems.isEmpty()) return FloatArray(0)
  val gains = stems.effectiveGains()
  return FloatArray(count) { i ->
    var sum = 0f
    for (s in stems.indices) {
      val peaks = stems[s].peaks
      if (i < peaks.size) {
        sum += peaks[i] * gains[s]
      }
    }
    sum.coerceIn(0f, 1f)
  }
}

@Composable
private fun EmptyAudioStage(
  selectedSample: SampleAsset?,
  onPickFromDevice: () -> Unit,
  onSelectSample: (SampleAsset) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .clip(RoundedCornerShape(24.dp))
        .clickable(onClick = onPickFromDevice)
        .padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(76.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        AudioStageGlyph(
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(38.dp),
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = "Choose audio to enhance",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Pick from your device, or tap a preset sample below.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 300.dp),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SamplePresetRow(
      selectedSample = selectedSample,
      onSelectSample = onSelectSample,
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Bottom Sheet Picker
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPickerBottomSheet(
  selectedSample: SampleAsset?,
  onDismiss: () -> Unit,
  onPickFromDevice: () -> Unit,
  onRecordVideo: (() -> Unit)?,
  onSelectSample: (SampleAsset) -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Sample presets",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp),
      )
      SamplePresetRow(
        selectedSample = selectedSample,
        onSelectSample = onSelectSample,
        modifier = Modifier.padding(horizontal = 20.dp),
      )
      OutlinedButton(
        onClick = onPickFromDevice,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 52.dp)
          .padding(horizontal = 20.dp),
      ) {
        AudioStageGlyph(
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Browse device audio or video",
          style = MaterialTheme.typography.titleSmall,
        )
      }
      if (onRecordVideo != null) {
        OutlinedButton(
          onClick = onRecordVideo,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp),
        ) {
          Icon(
            Icons.Rounded.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Record video with CameraX",
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Control panel
// ---------------------------------------------------------------------------------------------

@Composable
private fun ControlPanel(
  state: AudioSplitViewModel.UiState,
  availability: StudioSoundAvailability,
  position: State<Long>,
  viewModel: AudioSplitViewModel,
  onPickAudio: () -> Unit,
  onExportMix: () -> Unit,
  onExportStem: (StemChannel) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "Effects",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = state.selectedPreset.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    PresetTileGrid(
      selected = state.selectedPreset,
      onSelect = viewModel::applyPreset,
      enabled = !state.isProcessing,
      latencyMs = state.processingTimeMs,
      showExtendedChips = state.status == Status.READY,
    )

    ModuleNotice(
      availability = availability,
      onInstall = viewModel::installModule,
      onRetry = viewModel::retryModuleSetup,
    )

    val buttonModifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
    val canEnhance = state.source != null && !state.isLoadingSource && !state.isProcessing &&
      availability !is StudioSoundAvailability.Unsupported

    when {
      state.isProcessing -> OutlinedButton(onClick = viewModel::cancel, modifier = buttonModifier) {
        Text("Cancel", style = MaterialTheme.typography.titleMedium)
      }

      state.source == null -> Button(onClick = onPickAudio, modifier = buttonModifier) {
        AudioStageGlyph(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text("Choose audio", style = MaterialTheme.typography.titleMedium)
      }

      state.status == Status.READY -> Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = viewModel::split,
          enabled = canEnhance,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp),
        ) {
          Text(
            text = "Re-run",
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Button(
          onClick = onExportMix,
          enabled = !state.isExporting,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp),
        ) {
          Icon(
            Icons.Rounded.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (state.isExporting) "Exporting…" else "Export WAV",
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }

      else -> Button(
        onClick = viewModel::split,
        enabled = canEnhance,
        modifier = buttonModifier,
      ) {
        SparkleGlyph(
          color = if (canEnhance) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Enhance",
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }

    // Isolated Stem Mixer rows once separated
    AnimatedVisibility(visible = state.status == Status.READY && state.stems.isNotEmpty()) {
      val gains = state.stems.effectiveGains()
      val progress = { if (state.durationMs > 0) position.value.toFloat() / state.durationMs else 0f }
      val activeCount = gains.count { it > 0.01f }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 4.dp),
        ) {
          Text(
            text = "Separated stems",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "$activeCount of ${state.stems.size} audible",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        state.stems.forEachIndexed { index, stem ->
          StemRow(
            stem = stem,
            audible = state.monitor == Monitor.MIX && gains.getOrElse(index) { 0f } > 0f,
            progress = progress,
            onGainChange = { viewModel.setGain(stem.id, it) },
            onToggleMute = { viewModel.toggleMute(stem.id) },
            onToggleSolo = { viewModel.toggleSolo(stem.id) },
            onExport = { onExportStem(stem) },
          )
        }
        state.summary?.let { summary ->
          Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
      }
    }
  }
}
