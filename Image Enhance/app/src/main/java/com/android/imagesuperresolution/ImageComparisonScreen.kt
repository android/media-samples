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

import android.content.res.Resources
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long a finger has to rest on the photo before it shows the whole original. */
private const val PEEK_DELAY_MS = 250L

/** Windows at least this big in both directions (unfolded foldables, tablets) get the large layouts. */
private val LargeScreenMinSize = 600.dp

/** Space kept clear around the fold of a half-opened foldable. */
private val FoldGap = 24.dp

private val ReadyGreen = Color(0xFF6DD58C)
private val BusyAmber = Color(0xFFF6C344)
private val ProblemRed = Color(0xFFFF8A80)

/**
 * Single-photo editor: one stage that shows the photo (with a before/after slider once it has been
 * enhanced) and a control panel with the effects and the Enhance button underneath, or beside it
 * on wide screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageComparisonScreen(
    devicePosture: DevicePosture,
    enhancementViewModel: EnhancementViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by enhancementViewModel.uiState.collectAsStateWithLifecycle()
    // Not read here: only the photo (while drawing) and the slider row read it, so a slider drag
    // doesn't recompose this whole screen.
    val strengthState = enhancementViewModel.strength.collectAsStateWithLifecycle()
    val readStrength = { strengthState.value }
    var showPhotoSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> uri?.let { enhancementViewModel.onImageSelected(it) } }
    )
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) showCamera = true }
    )
    val pickFromGallery = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val openCamera = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            showCamera = true
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    val openPhotoSwitcher = { showPhotoSheet = true }

    if (showCamera) {
        CameraCaptureDialog(
            onDismiss = { showCamera = false },
            onPhotoCaptured = { file ->
                showCamera = false
                enhancementViewModel.onCameraPhotoCaptured(file)
            }
        )
    }

    if (showStatusDialog) {
        ModuleStatusDialog(
            uiState = uiState,
            onDismiss = { showStatusDialog = false },
            onRetry = enhancementViewModel::retryModuleSetup,
            onSetIgnoreAllowlist = enhancementViewModel::setIgnoreDeviceAllowlist
        )
    }

    if (showPhotoSheet) {
        PhotoPickerBottomSheet(
            selectedSample = uiState.selectedSample,
            onDismiss = { showPhotoSheet = false },
            onPickFromGallery = {
                showPhotoSheet = false
                pickFromGallery()
            },
            onSelectSample = { sample ->
                showPhotoSheet = false
                enhancementViewModel.onSampleSelected(sample)
            }
        )
    }

    // The app is always edge-to-edge (mandatory when targeting Android 15+); keep the content
    // clear of the system bars and display cutouts, including in landscape.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Unfolded foldables and tablets get layouts made for the bigger canvas. Phones, the
            // Fold's cover screen and split-screen windows keep the compact layouts below.
            val largeScreen = maxWidth >= LargeScreenMinSize && maxHeight >= LargeScreenMinSize
            val panelWidth = (maxWidth * 0.36f).coerceIn(320.dp, 400.dp)
            val topBar: @Composable () -> Unit = {
                TopBar(
                    uiState = uiState,
                    onStatusClick = { showStatusDialog = true },
                    onPickPhoto = openPhotoSwitcher,
                    onOpenCamera = openCamera
                )
            }
            val photoStage: @Composable (Modifier) -> Unit = { stageModifier ->
                PhotoStage(
                    uiState = uiState,
                    strength = readStrength,
                    onPickFromGallery = pickFromGallery,
                    onSelectSample = enhancementViewModel::onSampleSelected,
                    large = true,
                    modifier = stageModifier
                )
            }
            when {
                largeScreen && devicePosture == DevicePosture.TABLETOP_MODE -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Half-opened with a horizontal fold: the photo on the upper half and the
                    // controls on the lower half. Equal halves put the gap right on the fold.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            )
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    ) {
                        topBar()
                        Spacer(modifier = Modifier.height(8.dp))
                        photoStage(Modifier.weight(1f).fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(FoldGap))
                    TabletopControls(
                        uiState = uiState,
                        viewModel = enhancementViewModel,
                        onPickPhoto = openPhotoSwitcher,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            )
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    )
                }

                largeScreen && devicePosture == DevicePosture.BOOK_MODE -> Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Half-opened with a vertical fold: the photo on the left page and the controls
                    // on the right page. Equal pages put the gap right on the fold.
                    var topBarHeight by remember { mutableIntStateOf(0) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                            )
                            .padding(start = 20.dp, top = 12.dp, bottom = 16.dp)
                    ) {
                        Box(modifier = Modifier.onSizeChanged { topBarHeight = it.height }) { topBar() }
                        Spacer(modifier = Modifier.height(12.dp))
                        photoStage(Modifier.weight(1f).fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.width(FoldGap))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
                            )
                            .padding(end = 20.dp, top = 12.dp, bottom = 16.dp)
                    ) {
                        // Line the panel up with the photo on the other page.
                        Spacer(
                            modifier = Modifier.height(
                                with(LocalDensity.current) { topBarHeight.toDp() } + 12.dp
                            )
                        )
                        InspectorPanel(
                            uiState = uiState,
                            viewModel = enhancementViewModel,
                            onPickPhoto = openPhotoSwitcher,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }

                largeScreen && maxWidth >= maxHeight * 0.8f -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    // Flat foldable or tablet: a full-width header, the photo as large as possible
                    // and a side panel whose main action stays pinned to the bottom.
                    topBar()
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        photoStage(Modifier.weight(1f).fillMaxHeight())
                        InspectorPanel(
                            uiState = uiState,
                            viewModel = enhancementViewModel,
                            onPickPhoto = openPhotoSwitcher,
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight()
                        )
                    }
                }

                else -> CompactLayouts(
                    devicePosture = devicePosture,
                    uiState = uiState,
                    strength = readStrength,
                    viewModel = enhancementViewModel,
                    paddingValues = paddingValues,
                    topBar = topBar,
                    onPickFromGallery = pickFromGallery,
                    onPickPhoto = openPhotoSwitcher
                )
            }
        }
    }
}

/** The phone layouts: photo above the controls in portrait, side by side in landscape. */
@Composable
private fun CompactLayouts(
    devicePosture: DevicePosture,
    uiState: UiState,
    strength: () -> Float,
    viewModel: EnhancementViewModel,
    paddingValues: PaddingValues,
    topBar: @Composable () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickPhoto: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Photo and controls side by side on landscape phones, tablets and open foldables.
        val wide = maxWidth > maxHeight || devicePosture == DevicePosture.BOOK_MODE
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    topBar()
                    Spacer(modifier = Modifier.height(12.dp))
                    PhotoStage(
                        uiState = uiState,
                        strength = strength,
                        onPickFromGallery = onPickFromGallery,
                        onSelectSample = viewModel::onSampleSelected,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                ControlPanel(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPickPhoto = onPickPhoto,
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                topBar()
                Spacer(modifier = Modifier.height(12.dp))
                PhotoStage(
                    uiState = uiState,
                    strength = strength,
                    onPickFromGallery = onPickFromGallery,
                    onSelectSample = viewModel::onSampleSelected,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                ControlPanel(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPickPhoto = onPickPhoto,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun TopBar(
    uiState: UiState,
    onStatusClick: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enhance",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "On-device AI · Google Play services",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ModuleStatusPill(uiState.moduleState, uiState.ignoreDeviceAllowlist, onStatusClick)
        if (uiState.originalImage != null) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onPickPhoto, enabled = !uiState.isEnhancing) {
                PhotoGlyph(
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onOpenCamera, enabled = !uiState.isEnhancing) {
            CameraGlyph(
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ModuleStatusPill(state: ModuleState, allowlistIgnored: Boolean, onClick: () -> Unit) {
    val (dot, text) = when (state) {
        ModuleState.Checking -> MaterialTheme.colorScheme.outline to "Checking"
        ModuleState.Unsupported -> ProblemRed to "Not supported"
        is ModuleState.Installing -> BusyAmber to "Downloading ${state.progress}%"
        ModuleState.Ready -> ReadyGreen to if (allowlistIgnored) "Ready · override" else "Ready"
        is ModuleState.Failed -> ProblemRed to "Setup failed · Retry"
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dot, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModuleStatusDialog(
    uiState: UiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSetIgnoreAllowlist: (Boolean) -> Unit
) {
    val state = uiState.moduleState
    val allowlistIgnored = uiState.ignoreDeviceAllowlist
    val (dotColor, title, description) = when {
        state == ModuleState.Ready && !allowlistIgnored -> Triple(
            ReadyGreen,
            "Module Ready",
            "This device passed Google Play services' device allowlist check (isDeviceSupported() == true) and the on-demand MediaEffectEnhancement AI module is installed and ready.\n\nAll three photo effects (Tonemap, Deblur & DeNoise, and 2× Upscale) run locally on the device's NPU/GPU through Play services."
        )
        state == ModuleState.Ready && allowlistIgnored -> Triple(
            BusyAmber,
            "Module Installed (Allowlist Override)",
            "The client SDK allowlist in play-services-media-effect-enhancement (16.0.0-beta09) does not yet list ${android.os.Build.MODEL} (${android.os.Build.DEVICE}), so isDeviceSupported() returned false.\n\nThe optional module APK is installed on the device via the debug override, but Google Play services may still refuse to create a session on this device."
        )
        state == ModuleState.Unsupported -> Triple(
            ProblemRed,
            "Device Not Supported",
            "EnhancementClient.isDeviceSupported() returned false for ${android.os.Build.MODEL} (${android.os.Build.DEVICE}).\n\nIn version 16.0.0-beta09 of play-services-media-effect-enhancement, this feature is limited to supported Pixel 8, Pixel 9, and Pixel 10 models."
        )
        state is ModuleState.Installing -> Triple(
            BusyAmber,
            "Downloading AI Module (${state.progress}%)",
            "Google Play services is downloading the on-demand MediaEffectEnhancement model package for the first time. Once complete, enhancements run completely offline on the device."
        )
        state is ModuleState.Failed -> Triple(
            ProblemRed,
            "Module Setup Failed",
            state.message
        )
        else -> Triple(
            MaterialTheme.colorScheme.outline,
            "Checking Device Support…",
            "Querying Google Play services for device eligibility and module installation status."
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(dotColor, CircleShape)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagnosticRow("Device", "${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
                        DiagnosticRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                        DiagnosticRow("Client SDK", "16.0.0-beta09")
                        DiagnosticRow(
                            "Allowlist",
                            when {
                                state == ModuleState.Ready && !allowlistIgnored -> "Supported"
                                allowlistIgnored -> "Overridden (debug)"
                                state == ModuleState.Unsupported -> "Not on allowlist"
                                else -> "Checking"
                            }
                        )
                    }
                }

                if (uiState.canIgnoreDeviceAllowlist && (state == ModuleState.Unsupported || allowlistIgnored)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .toggleable(
                                value = allowlistIgnored,
                                enabled = !uiState.isEnhancing,
                                role = Role.Switch,
                                onValueChange = onSetIgnoreAllowlist
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bypass client allowlist",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Debug builds only · installs module directly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowlistIgnored,
                            onCheckedChange = null,
                            enabled = !uiState.isEnhancing
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry, enabled = !uiState.isEnhancing) {
                Text("Re-check")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Photo stage
// ---------------------------------------------------------------------------------------------

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

@Composable
private fun PhotoStage(
    uiState: UiState,
    /** Live strength blend, read only while drawing the before/after comparison. */
    strength: () -> Float,
    onPickFromGallery: () -> Unit,
    onSelectSample: (SampleImage) -> Unit,
    modifier: Modifier = Modifier,
    /** Large layouts show the samples as a wrapping grid instead of a scrolling strip. */
    large: Boolean = false
) {
    val original = uiState.originalImage
    val enhanced = uiState.enhancedImage
    val progress = uiState.progress

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        when {
            original == null && uiState.isLoadingImage -> CircularProgressIndicator()

            original == null -> EmptyStage(
                onPickFromGallery = onPickFromGallery,
                onSelectSample = onSelectSample,
                large = large
            )

            else -> {
                // Keyed on original.bitmap so zooming into a region before or after enhancing
                // preserves the exact zoom scale and pan offset across both views.
                var zoomScale by remember(original.bitmap) { mutableFloatStateOf(1f) }
                var zoomPan by remember(original.bitmap) { mutableStateOf(Offset.Zero) }
                val onTransform: (Float, Offset) -> Unit = { newScale, newPan ->
                    zoomScale = newScale
                    zoomPan = newPan
                }
                // Zoom and pan change on every frame of a gesture, so they are handed down as
                // lambdas that are only read while drawing: a pinch redraws the photo instead of
                // recomposing the stage on every frame.
                val readScale = { zoomScale }
                val readPan = { zoomPan }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StageHeaderBar(
                        original = original,
                        enhanced = enhanced,
                        sampleTitle = uiState.selectedSample?.title,
                        isEnhancing = progress != null,
                        zoomScale = readScale,
                        pan = readPan,
                        onResetZoom = {
                            zoomScale = 1f
                            zoomPan = Offset.Zero
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        if (enhanced != null && progress == null) {
                            BeforeAfterSlider(
                                original = original,
                                enhanced = enhanced,
                                strength = strength,
                                scale = readScale,
                                pan = readPan,
                                onTransform = onTransform,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val preview = progress?.previewBitmap ?: (enhanced ?: original).bitmap
                            ZoomablePhoto(
                                bitmap = preview,
                                scale = readScale,
                                pan = readPan,
                                onTransform = onTransform,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (progress != null) EnhancingOverlay(progress)
                        }
                    }
                }
            }
        }

        val error = uiState.imageError ?: uiState.enhancementError
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun StageHeaderBar(
    original: ImageInfo,
    enhanced: ImageInfo?,
    sampleTitle: String?,
    isEnhancing: Boolean,
    zoomScale: () -> Float,
    pan: () -> Offset,
    onResetZoom: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val title = when {
        enhanced != null && (original.width != enhanced.width || original.height != enhanced.height) ->
            "${original.width}×${original.height} → ${enhanced.width}×${enhanced.height}"
        enhanced != null -> "${enhanced.width}×${enhanced.height}"
        else -> "${sampleTitle ?: "Original"} · ${original.width}×${original.height}"
    }
    // Derived, so a gesture only recomposes the header when the chip appears, disappears or its
    // text changes, not on every frame.
    val resetLabel by remember(zoomScale, pan) {
        derivedStateOf {
            val scale = zoomScale()
            when {
                scale > 1.02f -> String.format(Locale.getDefault(), "%.1f× ✕", scale)
                pan().getDistance() > 4f -> "Center ✕"
                else -> null
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        resetLabel?.let { label ->
            Surface(
                shape = CircleShape,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onResetZoom)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (enhanced != null && enhanced.passes > 1) {
            Surface(
                shape = CircleShape,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ) {
                Text(
                    text = "Pass ${enhanced.passes}×",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        val badgeText = when {
            isEnhancing -> null
            enhanced?.latency != null -> formatDuration(enhanced.latency)
            else -> formatMemorySize(original.byteCount)
        }
        if (badgeText != null) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    bitmap: android.graphics.Bitmap,
    scale: () -> Float,
    pan: () -> Offset,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    val currentScale by rememberUpdatedState(scale)
    val currentPan by rememberUpdatedState(pan)
    val currentOnTransform by rememberUpdatedState(onTransform)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Selected photo" }
            .pointerInput(imageAspect) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        val zoom = event.calculateZoom()
                        val panDelta = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (zoom != 1f || panDelta != Offset.Zero) {
                            applyZoomAndPan(
                                currentScale = currentScale(),
                                currentPan = currentPan(),
                                zoom = zoom,
                                panDelta = panDelta,
                                centroid = centroid,
                                width = width,
                                height = height,
                                imageAspect = imageAspect,
                                onTransform = currentOnTransform
                            )
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
    ) {
        // Read in the draw phase, so zooming and panning only redraw.
        drawCroppedPhoto(
            bitmap = imageBitmap,
            scale = currentScale(),
            pan = currentPan()
        )
    }
}

@Composable
private fun EmptyStage(
    onPickFromGallery: () -> Unit,
    onSelectSample: (SampleImage) -> Unit,
    large: Boolean = false
) {
    if (large) {
        LargeEmptyStage(onPickFromGallery = onPickFromGallery, onSelectSample = onSelectSample)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onPickFromGallery)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PhotoGlyph(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Choose a photo to enhance",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pick from your gallery, or tap a sample below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SampleThumbnailRow(
            selectedSample = null,
            onSelectSample = onSelectSample
        )
    }
}

/**
 * The empty stage of the large layouts: every sample visible at once in a grid that grows with the
 * stage. Short stages (tabletop posture) drop the big icon, and it scrolls rather than clips.
 */
@Composable
private fun LargeEmptyStage(
    onPickFromGallery: () -> Unit,
    onSelectSample: (SampleImage) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val short = maxHeight < 440.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = if (short) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onPickFromGallery)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!short) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoGlyph(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = "Choose a photo to enhance",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pick from your gallery, or tap a sample below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 400.dp)
                )
            }
            Spacer(modifier = Modifier.height(if (short) 12.dp else 24.dp))
            SampleThumbnailGrid(
                onSelectSample = onSelectSample,
                maxThumbnail = if (short) 96.dp else 128.dp
            )
        }
    }
}

/**
 * All samples in a centered grid, balanced across rows (3 + 2 rather than 4 + 1), with thumbnails
 * sized to fill the width up to [maxThumbnail].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SampleThumbnailGrid(
    onSelectSample: (SampleImage) -> Unit,
    maxThumbnail: Dp,
    modifier: Modifier = Modifier
) {
    val samples = SampleImage.entries
    val gap = 14.dp
    val minThumbnail = 96.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        val fitPerRow = ((maxWidth + gap) / (minThumbnail + gap)).toInt().coerceIn(1, samples.size)
        val rows = (samples.size + fitPerRow - 1) / fitPerRow
        val perRow = (samples.size + rows - 1) / rows
        // 1dp of slack so pixel rounding can never push the last item of a row onto the next one.
        val thumbnail = ((maxWidth - gap * (perRow - 1)) / perRow - 1.dp).coerceAtMost(maxThumbnail)
        val shape = RoundedCornerShape(18.dp)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = perRow
        ) {
            samples.forEach { sample ->
                Column(
                    modifier = Modifier
                        .width(thumbnail)
                        .clip(shape)
                        .clickable { onSelectSample(sample) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SampleThumbnail(
                        sample = sample,
                        modifier = Modifier
                            .size(thumbnail)
                            .clip(shape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = sample.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleThumbnailRow(
    selectedSample: SampleImage?,
    onSelectSample: (SampleImage) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SampleImage.entries) { sample ->
            val isSelected = sample == selectedSample
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clip(shape)
                    .clickable { onSelectSample(sample) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SampleThumbnail(
                    sample = sample,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(shape)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, colors.primary, shape)
                            } else {
                                Modifier
                            }
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sample.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/** Shortest edge sample thumbnails are decoded at: enough for the largest one (128dp) at ~2.5x density. */
private const val THUMBNAIL_SHORT_EDGE_PX = 320

/**
 * Sample thumbnails, decoded off the main thread once and kept for the life of the process (a few
 * hundred KB each). painterResource() used to decode the full-size photos on the main thread every
 * time the samples were shown, about 100 ms of frozen UI on a Pixel 9 Pro Fold.
 */
private object SampleThumbnailCache {
    private val thumbnails = ConcurrentHashMap<SampleImage, ImageBitmap>()

    fun peek(sample: SampleImage): ImageBitmap? = thumbnails[sample]

    suspend fun load(resources: Resources, sample: SampleImage): ImageBitmap =
        thumbnails[sample] ?: withContext(Dispatchers.IO) {
            resources.decodeThumbnail(sample.resId, THUMBNAIL_SHORT_EDGE_PX)
                .also { it.prepareToDraw() }
                .asImageBitmap()
        }.also { thumbnails[sample] = it }
}

/** A sample's thumbnail, with a placeholder for the moment it takes to decode the first time. */
@Composable
private fun SampleThumbnail(sample: SampleImage, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val thumbnail by produceState(initialValue = SampleThumbnailCache.peek(sample), sample) {
        if (value == null) {
            value = try {
                SampleThumbnailCache.load(resources, sample)
            } catch (e: IOException) {
                null
            }
        }
    }
    val image = thumbnail
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = sample.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoPickerBottomSheet(
    selectedSample: SampleImage?,
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSelectSample: (SampleImage) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sample photos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            SampleThumbnailRow(
                selectedSample = selectedSample,
                onSelectSample = onSelectSample
            )
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 20.dp)
            ) {
                PhotoGlyph(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Browse device gallery",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun GlassPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** Dims the photo, sweeps a scan line over it and shows what is running and for how long. */
@Composable
private fun EnhancingOverlay(progress: EnhancementProgress) {
    val accent = MaterialTheme.colorScheme.primary
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "scanPosition"
    )
    val now = rememberElapsedRealtime()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.4f))
            val y = size.height * scan
            val band = size.height * 0.15f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent),
                    startY = y - band,
                    endY = y + band
                ),
                topLeft = Offset(0f, y - band),
                size = Size(size.width, band * 2)
            )
            drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }

        val steps = progress.steps
        val runningIndex = steps.indexOfFirst { it.startedAt != null && it.latency == null }
        val title = when {
            runningIndex < 0 -> "Preparing…"
            steps.size == 1 -> "Applying ${steps.single().label}"
            else -> "Step ${runningIndex + 1} of ${steps.size} · ${steps[runningIndex].label}"
        }
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(now - progress.startedAt),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * One view of the photo: the original left of a draggable divider and the enhanced result right of
 * it, alpha-blended over the original by [strength] and transformed together by [scale] and [pan].
 *
 * - Two fingers: pinch to zoom and pan both Before and After simultaneously.
 * - One finger on the divider line/handle: drag the Before/After split divider.
 * - One finger anywhere else on the photo: drag/pan both images together across the full photo.
 * - Hold one finger still: peek at the whole original at the current zoom & pan.
 */
@Composable
private fun BeforeAfterSlider(
    original: ImageInfo,
    enhanced: ImageInfo,
    /** 0f = original, 1f = full result. Read only while drawing, so the slider just redraws. */
    strength: () -> Float,
    scale: () -> Float,
    pan: () -> Offset,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val originalBitmap = remember(original.bitmap) { original.bitmap.asImageBitmap() }
    val enhancedBitmap = remember(enhanced.bitmap) { enhanced.bitmap.asImageBitmap() }
    val imageAspect = original.width.toFloat() / original.height.toFloat().coerceAtLeast(1f)

    var split by remember(enhanced.bitmap) { mutableFloatStateOf(1f) }
    var touched by remember(enhanced.bitmap) { mutableStateOf(false) }
    var peeking by remember(enhanced.bitmap) { mutableStateOf(false) }
    var showHint by remember(enhanced.bitmap) { mutableStateOf(true) }

    val currentScale by rememberUpdatedState(scale)
    val currentPan by rememberUpdatedState(pan)
    val currentOnTransform by rememberUpdatedState(onTransform)

    // Reveal the result with a wipe from right to the middle, then hint at the gestures.
    LaunchedEffect(enhanced.bitmap) {
        delay(200)
        animate(1f, 0.5f, animationSpec = tween(900, easing = FastOutSlowInEasing)) { value, _ ->
            if (!touched) split = value
        }
        delay(3000)
        showHint = false
    }

    // The divider moves on every frame of a drag or of the reveal; only these thresholds matter
    // for composition, the position itself is read while drawing.
    val beforeLabelVisible by remember(enhanced.bitmap) { derivedStateOf { peeking || split > 0.18f } }
    val afterLabelVisible by remember(enhanced.bitmap) { derivedStateOf { !peeking && split < 0.82f } }
    Box(
        modifier = modifier
            .semantics { contentDescription = "Enhanced photo comparison" }
            .pointerInput(enhanced.bitmap, imageAspect) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touched = true
                    showHint = false
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val handleGrabRadius = 36.dp.toPx()
                    val startedNearDivider = abs(down.position.x - width * split) <= handleGrabRadius
                    val initialSplit = split

                    var dragging = false
                    var released = false
                    var multiTouch = false
                    var initialDragPosition = down.position
                    var initialDragDelta = Offset.Zero

                    withTimeoutOrNull(PEEK_DELAY_MS) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) {
                                multiTouch = true
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                released = true
                                break
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                initialDragPosition = change.position
                                initialDragDelta = change.position - down.position
                                change.consume()
                                break
                            }
                        }
                    }

                    if (!multiTouch && !dragging && !released) {
                        peeking = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) {
                                released = true
                                break
                            }
                            if (pressed > 1) {
                                multiTouch = true
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                initialDragPosition = change.position
                                initialDragDelta = change.position - down.position
                                change.consume()
                                break
                            }
                        }
                        peeking = false
                    }

                    when {
                        multiTouch -> {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val zoom = event.calculateZoom()
                                val panDelta = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (zoom != 1f || panDelta != Offset.Zero) {
                                    applyZoomAndPan(
                                        currentScale = currentScale(),
                                        currentPan = currentPan(),
                                        zoom = zoom,
                                        panDelta = panDelta,
                                        centroid = centroid,
                                        width = width,
                                        height = height,
                                        imageAspect = imageAspect,
                                        onTransform = currentOnTransform
                                    )
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }

                        dragging -> {
                            val dragVector = initialDragPosition - down.position
                            val movingDivider = startedNearDivider && abs(dragVector.x) >= abs(dragVector.y)
                            if (movingDivider) {
                                split = (initialDragPosition.x / width).coerceIn(0f, 1f)
                            } else if (initialDragDelta != Offset.Zero) {
                                currentOnTransform(
                                    currentScale(),
                                    clampPan(
                                        currentPan() + initialDragDelta,
                                        currentScale(),
                                        width,
                                        height,
                                        imageAspect
                                    )
                                )
                            }
                            var switchedToPinch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed == 0) break
                                if (pressed > 1 && !switchedToPinch) {
                                    switchedToPinch = true
                                    if (movingDivider) split = initialSplit
                                }
                                if (switchedToPinch) {
                                    val zoom = event.calculateZoom()
                                    val panDelta = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    if (zoom != 1f || panDelta != Offset.Zero) {
                                        applyZoomAndPan(
                                            currentScale = currentScale(),
                                            currentPan = currentPan(),
                                            zoom = zoom,
                                            panDelta = panDelta,
                                            centroid = centroid,
                                            width = width,
                                            height = height,
                                            imageAspect = imageAspect,
                                            onTransform = currentOnTransform
                                        )
                                    }
                                } else {
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                    if (movingDivider) {
                                        split = (change.position.x / width).coerceIn(0f, 1f)
                                    } else {
                                        val delta = change.position - change.previousPosition
                                        if (delta != Offset.Zero) {
                                            currentOnTransform(
                                                currentScale(),
                                                clampPan(
                                                    currentPan() + delta,
                                                    currentScale(),
                                                    width,
                                                    height,
                                                    imageAspect
                                                )
                                            )
                                        }
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Everything that changes frame by frame (divider, zoom, pan, strength) is read here,
            // in the draw phase, so dragging or pinching only redraws this canvas.
            val fraction = if (peeking) 1f else split
            val zoom = currentScale()
            val offset = currentPan()
            drawCroppedPhoto(
                bitmap = originalBitmap,
                scale = zoom,
                pan = offset
            )
            clipRect(left = size.width * fraction) {
                drawCroppedPhoto(
                    bitmap = enhancedBitmap,
                    scale = zoom,
                    pan = offset,
                    alpha = strength().coerceIn(0f, 1f)
                )
            }
            if (!peeking) {
                val x = size.width * fraction
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                val radius = 18.dp.toPx()
                val handleCenter = Offset(x, size.height / 2)
                drawCircle(Color.Black.copy(alpha = 0.3f), radius + 2.dp.toPx(), handleCenter)
                drawCircle(Color.White, radius, handleCenter)
                val arrow = radius * 0.32f
                val gap = radius * 0.16f
                val left = Path().apply {
                    moveTo(handleCenter.x - gap - arrow, handleCenter.y)
                    lineTo(handleCenter.x - gap, handleCenter.y - arrow)
                    lineTo(handleCenter.x - gap, handleCenter.y + arrow)
                    close()
                }
                val right = Path().apply {
                    moveTo(handleCenter.x + gap + arrow, handleCenter.y)
                    lineTo(handleCenter.x + gap, handleCenter.y - arrow)
                    lineTo(handleCenter.x + gap, handleCenter.y + arrow)
                    close()
                }
                drawPath(left, Color(0xFF202124))
                drawPath(right, Color(0xFF202124))
            }
        }

        CornerLabel(
            text = "BEFORE",
            visible = beforeLabelVisible,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        AfterCornerLabel(
            strength = strength,
            passes = enhanced.passes,
            visible = afterLabelVisible,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        AnimatedVisibility(
            visible = showHint && !peeking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 96.dp)
        ) {
            GlassPill(text = "Drag handle to compare · Drag photo to pan · Pinch to zoom")
        }
    }
}

private fun DrawScope.drawCroppedPhoto(
    bitmap: ImageBitmap,
    scale: Float,
    pan: Offset,
    alpha: Float = 1f
) {
    val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
    val imgH = bitmap.height.toFloat().coerceAtLeast(1f)
    val coverScale = max(size.width / imgW, size.height / imgH)
    val totalScale = coverScale * scale
    withTransform({
        translate(left = pan.x, top = pan.y)
        scale(scaleX = totalScale, scaleY = totalScale, pivot = center)
    }) {
        drawImage(
            image = bitmap,
            topLeft = Offset(center.x - imgW / 2f, center.y - imgH / 2f),
            alpha = alpha
        )
    }
}

private fun applyZoomAndPan(
    currentScale: Float,
    currentPan: Offset,
    zoom: Float,
    panDelta: Offset,
    centroid: Offset,
    width: Float,
    height: Float,
    imageAspect: Float,
    onTransform: (Float, Offset) -> Unit
) {
    val newScale = (currentScale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val effectiveZoom = newScale / currentScale
    val center = Offset(width / 2f, height / 2f)
    val centroidOffset = if (centroid != Offset.Unspecified) centroid - center else Offset.Zero
    val rawPan = (currentPan - centroidOffset) * effectiveZoom + centroidOffset + panDelta
    onTransform(newScale, clampPan(rawPan, newScale, width, height, imageAspect))
}

private fun clampPan(
    pan: Offset,
    scale: Float,
    width: Float,
    height: Float,
    imageAspect: Float
): Offset {
    val safeAspect = if (imageAspect.isFinite() && imageAspect > 0f) imageAspect else 1f
    val viewAspect = width / height.coerceAtLeast(1f)
    val baseWidth: Float
    val baseHeight: Float
    if (safeAspect >= viewAspect) {
        baseWidth = height * safeAspect
        baseHeight = height
    } else {
        baseWidth = width
        baseHeight = width / safeAspect
    }
    val maxX = ((baseWidth * scale - width) / 2f).coerceAtLeast(0f)
    val maxY = ((baseHeight * scale - height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = pan.x.coerceIn(-maxX, maxX),
        y = pan.y.coerceIn(-maxY, maxY)
    )
}

/**
 * The AFTER label, which shows the blend while the strength slider moves. It recomposes on its
 * own, and only when its text changes.
 */
@Composable
private fun AfterCornerLabel(
    strength: () -> Float,
    passes: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val text by remember(strength, passes) {
        derivedStateOf {
            val percent = (strength().coerceIn(0f, 1f) * 100).roundToInt()
            when {
                percent < 100 -> "AFTER · $percent%"
                passes > 1 -> "AFTER · ${passes}×"
                else -> "AFTER"
            }
        }
    }
    CornerLabel(text = text, visible = visible, modifier = modifier)
}

@Composable
private fun CornerLabel(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(10.dp)
            .alpha(if (visible) 1f else 0f)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Control panel
// ---------------------------------------------------------------------------------------------

private val EnhancementOption.description: String
    get() = when (this) {
        EnhancementOption.Tonemap -> "Balance light and color"
        EnhancementOption.DeblurDenoise -> "Sharpen and clean up noise"
        EnhancementOption.Upscale -> "Boost the resolution"
    }

private val EnhancementOption.title: String
    get() = when (this) {
        EnhancementOption.Tonemap -> "Tonemap"
        EnhancementOption.DeblurDenoise -> "Deblur"
        EnhancementOption.Upscale -> "Upscale"
    }

/** What an effect tile shows in its corner. */
private sealed interface TileStatus {
    data object Idle : TileStatus
    data object Queued : TileStatus
    data object Running : TileStatus
    data class Done(val millis: Long) : TileStatus
}

private fun UiState.tileStatus(option: EnhancementOption): TileStatus {
    val running = progress
    if (running != null) {
        val step = running.steps.firstOrNull { option in it.options } ?: return TileStatus.Idle
        return when {
            step.latency != null -> TileStatus.Done(step.latency)
            step.startedAt != null -> TileStatus.Running
            else -> TileStatus.Queued
        }
    }
    val latency = enhancedImage?.steps?.firstOrNull { option in it.options }?.latency
    return if (latency != null) TileStatus.Done(latency) else TileStatus.Idle
}

@Composable
private fun ControlPanel(
    uiState: UiState,
    viewModel: EnhancementViewModel,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(visible = uiState.enhancedImage != null && !uiState.isEnhancing) {
            StrengthSlider(
                viewModel = viewModel,
                passes = uiState.enhancedImage?.passes ?: 1
            )
        }

        EffectsHeader(uiState)

        EffectTileRow(uiState, viewModel)

        StepByStepToggle(uiState, viewModel)

        AnimatedVisibility(visible = uiState.stepByStep && uiState.orderedSelection.size > 1) {
            StepOrderRow(
                order = uiState.orderedSelection,
                enabled = !uiState.isEnhancing,
                onMove = viewModel::moveStep
            )
        }

        ModuleNotice(uiState, viewModel)

        PrimaryActions(uiState, viewModel, onPickPhoto)
    }
}

/**
 * The large-screen control panel: the effects as full-width rows with room for their
 * descriptions, and the result controls (strength and the main action) pinned to the bottom so
 * they stay in the same place whatever else is showing.
 */
@Composable
private fun InspectorPanel(
    uiState: UiState,
    viewModel: EnhancementViewModel,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EffectsHeader(uiState)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnhancementOption.entries.forEach { option ->
                        val position = uiState.orderedSelection.indexOf(option)
                        EffectRow(
                            option = option,
                            selected = option in uiState.selectedOptions,
                            status = uiState.tileStatus(option),
                            stepNumber = if (uiState.stepByStep && position >= 0) position + 1 else null,
                            enabled = !uiState.isEnhancing,
                            onClick = { viewModel.onOptionSelected(option) }
                        )
                    }
                }
                StepByStepToggle(uiState, viewModel)
                AnimatedVisibility(visible = uiState.stepByStep && uiState.orderedSelection.size > 1) {
                    StepOrderRow(
                        order = uiState.orderedSelection,
                        enabled = !uiState.isEnhancing,
                        onMove = viewModel::moveStep
                    )
                }
                ModuleNotice(uiState, viewModel)
            }
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedVisibility(visible = uiState.enhancedImage != null && !uiState.isEnhancing) {
                StrengthSlider(
                    viewModel = viewModel,
                    passes = uiState.enhancedImage?.passes ?: 1,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            PrimaryActions(uiState, viewModel, onPickPhoto, stacked = true)
        }
    }
}

/**
 * Tabletop posture (half-opened, fold across the middle): the effects on the left of the lower
 * half and the run controls on the right, so everything fits below the fold without scrolling.
 */
@Composable
private fun TabletopControls(
    uiState: UiState,
    viewModel: EnhancementViewModel,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EffectsHeader(uiState)
            EffectTileRow(uiState, viewModel)
            StepByStepToggle(uiState, viewModel)
            AnimatedVisibility(visible = uiState.stepByStep && uiState.orderedSelection.size > 1) {
                StepOrderRow(
                    order = uiState.orderedSelection,
                    enabled = !uiState.isEnhancing,
                    onMove = viewModel::moveStep
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModuleNotice(uiState, viewModel)
            }
            AnimatedVisibility(visible = uiState.enhancedImage != null && !uiState.isEnhancing) {
                StrengthSlider(
                    viewModel = viewModel,
                    passes = uiState.enhancedImage?.passes ?: 1
                )
            }
            PrimaryActions(uiState, viewModel, onPickPhoto, stacked = true)
        }
    }
}

@Composable
private fun EffectsHeader(uiState: UiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Effects",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${uiState.selectedOptions.size} of ${EnhancementOption.entries.size} selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The three effects as equal tiles side by side (phones and tabletop posture). */
@Composable
private fun EffectTileRow(uiState: UiState, viewModel: EnhancementViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EnhancementOption.entries.forEach { option ->
            val position = uiState.orderedSelection.indexOf(option)
            EffectTile(
                option = option,
                selected = option in uiState.selectedOptions,
                status = uiState.tileStatus(option),
                stepNumber = if (uiState.stepByStep && position >= 0) position + 1 else null,
                enabled = !uiState.isEnhancing,
                onClick = { viewModel.onOptionSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun StepByStepToggle(uiState: UiState, viewModel: EnhancementViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(
                value = uiState.stepByStep,
                enabled = !uiState.isEnhancing,
                role = Role.Switch,
                onValueChange = viewModel::setStepByStep
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "One effect at a time", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Runs each effect separately and times it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = uiState.stepByStep, onCheckedChange = null, enabled = !uiState.isEnhancing)
    }
}

/** The main action for the current state: choose a photo, enhance, cancel, or re-run / boost a result. */
@Composable
private fun PrimaryActions(
    uiState: UiState,
    viewModel: EnhancementViewModel,
    onPickPhoto: () -> Unit,
    /** Boost above Re-run at full width instead of side by side, for narrow side panels. */
    stacked: Boolean = false
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    when {
        uiState.isEnhancing -> OutlinedButton(onClick = viewModel::cancelEnhancement, modifier = buttonModifier) {
            Text("Cancel", style = MaterialTheme.typography.titleMedium)
        }

        uiState.originalImage == null -> Button(onClick = onPickPhoto, modifier = buttonModifier) {
            PhotoGlyph(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Choose photo", style = MaterialTheme.typography.titleMedium)
        }

        uiState.enhancedImage != null && stacked -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BoostButton(uiState, viewModel, buttonModifier)
            ReRunButton(uiState, viewModel, buttonModifier)
        }

        uiState.enhancedImage != null -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReRunButton(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
            )
            BoostButton(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
            )
        }

        else -> Button(
            onClick = viewModel::enhanceImage,
            enabled = uiState.canEnhance,
            modifier = buttonModifier
        ) {
            SparkleGlyph(
                color = if (uiState.canEnhance) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Enhance",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ReRunButton(uiState: UiState, viewModel: EnhancementViewModel, modifier: Modifier) {
    OutlinedButton(
        onClick = viewModel::enhanceImage,
        enabled = uiState.canEnhance,
        modifier = modifier
    ) {
        Text(
            text = "Re-run",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun BoostButton(uiState: UiState, viewModel: EnhancementViewModel, modifier: Modifier) {
    Button(
        onClick = viewModel::boostImage,
        enabled = uiState.canBoost,
        modifier = modifier
    ) {
        SparkleGlyph(
            color = if (uiState.canBoost) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Boost (+1 pass)",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/** One effect as a full-width row: icon, name, description and its status on the right. */
@Composable
private fun EffectRow(
    option: EnhancementOption,
    selected: Boolean,
    status: TileStatus,
    /** Position in the step-by-step order, or null when effects run together. */
    stepNumber: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (selected) colors.primary else colors.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                EffectGlyph(
                    option = option,
                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = option.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.widthIn(min = 24.dp), contentAlignment = Alignment.Center) {
                if (status == TileStatus.Idle && !selected) {
                    // An empty ring so it reads as something that can be switched on.
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, colors.outline, CircleShape)
                    )
                } else {
                    EffectStatusIndicator(status = status, selected = selected, stepNumber = stepNumber)
                }
            }
        }
    }
}

/** Running spinner, "Next", the measured time, the step number or a check mark. */
@Composable
private fun EffectStatusIndicator(status: TileStatus, selected: Boolean, stepNumber: Int?) {
    when (status) {
        TileStatus.Running -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )

        TileStatus.Queued -> Text(text = "Next", style = MaterialTheme.typography.labelSmall)

        is TileStatus.Done -> Text(
            text = formatDuration(status.millis),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        TileStatus.Idle -> when {
            selected && stepNumber != null -> StepNumberBadge(stepNumber)
            selected -> CheckGlyph(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** The strength slider. It collects [EnhancementViewModel.strength] itself, so a drag recomposes only this row. */
@Composable
private fun StrengthSlider(
    viewModel: EnhancementViewModel,
    passes: Int,
    modifier: Modifier = Modifier
) {
    val strength by viewModel.strength.collectAsStateWithLifecycle()
    StrengthControl(
        strength = strength,
        passes = passes,
        onStrengthChange = viewModel::setStrength,
        modifier = modifier
    )
}

@Composable
private fun StrengthControl(
    strength: Float,
    passes: Int,
    onStrengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = (strength.coerceIn(0f, 1f) * 100).roundToInt()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (passes > 1) "Strength (${passes}×)" else "Strength",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = strength,
                onValueChange = onStrengthChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp)
            )
        }
    }
}

@Composable
private fun EffectTile(
    option: EnhancementOption,
    selected: Boolean,
    status: TileStatus,
    /** Position in the step-by-step order, or null when effects run together. */
    stepNumber: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (selected) colors.primary else colors.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    EffectGlyph(
                        option = option,
                        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                when (status) {
                    TileStatus.Running -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )

                    TileStatus.Queued -> Text(text = "Next", style = MaterialTheme.typography.labelSmall)

                    is TileStatus.Done -> Text(
                        text = formatDuration(status.millis),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    TileStatus.Idle -> when {
                        selected && stepNumber != null -> StepNumberBadge(stepNumber)
                        selected -> CheckGlyph(color = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = option.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StepNumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The selected effects in the order step-by-step mode runs them. Drag a chip sideways to move it;
 * it swaps places with a neighbour once it has travelled more than half a slot.
 */
@Composable
private fun StepOrderRow(
    order: List<EnhancementOption>,
    enabled: Boolean,
    onMove: (EnhancementOption, Int) -> Unit
) {
    val gap = 8.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    var rowWidth by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableStateOf<EnhancementOption?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The drag handlers outlive recompositions, so they read the latest order and slot width.
    val currentOrder by rememberUpdatedState(order)
    val slotWidth = if (order.isEmpty()) 0f else (rowWidth - gapPx * (order.size - 1)) / order.size + gapPx
    val currentSlotWidth by rememberUpdatedState(slotWidth)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Order · drag to rearrange",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidth = it.width },
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            order.forEachIndexed { index, option ->
                // Keyed so a chip keeps its gesture while it moves to a new position.
                key(option) {
                    val isDragged = dragged == option
                    StepChip(
                        number = index + 1,
                        option = option,
                        lifted = isDragged,
                        modifier = Modifier
                            .weight(1f)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (isDragged) dragOffset else 0f
                                val scale = if (isDragged) 1.06f else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                            .pointerInput(option, enabled) {
                                if (!enabled) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragged = option
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val slot = currentSlotWidth
                                        val position = currentOrder.indexOf(option)
                                        if (slot > 0f && dragOffset > slot / 2 && position < currentOrder.lastIndex) {
                                            onMove(option, position + 1)
                                            dragOffset -= slot
                                        } else if (slot > 0f && dragOffset < -slot / 2 && position > 0) {
                                            onMove(option, position - 1)
                                            dragOffset += slot
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepChip(
    number: Int,
    option: EnhancementOption,
    lifted: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (lifted) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (lifted) colors.onPrimaryContainer else colors.onSurface,
        shadowElevation = if (lifted) 6.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepNumberBadge(number)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = option.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            DragHandleGlyph(color = colors.onSurfaceVariant, modifier = Modifier.size(width = 10.dp, height = 16.dp))
        }
    }
}

/** Module download progress, setup failures and the unsupported-device notice. */
@Composable
private fun ModuleNotice(uiState: UiState, viewModel: EnhancementViewModel) {
    when (val state = uiState.moduleState) {
        is ModuleState.Installing -> NoticeCard {
            Text(
                text = "Downloading the enhancement module (first run only) · ${state.progress}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        is ModuleState.Failed -> NoticeCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::retryModuleSetup) { Text("Retry") }
            }
        }

        else -> Unit
    }

    val unsupported = uiState.moduleState == ModuleState.Unsupported
    if (unsupported || uiState.ignoreDeviceAllowlist) {
        NoticeCard {
            if (unsupported) {
                Text(
                    text = "This phone isn't supported by Google's photo enhancement yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (uiState.canIgnoreDeviceAllowlist) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = uiState.ignoreDeviceAllowlist,
                            enabled = !uiState.isEnhancing,
                            role = Role.Switch,
                            onValueChange = viewModel::setIgnoreDeviceAllowlist
                        )
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Try anyway (debug builds only)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.ignoreDeviceAllowlist,
                        onCheckedChange = null,
                        enabled = !uiState.isEnhancing
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------------------------
// Glyphs (drawn in code: the app doesn't depend on an icon library)
// ---------------------------------------------------------------------------------------------

@Composable
private fun EffectGlyph(option: EnhancementOption, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (option) {
            // Contrast: a ring with one half filled.
            EnhancementOption.Tonemap -> {
                val r = s * 0.4f
                drawCircle(color, radius = r, style = stroke)
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2)
                )
            }
            // Focus: viewfinder corners around a dot.
            EnhancementOption.DeblurDenoise -> {
                val a = s * 0.08f
                val b = s * 0.92f
                val l = s * 0.26f
                val corners = Path().apply {
                    moveTo(a, a + l); lineTo(a, a); lineTo(a + l, a)
                    moveTo(b - l, a); lineTo(b, a); lineTo(b, a + l)
                    moveTo(b, b - l); lineTo(b, b); lineTo(b - l, b)
                    moveTo(a + l, b); lineTo(a, b); lineTo(a, b - l)
                }
                drawPath(corners, color, style = stroke)
                drawCircle(color, radius = s * 0.14f, center = center)
            }
            // Upscale: small frame with an arrow growing out of it.
            EnhancementOption.Upscale -> {
                drawRect(
                    color = color,
                    topLeft = Offset(s * 0.1f, s * 0.54f),
                    size = Size(s * 0.36f, s * 0.36f),
                    style = stroke
                )
                val arrow = Path().apply {
                    moveTo(s * 0.48f, s * 0.52f); lineTo(s * 0.88f, s * 0.12f)
                    moveTo(s * 0.56f, s * 0.12f); lineTo(s * 0.88f, s * 0.12f); lineTo(s * 0.88f, s * 0.44f)
                }
                drawPath(arrow, color, style = stroke)
            }
        }
    }
}

@Composable
private fun PhotoGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        drawRoundRect(
            color = color,
            topLeft = Offset(s * 0.08f, s * 0.16f),
            size = Size(s * 0.84f, s * 0.68f),
            cornerRadius = CornerRadius(s * 0.12f),
            style = Stroke(width = s * 0.09f)
        )
        val mountains = Path().apply {
            moveTo(s * 0.18f, s * 0.74f)
            lineTo(s * 0.4f, s * 0.46f)
            lineTo(s * 0.55f, s * 0.62f)
            lineTo(s * 0.65f, s * 0.52f)
            lineTo(s * 0.82f, s * 0.74f)
            close()
        }
        drawPath(mountains, color)
        drawCircle(color, radius = s * 0.07f, center = Offset(s * 0.68f, s * 0.34f))
    }
}

@Composable
private fun SparkleGlyph(color: Color, modifier: Modifier = Modifier) {
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
private fun DragHandleGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.width * 0.16f
        for (column in 0..1) for (row in 0..2) {
            drawCircle(
                color = color,
                radius = r,
                center = Offset(size.width * (0.28f + 0.44f * column), size.height * (0.2f + 0.3f * row))
            )
        }
    }
}

@Composable
private fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        drawCircle(color, radius = s / 2)
        val tick = Path().apply {
            moveTo(s * 0.28f, s * 0.52f)
            lineTo(s * 0.44f, s * 0.67f)
            lineTo(s * 0.73f, s * 0.36f)
        }
        drawPath(tick, Color.Black.copy(alpha = 0.75f), style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------

/** Formats a byte count as KB or MB. */
private fun formatMemorySize(bytes: Int): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format(Locale.getDefault(), "%.1f MB", mb)
    else String.format(Locale.getDefault(), "%.0f KB", kb)
}

/** Formats an elapsed time like a stopwatch: "8.4 s" below one minute and "2:11" from then on. */
internal fun formatDuration(millis: Long, locale: Locale = Locale.getDefault()): String {
    val clamped = millis.coerceAtLeast(0L)
    if (clamped < 60_000) return String.format(locale, "%.1f s", (clamped / 100) / 10.0)
    val totalSeconds = clamped / 1000
    return String.format(locale, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** [SystemClock.elapsedRealtime], refreshed ten times a second while this is composed. */
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
