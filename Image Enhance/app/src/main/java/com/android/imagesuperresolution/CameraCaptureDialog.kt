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
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private const val TAG = "CameraCaptureDialog"

/**
 * Full-screen CameraX viewfinder dialog that captures a photo to a temporary file and hands it back
 * via [onPhotoCaptured] for on-device enhancement.
 */
@Composable
fun CameraCaptureDialog(
    onDismiss: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(lensFacing) {
        errorMessage = null
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider
            val desiredSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val selector = if (provider.hasCamera(desiredSelector)) {
                desiredSelector
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request -> surfaceRequest = request }
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCapture = capture
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind CameraX use cases", e)
            errorMessage = "Could not start camera: ${e.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    Dialog(
        onDismissRequest = { if (!isCapturing) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val currentRequest = surfaceRequest
            if (currentRequest != null) {
                CameraXViewfinder(
                    surfaceRequest = currentRequest,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (errorMessage == null) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isCapturing,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White,
                        ),
                    ) {
                        CloseGlyph(color = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Text(
                        text = "Take photo to enhance",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        enabled = !isCapturing,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White,
                        ),
                    ) {
                        FlipCameraGlyph(color = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    ShutterButton(
                        isCapturing = isCapturing,
                        enabled = imageCapture != null && !isCapturing,
                        onClick = {
                            val capture = imageCapture ?: return@ShutterButton
                            isCapturing = true
                            errorMessage = null
                            takePhotoToFile(
                                context = context,
                                imageCapture = capture,
                                mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
                                onSaved = { file ->
                                    isCapturing = false
                                    onPhotoCaptured(file)
                                },
                                onError = { message ->
                                    isCapturing = false
                                    errorMessage = message
                                },
                            )
                        },
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

private fun takePhotoToFile(
    context: Context,
    imageCapture: ImageCapture,
    mirrorHorizontally: Boolean,
    onSaved: (File) -> Unit,
    onError: (String) -> Unit,
) {
    val file = File(context.cacheDir, "captured_${System.currentTimeMillis()}.jpg")
    val metadata = ImageCapture.Metadata().apply {
        isReversedHorizontal = mirrorHorizontally
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
        .setMetadata(metadata)
        .build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exception)
                file.delete()
                onError("Capture failed: ${exception.message}")
            }
        },
    )
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
internal fun CameraGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(s * 0.16f, s * 0.30f)
            lineTo(s * 0.30f, s * 0.30f)
            lineTo(s * 0.37f, s * 0.18f)
            lineTo(s * 0.63f, s * 0.18f)
            lineTo(s * 0.70f, s * 0.30f)
            lineTo(s * 0.84f, s * 0.30f)
            lineTo(s * 0.88f, s * 0.34f)
            lineTo(s * 0.88f, s * 0.78f)
            lineTo(s * 0.84f, s * 0.82f)
            lineTo(s * 0.16f, s * 0.82f)
            lineTo(s * 0.12f, s * 0.78f)
            lineTo(s * 0.12f, s * 0.34f)
            close()
        }
        drawPath(body, color, style = stroke)
        drawCircle(
            color = color,
            radius = s * 0.18f,
            center = Offset(s * 0.50f, s * 0.56f),
            style = stroke,
        )
    }
}

@Composable
private fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.22f, s * 0.22f), Offset(s * 0.78f, s * 0.78f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(s * 0.78f, s * 0.22f), Offset(s * 0.22f, s * 0.78f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun FlipCameraGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val r = s * 0.36f
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)
        drawArc(color, startAngle = 200f, sweepAngle = 130f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(color, startAngle = 20f, sweepAngle = 130f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        val topArrow = Path().apply {
            moveTo(s * 0.68f, s * 0.16f)
            lineTo(s * 0.82f, s * 0.30f)
            lineTo(s * 0.64f, s * 0.34f)
        }
        val bottomArrow = Path().apply {
            moveTo(s * 0.32f, s * 0.84f)
            lineTo(s * 0.18f, s * 0.70f)
            lineTo(s * 0.36f, s * 0.66f)
        }
        drawPath(topArrow, color, style = stroke)
        drawPath(bottomArrow, color, style = stroke)
    }
}
