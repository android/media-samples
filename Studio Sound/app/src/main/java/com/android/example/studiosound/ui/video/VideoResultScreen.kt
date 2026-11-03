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

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.android.example.studiosound.ui.components.CornerLabel
import com.android.example.studiosound.ui.components.EnhancingOverlay
import com.android.example.studiosound.ui.components.ErrorCard
import com.android.example.studiosound.ui.components.ModuleStatusDialog
import com.android.example.studiosound.ui.components.ModuleStatusPill
import com.android.example.studiosound.ui.components.PipelineCard
import com.android.example.studiosound.ui.components.PresetTileGrid
import com.android.example.studiosound.ui.components.SparkleGlyph
import com.android.example.studiosound.ui.components.StemRow
import com.android.example.studiosound.ui.components.formatDuration
import com.android.example.studiosound.ui.mix.effectiveGains
import com.android.example.studiosound.ui.mix.matchingPreset
import com.android.example.studiosound.ui.video.VideoResultViewModel.Monitor
import com.android.example.studiosound.ui.video.VideoResultViewModel.Status
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoResultScreen(onBack: () -> Unit, viewModel: VideoResultViewModel = viewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val availability by viewModel.availability.collectAsStateWithLifecycle()
  val ignoreDeviceAllowlist by viewModel.ignoreDeviceAllowlist.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var showStatusDialog by remember { mutableStateOf(false) }
  LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.player.pause() }

  if (showStatusDialog) {
    ModuleStatusDialog(
      availability = availability,
      allowlistIgnored = ignoreDeviceAllowlist,
      canIgnoreDeviceAllowlist = viewModel.canIgnoreDeviceAllowlist,
      playServicesVersion = viewModel.playServicesVersionName,
      isBusy = state.status == Status.PROCESSING,
      onDismiss = { showStatusDialog = false },
      onRetry = viewModel::retryModuleSetup,
      onInstall = viewModel::installModule,
      onSetIgnoreAllowlist = viewModel::setIgnoreDeviceAllowlist,
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      // Top bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Video Studio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "On-device AI · Google Play services",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        ModuleStatusPill(
          availability = availability,
          allowlistIgnored = ignoreDeviceAllowlist,
          onClick = { showStatusDialog = true },
        )
        if (state.studioReady) {
          Spacer(modifier = Modifier.width(8.dp))
          if (state.isSaving) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
          } else {
            FilledTonalIconButton(onClick = viewModel::saveToGallery) {
              Icon(Icons.Rounded.SaveAlt, contentDescription = "Save to gallery")
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Video Stage
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(310.dp)
          .clip(RoundedCornerShape(28.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(10.dp),
      ) {
        VideoPlayer(
          player = viewModel.player,
          isStudioActive = state.studioReady && state.monitor == Monitor.STUDIO,
          modifier = Modifier.fillMaxSize(),
        )
        if (state.status == Status.PROCESSING) {
          EnhancingOverlay(
            steps = state.steps,
            startedAtMs = state.startedAtElapsedMs,
            modifier = Modifier
              .matchParentSize()
              .clip(RoundedCornerShape(20.dp)),
          )
        }
      }

      MonitorToggle(
        monitor = state.monitor,
        enabled = state.studioReady,
        onChange = viewModel::setMonitor,
        modifier = Modifier.padding(vertical = 12.dp),
      )

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (state.status) {
          Status.PROCESSING -> item { PipelineCard(state.steps, title = "Applying Studio Sound…") }
          Status.FAILED -> {
            item { PipelineCard(state.steps, title = "Studio Sound failed") }
            item { ErrorCard(state.errorMessage ?: "Something went wrong.", onRetry = viewModel::process) }
          }
          Status.READY -> {
            val activePreset = state.stems.matchingPreset()
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Effects",
                  style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = activePreset?.label ?: "Custom mix",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            item {
              PresetTileGrid(
                selected = activePreset,
                onSelect = viewModel::applyPreset,
                latencyMs = state.processingTimeMs,
              )
            }
            item {
              Text(
                text = "Separated stems",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
            val gains = state.stems.effectiveGains()
            items(state.stems, key = { it.id }) { stem ->
              val index = state.stems.indexOf(stem)
              StemRow(
                stem = stem,
                audible = gains.getOrElse(index) { 0f } > 0f,
                onGainChange = { viewModel.setGain(stem.id, it) },
                onToggleMute = { viewModel.toggleMute(stem.id) },
                onToggleSolo = { viewModel.toggleSolo(stem.id) },
              )
            }
            item {
              val canApply = state.mixChanged && !state.isRendering
              Button(
                onClick = viewModel::applyMix,
                enabled = canApply,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 56.dp),
              ) {
                SparkleGlyph(
                  color = if (canApply) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  when {
                    state.isRendering -> "Rendering…"
                    state.mixChanged -> "Apply mix to video"
                    else -> "Mix applied"
                  },
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              if (state.isRendering) {
                LinearProgressIndicator(
                  progress = { state.renderProgress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                )
              }
            }
            state.summary?.let { summary ->
              item {
                Text(
                  summary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 4.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MonitorToggle(
  monitor: Monitor,
  enabled: Boolean,
  onChange: (Monitor) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = CircleShape,
    color = colors.surfaceContainerHigh,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      listOf(
        Monitor.ORIGINAL to "Original",
        Monitor.STUDIO to "Studio Sound",
      ).forEach { (item, label) ->
        val selected = monitor == item
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent, CircleShape)
            .clickable(enabled = enabled) { onChange(item) }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
  player: Player,
  isStudioActive: Boolean,
  modifier: Modifier = Modifier,
) {
  val playPause = rememberPlayPauseButtonState(player)
  var positionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  LaunchedEffect(player) {
    while (true) {
      positionMs = player.currentPosition
      durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
      delay(100)
    }
  }

  Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { playPause.onClick() },
      contentAlignment = Alignment.Center,
    ) {
      // TextureView so the video respects the rounded clip.
      ContentFrame(
        player = player,
        modifier = Modifier.fillMaxSize(),
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Fit,
      )
      if (playPause.showPlay) {
        FilledIconButton(
          onClick = playPause::onClick,
          enabled = playPause.isEnabled,
          colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
          modifier = Modifier.size(64.dp),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(36.dp))
        }
      }
      CornerLabel(
        text = if (isStudioActive) "AFTER · STUDIO SOUND" else "BEFORE · ORIGINAL",
        visible = true,
        modifier = Modifier.align(Alignment.BottomStart),
      )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
        Icon(
          if (playPause.showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
          contentDescription = if (playPause.showPlay) "Play" else "Pause",
          tint = Color.White,
        )
      }
      val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
      Text(
        formatDuration((fraction * durationMs).toLong()),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
      )
      Slider(
        value = fraction,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
          dragFraction?.let { player.seekTo((it * durationMs).toLong()) }
          dragFraction = null
        },
        enabled = durationMs > 0,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      )
      Text(
        formatDuration(durationMs),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
      )
      Spacer(Modifier.width(8.dp))
    }
  }
}
