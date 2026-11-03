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
package com.android.example.studiosound.ui.components

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SpatialAudioOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VoiceOverOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.example.studiosound.samples.SampleAsset
import com.android.example.studiosound.samples.SampleAssets
import com.android.example.studiosound.studiosound.InstallStage
import com.android.example.studiosound.studiosound.StemType
import com.android.example.studiosound.studiosound.StudioSoundAvailability
import com.android.example.studiosound.ui.mix.MAX_STEM_GAIN
import com.android.example.studiosound.ui.mix.MixPreset
import com.android.example.studiosound.ui.mix.StemChannel
import com.android.example.studiosound.ui.theme.BusyAmber
import com.android.example.studiosound.ui.theme.ProblemRed
import com.android.example.studiosound.ui.theme.ReadyGreen
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

val StemType.icon: ImageVector
  get() = when (this) {
    StemType.SPEECH -> Icons.Rounded.RecordVoiceOver
    StemType.MUSIC -> Icons.Rounded.MusicNote
    StemType.CROWD -> Icons.Rounded.Groups
    StemType.NATURE -> Icons.Rounded.Forest
    StemType.WIND -> Icons.Rounded.Air
    StemType.NOISE -> Icons.Rounded.GraphicEq
    StemType.OTHER -> Icons.Rounded.Audiotrack
  }

val StemType.color: Color
  @Composable
  get() = when (this) {
    StemType.SPEECH -> MaterialTheme.colorScheme.primary
    StemType.MUSIC -> MaterialTheme.colorScheme.tertiary
    StemType.CROWD -> MaterialTheme.colorScheme.tertiary
    StemType.NATURE -> MaterialTheme.colorScheme.secondary
    StemType.WIND -> MaterialTheme.colorScheme.secondary
    StemType.NOISE -> MaterialTheme.colorScheme.secondary
    StemType.OTHER -> MaterialTheme.colorScheme.outline
  }

val MixPreset.icon: ImageVector
  get() = when (this) {
    MixPreset.STUDIO -> Icons.Rounded.AutoAwesome
    MixPreset.VOICE_AMBIENCE -> Icons.Rounded.SpatialAudioOff
    MixPreset.NO_MUSIC -> Icons.Rounded.MusicOff
    MixPreset.NO_VOICE -> Icons.Rounded.VoiceOverOff
    MixPreset.ORIGINAL -> Icons.Rounded.Tune
  }

fun formatDuration(ms: Long): String {
  val totalSeconds = (ms.coerceAtLeast(0) / 1000)
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Formats an elapsed processing duration: "1.4 s" under 1 min, "1:12" above. */
fun formatLatency(millis: Long, locale: Locale = Locale.getDefault()): String {
  val clamped = millis.coerceAtLeast(0L)
  if (clamped < 60_000) return String.format(locale, "%.1f s", (clamped / 100) / 10.0)
  val totalSeconds = clamped / 1000
  return String.format(locale, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

fun formatMemorySize(bytes: Long): String {
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  return if (mb >= 1) String.format(Locale.getDefault(), "%.1f MB", mb)
  else String.format(Locale.getDefault(), "%.0f KB", kb)
}

/** Bar-style waveform. [progress] is read during drawing only, so playback doesn't recompose. */
@Composable
fun WaveformBars(
  peaks: FloatArray,
  color: Color,
  modifier: Modifier = Modifier,
  progress: () -> Float = { 0f },
  inactiveAlpha: Float = 0.28f,
) {
  Canvas(modifier) {
    if (peaks.isEmpty()) return@Canvas
    val count = peaks.size
    val slot = size.width / count
    val barWidth = (slot * 0.62f).coerceAtLeast(1f)
    val minHeight = 3.dp.toPx()
    val played = progress().coerceIn(0f, 1f)
    for (i in 0 until count) {
      val height = (peaks[i] * size.height).coerceAtLeast(minHeight)
      val isPlayed = (i + 0.5f) / count <= played
      drawRoundRect(
        color = if (isPlayed) color else color.copy(alpha = inactiveAlpha),
        topLeft = Offset(i * slot + (slot - barWidth) / 2, (size.height - height) / 2),
        size = Size(barWidth, height),
        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
      )
    }
  }
}

@Composable
fun StemBadge(type: StemType, modifier: Modifier = Modifier, size: Int = 36) {
  Box(
    modifier = modifier.size(size.dp).background(type.color.copy(alpha = 0.18f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(type.icon, contentDescription = null, tint = type.color, modifier = Modifier.size((size * 0.54f).dp))
  }
}

// ---------------------------------------------------------------------------------------------
// Top Bar, Status Pill & Status Dialog
// ---------------------------------------------------------------------------------------------

@Composable
fun ModuleStatusPill(
  availability: StudioSoundAvailability,
  allowlistIgnored: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (dot, text) = when (availability) {
    StudioSoundAvailability.Checking -> MaterialTheme.colorScheme.outline to "Checking"
    StudioSoundAvailability.Ready -> ReadyGreen to if (allowlistIgnored) "Ready · override" else "Ready"
    StudioSoundAvailability.ModuleNotInstalled -> BusyAmber to "Download module"
    is StudioSoundAvailability.Installing -> {
      val label = when (availability.stage) {
        InstallStage.PENDING -> "Pending…"
        InstallStage.DOWNLOADING -> "Downloading ${availability.progress ?: 0}%"
        InstallStage.PAUSED -> "Paused"
        InstallStage.INSTALLING -> "Installing…"
      }
      BusyAmber to label
    }
    is StudioSoundAvailability.Unsupported -> ProblemRed to "Not supported"
    is StudioSoundAvailability.Error -> ProblemRed to "Setup failed · Retry"
  }
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier
      .clip(CircleShape)
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .background(dot, CircleShape),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
fun ModuleStatusDialog(
  availability: StudioSoundAvailability,
  allowlistIgnored: Boolean,
  canIgnoreDeviceAllowlist: Boolean,
  playServicesVersion: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onRetry: () -> Unit,
  onInstall: () -> Unit,
  onSetIgnoreAllowlist: (Boolean) -> Unit,
) {
  val (dotColor, title, description) = when {
    availability == StudioSoundAvailability.Ready && !allowlistIgnored -> Triple(
      ReadyGreen,
      "Module Ready",
      "This device passed Google Play services' device allowlist check (isDeviceSupported() == true) and the on-demand MediaEffectStudiosound AI module is installed and ready.\n\nStem separation (Speech, Music, and Ambient Noise) runs locally on the device's NPU/GPU through Google Play services.",
    )
    availability == StudioSoundAvailability.Ready && allowlistIgnored -> Triple(
      BusyAmber,
      "Module Installed (Allowlist Override)",
      "The client SDK allowlist in play-services-media-effect-studiosound (16.0.0-beta03) does not yet list ${Build.MODEL} (${Build.DEVICE}), so isDeviceSupported() returned false.\n\nThe optional module APK is installed on the device via the debug override, but Google Play services may still refuse to create a session on this device [DEVICE_NOT_SUPPORTED].",
    )
    availability is StudioSoundAvailability.Unsupported -> Triple(
      ProblemRed,
      "Device Not Supported",
      "StudioSoundClient.isDeviceSupported() returned false for ${Build.MODEL} (${Build.DEVICE}).\n\nIn version 16.0.0-beta03 of play-services-media-effect-studiosound, Studio Sound is limited to supported Pixel 6–11 and recent Galaxy S models.\n\n${availability.reason}",
    )
    availability == StudioSoundAvailability.ModuleNotInstalled -> Triple(
      BusyAmber,
      "AI Module Not Installed",
      "This device supports Studio Sound, but the on-demand MediaEffectStudiosound model package from Google Play services has not been downloaded yet. Tap Download below to install it.",
    )
    availability is StudioSoundAvailability.Installing -> Triple(
      BusyAmber,
      "Downloading AI Module (${availability.progress ?: 0}%)",
      "Google Play services is downloading the on-demand MediaEffectStudiosound model package for the first time. Once complete, stem separation runs completely offline on the device.",
    )
    availability is StudioSoundAvailability.Error -> Triple(
      ProblemRed,
      "Module Setup Failed",
      availability.message,
    )
    else -> Triple(
      MaterialTheme.colorScheme.outline,
      "Checking Device Support…",
      "Querying Google Play services for device eligibility and module installation status.",
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .background(dotColor, CircleShape),
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            DiagnosticRow("Device", "${Build.MODEL} (${Build.DEVICE})")
            DiagnosticRow("SoC", Build.SOC_MODEL)
            DiagnosticRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            DiagnosticRow("Play services", playServicesVersion)
            DiagnosticRow("Client SDK", "16.0.0-beta03")
            DiagnosticRow(
              "Allowlist",
              when {
                availability == StudioSoundAvailability.Ready && !allowlistIgnored -> "Supported"
                allowlistIgnored -> "Overridden (debug)"
                availability is StudioSoundAvailability.Unsupported -> "Not on allowlist"
                else -> "Checking"
              },
            )
          }
        }

        if (canIgnoreDeviceAllowlist && (availability is StudioSoundAvailability.Unsupported || allowlistIgnored)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .toggleable(
                value = allowlistIgnored,
                enabled = !isBusy,
                role = Role.Switch,
                onValueChange = onSetIgnoreAllowlist,
              )
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Bypass client allowlist",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = "Debug override · attempts session via Play services",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = allowlistIgnored,
              onCheckedChange = null,
              enabled = !isBusy,
            )
          }
        }
      }
    },
    dismissButton = {
      if (availability == StudioSoundAvailability.ModuleNotInstalled) {
        TextButton(onClick = onInstall, enabled = !isBusy) {
          Text("Download")
        }
      } else {
        TextButton(onClick = onRetry, enabled = !isBusy) {
          Text("Re-check")
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("Done")
      }
    },
  )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun ModuleNotice(
  availability: StudioSoundAvailability,
  onInstall: () -> Unit,
  onRetry: () -> Unit,
) {
  when (availability) {
    StudioSoundAvailability.ModuleNotInstalled -> NoticeCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "Download the on-device Studio Sound module from Google Play services to separate stems.",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onInstall) { Text("Download") }
      }
    }
    is StudioSoundAvailability.Installing -> NoticeCard {
      val progress = availability.progress
      Text(
        text = "Downloading the Studio Sound module (first run only) · ${progress ?: 0}%",
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(8.dp))
      if (progress != null) {
        LinearProgressIndicator(
          progress = { progress / 100f },
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
    is StudioSoundAvailability.Error -> NoticeCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = availability.message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
      }
    }
    is StudioSoundAvailability.Unsupported -> NoticeCard {
      Text(
        text = availability.reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    else -> Unit
  }
}

@Composable
fun NoticeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
  }
}

// ---------------------------------------------------------------------------------------------
// Stage Overlays & Sample Presets
// ---------------------------------------------------------------------------------------------

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    color = Color.White,
    style = MaterialTheme.typography.labelLarge,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
      .background(Color.Black.copy(alpha = 0.58f), CircleShape)
      .padding(horizontal = 14.dp, vertical = 6.dp),
  )
}

@Composable
fun CornerLabel(text: String, visible: Boolean, modifier: Modifier = Modifier) {
  Text(
    text = text,
    color = Color.White,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    modifier = modifier
      .padding(10.dp)
      .alpha(if (visible) 1f else 0f)
      .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  )
}

/**
 * Dims the stage, sweeps an Android-green scan line across it and shows which Studio Sound step
 * is running and for how long.
 */
@Composable
fun EnhancingOverlay(
  steps: List<PipelineStep>,
  startedAtMs: Long,
  modifier: Modifier = Modifier,
) {
  val accent = MaterialTheme.colorScheme.primary
  val scan by rememberInfiniteTransition(label = "scan").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
    label = "scanPosition",
  )
  val now = rememberElapsedRealtime()
  val elapsed = if (startedAtMs > 0L) (now - startedAtMs).coerceAtLeast(0L) else 0L

  val runningIndex = steps.indexOfFirst { it.status == StepStatus.RUNNING }
  val runningStep = steps.getOrNull(runningIndex)
  val title = when {
    runningStep == null -> "Preparing Studio Sound…"
    steps.size <= 1 -> runningStep.title
    else -> "Step ${runningIndex + 1} of ${steps.size} · ${runningStep.title}"
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(Color.Black.copy(alpha = 0.48f))
      val y = size.height * scan
      val band = size.height * 0.16f
      drawRect(
        brush = Brush.verticalGradient(
          colors = listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent),
          startY = y - band,
          endY = y + band,
        ),
        topLeft = Offset(0f, y - band),
        size = Size(size.width, band * 2),
      )
      drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
    }

    Column(
      modifier = Modifier
        .padding(24.dp)
        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(24.dp))
        .padding(horizontal = 24.dp, vertical = 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = title,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
      )
      runningStep?.detail?.let { detail ->
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = detail,
          color = Color.White.copy(alpha = 0.72f),
          style = MaterialTheme.typography.labelMedium,
          textAlign = TextAlign.Center,
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = formatLatency(elapsed),
        color = Color.White,
        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

/**
 * Horizontal preset row for the 3 bundled sample audio files.
 */
@Composable
fun SamplePresetRow(
  selectedSample: SampleAsset?,
  onSelectSample: (SampleAsset) -> Unit,
  modifier: Modifier = Modifier,
  samples: List<SampleAsset> = SampleAssets.ALL,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    samples.forEach { sample ->
      val isSelected = sample.id == selectedSample?.id
      val shape = RoundedCornerShape(14.dp)
      val accentColor = when (sample.id) {
        SampleAssets.SAMPLE_1.id -> StemType.SPEECH.color
        SampleAssets.SAMPLE_2.id -> StemType.MUSIC.color
        SampleAssets.SAMPLE_3.id -> StemType.WIND.color
        else -> colors.primary
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .clip(shape)
          .clickable { onSelectSample(sample) },
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(
              Brush.verticalGradient(
                listOf(
                  accentColor.copy(alpha = if (isSelected) 0.30f else 0.16f),
                  colors.surfaceContainerHighest,
                ),
              ),
            )
            .then(
              if (isSelected) Modifier.border(2.dp, colors.primary, shape)
              else Modifier.border(1.dp, colors.outline.copy(alpha = 0.25f), shape)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
          Text(
            text = sample.badge,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) colors.primary else colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart),
          )
          WaveformBars(
            peaks = sample.previewPeaks,
            color = if (isSelected) colors.primary else accentColor,
            progress = { 1f },
            modifier = Modifier
              .fillMaxWidth()
              .height(22.dp)
              .align(Alignment.BottomCenter),
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = sample.title,
          style = MaterialTheme.typography.labelSmall,
          color = if (isSelected) colors.primary else colors.onSurface,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Effect Tiles for Mix Presets
// ---------------------------------------------------------------------------------------------

private val FEATURED_PRESETS = listOf(
  MixPreset.STUDIO,
  MixPreset.VOICE_AMBIENCE,
  MixPreset.NO_MUSIC,
)

@Composable
fun PresetTileGrid(
  selected: MixPreset?,
  onSelect: (MixPreset) -> Unit,
  enabled: Boolean = true,
  latencyMs: Long? = null,
  showExtendedChips: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      FEATURED_PRESETS.forEach { preset ->
        PresetEffectTile(
          preset = preset,
          selected = preset == selected,
          latencyMs = if (preset == selected) latencyMs else null,
          enabled = enabled,
          onClick = { onSelect(preset) },
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        )
      }
    }
    if (showExtendedChips) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(MixPreset.NO_VOICE, MixPreset.ORIGINAL).forEach { preset ->
          val isSelected = preset == selected
          FilterChip(
            selected = isSelected,
            onClick = { onSelect(preset) },
            enabled = enabled,
            label = { Text("${preset.shortTitle} · ${preset.label}") },
            leadingIcon = if (isSelected) {
              { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
              { Icon(preset.icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            },
          )
        }
      }
    }
  }
}

@Composable
fun PresetEffectTile(
  preset: MixPreset,
  selected: Boolean,
  latencyMs: Long?,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(20.dp),
    color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
    contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .background(if (selected) colors.primary else colors.surfaceVariant, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = preset.icon,
            contentDescription = null,
            tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        when {
          selected && latencyMs != null -> Text(
            text = formatLatency(latencyMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          selected -> CheckGlyph(color = colors.primary, modifier = Modifier.size(18.dp))
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = preset.shortTitle,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = preset.description,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.82f) else colors.onSurfaceVariant,
        maxLines = 2,
      )
    }
  }
}

@Composable
fun PresetRow(selected: MixPreset?, onSelect: (MixPreset) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    MixPreset.entries.forEach { preset ->
      val isSelected = preset == selected
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(preset) },
        label = { Text(preset.label) },
        leadingIcon =
          if (isSelected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
          } else {
            null
          },
      )
    }
  }
}

/**
 * A mixer channel strip for one stem.
 */
@Composable
fun StemRow(
  stem: StemChannel,
  audible: Boolean,
  onGainChange: (Float) -> Unit,
  onToggleMute: () -> Unit,
  onToggleSolo: () -> Unit,
  modifier: Modifier = Modifier,
  progress: () -> Float = { 0f },
  onExport: (() -> Unit)? = null,
) {
  val color = stem.type.color
  val contentAlpha = if (audible) 1f else 0.45f
  val percent = (stem.gain * 100).roundToInt()

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        StemBadge(stem.type, Modifier.alpha(contentAlpha), size = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
          Text(stem.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text(
            stem.type.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconToggleButton(checked = stem.muted, onCheckedChange = { onToggleMute() }) {
          val tint by animateColorAsState(
            if (stem.muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "muteTint",
          )
          Icon(
            if (stem.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = if (stem.muted) "Unmute ${stem.label}" else "Mute ${stem.label}",
            tint = tint,
            modifier = Modifier.size(20.dp),
          )
        }
        IconToggleButton(checked = stem.solo, onCheckedChange = { onToggleSolo() }) {
          val tint by animateColorAsState(
            if (stem.solo) color else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "soloTint",
          )
          Icon(
            Icons.Rounded.Headphones,
            contentDescription = "Solo ${stem.label}",
            tint = tint,
            modifier = Modifier.size(20.dp),
          )
        }
        if (onExport != null) {
          IconButton(onClick = onExport) {
            Icon(
              Icons.Rounded.Download,
              contentDescription = "Export ${stem.label} as WAV",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
      WaveformBars(
        peaks = stem.peaks,
        color = color,
        progress = progress,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp, end = 6.dp)
          .height(26.dp)
          .alpha(contentAlpha),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(end = 6.dp),
      ) {
        Slider(
          value = stem.gain,
          onValueChange = onGainChange,
          valueRange = 0f..MAX_STEM_GAIN,
          colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "$percent%",
          style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
          fontWeight = FontWeight.SemiBold,
          color = if (audible) color else MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End,
          modifier = Modifier.widthIn(min = 44.dp),
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Pipeline Steps & Error Cards
// ---------------------------------------------------------------------------------------------

enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

@Immutable
data class PipelineStep(
  val title: String,
  val status: StepStatus = StepStatus.PENDING,
  val progress: Float? = null,
  val detail: String? = null,
)

fun List<PipelineStep>.update(index: Int, transform: (PipelineStep) -> PipelineStep): List<PipelineStep> =
  mapIndexed { i, step -> if (i == index) transform(step) else step }

@Composable
fun PipelineCard(
  steps: List<PipelineStep>,
  modifier: Modifier = Modifier,
  title: String = "Processing",
  onCancel: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
      }
      steps.forEach { step -> PipelineStepRow(step) }
    }
  }
}

@Composable
private fun PipelineStepRow(step: PipelineStep) {
  Row(verticalAlignment = Alignment.Top) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      when (step.status) {
        StepStatus.PENDING ->
          Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
        StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
        StepStatus.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        StepStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
      }
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(
        step.title,
        style = MaterialTheme.typography.bodyLarge,
        color =
          if (step.status == StepStatus.PENDING) MaterialTheme.colorScheme.onSurfaceVariant
          else MaterialTheme.colorScheme.onSurface,
      )
      step.detail?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (step.status == StepStatus.RUNNING) {
        val progress = step.progress
        if (progress != null) {
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
        } else {
          LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
      }
    }
  }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.errorContainer,
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
      Spacer(Modifier.width(12.dp))
      Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f),
      )
      if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Custom Canvas Glyphs
// ---------------------------------------------------------------------------------------------

/** Framed audio waveform glyph. */
@Composable
fun AudioStageGlyph(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension
    drawRoundRect(
      color = color,
      topLeft = Offset(s * 0.08f, s * 0.16f),
      size = Size(s * 0.84f, s * 0.68f),
      cornerRadius = CornerRadius(s * 0.14f),
      style = Stroke(width = s * 0.085f),
    )
    val bars = floatArrayOf(0.22f, 0.42f, 0.30f, 0.48f, 0.26f)
    val startX = s * 0.25f
    val stepX = s * 0.125f
    for (i in bars.indices) {
      val x = startX + i * stepX
      val halfH = s * bars[i] * 0.5f
      drawLine(
        color = color,
        start = Offset(x, s * 0.5f - halfH),
        end = Offset(x, s * 0.5f + halfH),
        strokeWidth = s * 0.075f,
        cap = StrokeCap.Round,
      )
    }
  }
}

@Composable
fun SparkleGlyph(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension
    fun star(cx: Float, cy: Float, r: Float) = Path().apply {
      moveTo(cx, cy - r)
      quadraticTo(cx, cy, cx + r, cy)
      quadraticTo(cx, cy, cx, cy + r)
      quadraticTo(cx, cy, cx - r, cy)
      quadraticTo(cx, cy, cx, cy - r)
      close()
    }
    drawPath(star(s * 0.42f, s * 0.56f, s * 0.42f), color)
    drawPath(star(s * 0.82f, s * 0.2f, s * 0.18f), color)
  }
}

@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension
    drawCircle(color, radius = s / 2)
    val tick = Path().apply {
      moveTo(s * 0.28f, s * 0.52f)
      lineTo(s * 0.44f, s * 0.67f)
      lineTo(s * 0.73f, s * 0.36f)
    }
    drawPath(
      tick,
      Color.Black.copy(alpha = 0.75f),
      style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
private fun rememberElapsedRealtime(): Long {
  val now by produceState(initialValue = SystemClock.elapsedRealtime()) {
    while (true) {
      delay(100)
      value = SystemClock.elapsedRealtime()
    }
  }
  return now
}
