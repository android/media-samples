/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.ai.samples.imagenediting.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.android.ai.samples.imagenediting.R
import kotlin.math.min

@Composable
fun ImagenEditingMaskEditor(sourceBitmap: Bitmap, onMaskFinalized: (Bitmap) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            // TODO #2 - Implement Drag logic
                            onDragStart = { startOffset ->
                            },
                            onDrag = { change, _ ->
                            },
                            onDragEnd = {
                            },
                        )
                    },
            ) {
                Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.editing_image_to_mask),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val bitmapWidth = sourceBitmap.width.toFloat()
                    val bitmapHeight = sourceBitmap.height.toFloat()
                    scale = min(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
                    offsetX = (canvasWidth - bitmapWidth * scale) / 2
                    offsetY = (canvasHeight - bitmapHeight * scale) / 2
                    withTransform(
                        {
                            translate(left = offsetX, top = offsetY)
                            scale(scale, scale, pivot = Offset.Zero)
                        },
                    ) {
                        val strokeWidth = 70f / scale
                        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val pathColor = Color.White.copy(alpha = 0.5f)
                        paths.forEach { path ->
                            drawPath(path = path, color = pathColor, style = stroke)
                        }
                        currentPath?.let { path ->
                            drawPath(path = path, color = pathColor, style = stroke)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomEnd)
                        .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cancel_masking),
                        modifier = Modifier
                            .padding(10.dp)
                            .clickable(true) {
                                onCancel()
                            },
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.undo_the_mask),
                        modifier = Modifier
                            .padding(10.dp)
                            .clickable(true) {
                                if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex)
                            },
                    )
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.save_the_mask),
                        modifier = Modifier
                            .padding(10.dp)
                            .clickable(true) {
                                val maskBitmap = createBitmap(sourceBitmap.width, sourceBitmap.height)
                                val canvas = android.graphics.Canvas(maskBitmap)
                                val paint = Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    strokeWidth = 70f
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    isAntiAlias = true
                                }
                                paths.forEach { path -> canvas.drawPath(path.asAndroidPath(), paint) }
                                onMaskFinalized(maskBitmap)
                            },
                    )
                }
            }
        }
    }
}
