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
import android.graphics.BitmapFactory
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.ai.samples.imagenediting.R
import com.android.ai.uicomponent.GenerateButton
import com.android.ai.uicomponent.SampleDetailTopAppBar
import com.android.ai.uicomponent.TextInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagenEditingScreen(viewModel: ImagenEditingViewModel = hiltViewModel()) {
    val uiState: ImagenEditingUIState by viewModel.uiState.collectAsStateWithLifecycle()
    val showMaskEditor: Boolean by viewModel.showMaskEditor.collectAsStateWithLifecycle()
    val bitmapForMasking: Bitmap? by viewModel.bitmapForMasking.collectAsStateWithLifecycle()

    ImagenEditingScreenContent(
        uiState = uiState,
        showMaskEditor = showMaskEditor,
        bitmapForMasking = bitmapForMasking,
        onGenerateClick = viewModel::generateImage,
        onInpaintClick = { source, mask, prompt -> viewModel.inpaintImage(source, mask, prompt) },
        onImageMaskReady = { source, mask -> viewModel.onImageMaskReady(source, mask) },
        onCancelMasking = viewModel::onCancelMasking,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun ImagenEditingScreenContent(
    uiState: ImagenEditingUIState,
    showMaskEditor: Boolean,
    bitmapForMasking: Bitmap?,
    onGenerateClick: (String) -> Unit,
    onInpaintClick: (source: Bitmap, mask: Bitmap, prompt: String) -> Unit,
    onImageMaskReady: (source: Bitmap, mask: Bitmap) -> Unit,
    onCancelMasking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isGenerating = uiState is ImagenEditingUIState.Loading
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SampleDetailTopAppBar(
                sampleName = stringResource(R.string.editing_title_image_generation_title),
                sampleDescription = stringResource(R.string.editing_title_image_generation_subtitle),
                sourceCodeUrl = "https://github.com/android/ai-samples/tree/main/ai-catalog/samples/imagen-editing",
                onBackClick = { backDispatcher?.onBackPressed() },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { innerPadding ->
        val context = LocalContext.current
        val imageBitmap = remember {
            val bitmap = BitmapFactory.decodeResource(context.resources, com.android.ai.uicomponent.R.drawable.img_fill)
            bitmap.asImageBitmap()
        }
        val imageShader = remember {
            ImageShader(
                image = imageBitmap,
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated,
            )
        }

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .padding(16.dp)
                    .imePadding()
                    .widthIn(max = 440.dp)
                    .fillMaxHeight(0.85f)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(40.dp),
                    )
                    .clip(RoundedCornerShape(40.dp))
                    .background(ShaderBrush(imageShader)),
                contentAlignment = Alignment.Center,
            ) {
                val keyboardController = LocalSoftwareKeyboardController.current

                when (uiState) {
                    is ImagenEditingUIState.Initial -> {
                        Text(
                            text = stringResource(R.string.generate_an_image_to_edit),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(24.dp)
                                .align(Alignment.Center),
                        )

                        val textFieldState = rememberTextFieldState()

                        TextField(
                            textFieldState,
                            isGenerating,
                            onGenerateClick,
                            keyboardController,
                            placeholder = stringResource(R.string.describe_the_image_to_generate),
                        )
                    }

                    is ImagenEditingUIState.Loading -> {
                        Box(modifier.fillMaxSize()) {
                            ContainedLoadingIndicator(
                                modifier = Modifier
                                    .size(60.dp)
                                    .align(Alignment.Center),
                            )
                        }
                    }

                    is ImagenEditingUIState.ImageGenerated -> {
                        if (showMaskEditor && bitmapForMasking != null) {
                            val textFieldState = rememberTextFieldState()

                            ImagenEditingMaskEditor(
                                sourceBitmap = bitmapForMasking,
                                onMaskFinalized = { maskBitmap ->
                                    onImageMaskReady(bitmapForMasking, maskBitmap)
                                },
                                onCancel = { onCancelMasking() },
                                modifier = Modifier.fillMaxSize(),
                            )

                            Text(
                                text = "Draw a mask on the image",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(24.dp)
                                    .align(Alignment.TopCenter)
                                    .background(color = MaterialTheme.colorScheme.surfaceContainer),
                            )
                        } else {
                            val textFieldState = rememberTextFieldState()

                            Image(
                                bitmap = uiState.bitmap.asImageBitmap(),
                                contentDescription = uiState.contentDescription,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            TextField(
                                textFieldState,
                                isGenerating,
                                onGenerateClick,
                                keyboardController,
                                placeholder = stringResource(R.string.describe_the_image_to_generate),
                            )
                        }
                    }

                    is ImagenEditingUIState.ImageMasked -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = uiState.originalBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.editing_generated_image),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Image(
                                bitmap = uiState.maskBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.editing_generated_mask),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(Color.Red.copy(alpha = 0.5f)),
                            )
                        }
                        val textFieldState = rememberTextFieldState()

                        TextField(
                            textFieldState = textFieldState,
                            isGenerating = isGenerating,
                            onGenerateClick = { prompt -> onInpaintClick(uiState.originalBitmap, uiState.maskBitmap, prompt) },
                            keyboardController,
                            placeholder = stringResource(R.string.describe_the_image_to_in_paint),
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TextField(
    textFieldState: TextFieldState,
    isGenerating: Boolean,
    onGenerateClick: (String) -> Unit,
    keyboardController: SoftwareKeyboardController?,
    placeholder: String = "",
) {
    TextInput(
        state = textFieldState,
        placeholder = placeholder,
        primaryButton = {
            GenerateButton(
                text = "",
                icon = painterResource(id = com.android.ai.uicomponent.R.drawable.ic_ai_img),
                modifier = Modifier
                    .width(72.dp)
                    .height(55.dp)
                    .padding(4.dp),
                enabled = !isGenerating,
                onClick = {
                    onGenerateClick(textFieldState.text.toString())
                    keyboardController?.hide()
                },
            )
        },
        modifier = Modifier
            .widthIn(max = 646.dp)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .align(Alignment.BottomCenter),
    )
}
