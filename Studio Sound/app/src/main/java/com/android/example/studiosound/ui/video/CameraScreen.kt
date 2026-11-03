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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.example.studiosound.ui.components.formatDuration

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@Composable
fun CameraScreen(
  onBack: () -> Unit,
  onRecordingFinished: (fileName: String) -> Unit,
  viewModel: CameraViewModel = viewModel(),
) {
  val context = LocalContext.current
  fun allGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }
  var granted by remember { mutableStateOf(allGranted()) }
  var asked by rememberSaveable { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    granted = allGranted()
    asked = true
  }
  LaunchedEffect(Unit) { if (!granted && !asked) launcher.launch(REQUIRED_PERMISSIONS) }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    if (granted) {
      CameraContent(viewModel, onRecordingFinished)
    } else {
      PermissionRationale(
        permanentlyDenied = asked,
        onRequest = { launcher.launch(REQUIRED_PERMISSIONS) },
        onOpenSettings = {
          context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
          )
        },
      )
    }
    IconButton(
      onClick = onBack,
      colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
      modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
    }
  }
}

@Composable
private fun CameraContent(viewModel: CameraViewModel, onRecordingFinished: (String) -> Unit) {
  val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnFinished by rememberUpdatedState(onRecordingFinished)

  LaunchedEffect(lifecycleOwner) { viewModel.bindToCamera(lifecycleOwner) }
  LaunchedEffect(viewModel) { viewModel.finishedRecordings.collect { currentOnFinished(it) } }

  // Rotating mid-recording would rebind the camera and cut the recording short.
  val activity = LocalContext.current as? Activity
  DisposableEffect(state.isRecording) {
    activity?.requestedOrientation =
      if (state.isRecording) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
  }

  Box(Modifier.fillMaxSize()) {
    surfaceRequest?.let { request ->
      CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
    }

    Column(
      modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AnimatedVisibility(visible = state.isRecording, enter = fadeIn(), exit = fadeOut()) {
        Row(
          modifier = Modifier.background(Color(0xFFE53935), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.size(8.dp).background(Color.White, CircleShape))
          Spacer(Modifier.width(8.dp))
          Text(formatDuration(state.elapsedMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    Column(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val hint = state.error ?: when {
        state.isFinalizing -> "Saving recording…"
        state.isRecording -> "Tap to stop and apply Studio Sound"
        else -> "Record yourself talking somewhere noisy, then hear the difference"
      }
      Text(
        hint,
        color = if (state.error != null) Color(0xFFFFB4AB) else Color.White,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
          .padding(horizontal = 14.dp, vertical = 8.dp),
      )
      Spacer(Modifier.height(20.dp))
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(Modifier.size(56.dp))
        RecordButton(
          isRecording = state.isRecording,
          isBusy = state.isFinalizing,
          onClick = viewModel::toggleRecording,
        )
        IconButton(
          onClick = viewModel::flipCamera,
          enabled = state.canFlip && !state.isRecording,
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
            disabledContainerColor = Color.Black.copy(alpha = 0.2f),
            disabledContentColor = Color.White.copy(alpha = 0.3f),
          ),
          modifier = Modifier.size(56.dp),
        ) {
          Icon(Icons.Rounded.Cameraswitch, contentDescription = "Switch camera")
        }
      }
    }
  }
}

@Composable
private fun RecordButton(isRecording: Boolean, isBusy: Boolean, onClick: () -> Unit) {
  val innerSize by animateDpAsState(if (isRecording) 30.dp else 64.dp, label = "recordInner")
  val corner by animateDpAsState(if (isRecording) 8.dp else 32.dp, label = "recordCorner")
  Box(
    modifier = Modifier.size(84.dp).border(4.dp, Color.White, CircleShape).clip(CircleShape)
      .clickable(enabled = !isBusy, onClickLabel = if (isRecording) "Stop recording" else "Start recording", onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (isBusy) {
      CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
    } else {
      Box(Modifier.size(innerSize).clip(RoundedCornerShape(corner)).background(Color(0xFFE53935)))
    }
  }
}

@Composable
private fun PermissionRationale(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(16.dp))
    Text(
      "Camera & microphone access",
      color = Color.White,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "Recording a video with sound needs both permissions. Your recording stays on this device.",
      color = Color.White.copy(alpha = 0.8f),
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRequest) { Text("Grant access") }
    if (permanentlyDenied) {
      TextButton(onClick = onOpenSettings) { Text("Open settings", color = Color.White) }
    }
  }
}
