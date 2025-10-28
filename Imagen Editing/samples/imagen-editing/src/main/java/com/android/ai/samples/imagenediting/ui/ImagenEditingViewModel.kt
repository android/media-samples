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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.ai.samples.imagenediting.data.ImagenEditingDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ImagenEditingViewModel @Inject constructor(private val imagenDataSource: ImagenEditingDataSource) : ViewModel() {

    private val _uiState: MutableStateFlow<ImagenEditingUIState> = MutableStateFlow(ImagenEditingUIState.Initial)
    val uiState: StateFlow<ImagenEditingUIState> = _uiState

    private val _bitmapForMasking = MutableStateFlow<Bitmap?>(null)
    val bitmapForMasking: StateFlow<Bitmap?> = _bitmapForMasking

    private val _showMaskEditor = MutableStateFlow(false)
    val showMaskEditor: StateFlow<Boolean> = _showMaskEditor

    fun generateImage(prompt: String) {
        _uiState.value = ImagenEditingUIState.Loading
        viewModelScope.launch {
            try {
                val bitmap = imagenDataSource.generateImage(prompt)

                _bitmapForMasking.value = bitmap
                _showMaskEditor.value = true
                _uiState.value = ImagenEditingUIState.ImageGenerated(bitmap, contentDescription = prompt)
            } catch (e: Exception) {
                _uiState.value = ImagenEditingUIState.Error(e.message)
            }
        }
    }

    fun inpaintImage(sourceImage: Bitmap, maskImage: Bitmap, prompt: String, editSteps: Int = 50) {
       // TODO #5 - Implement ViewModel Logic for inpainting
    }

    fun onImageMaskReady(originalBitmap: Bitmap, maskBitmap: Bitmap) {
        val originalContentDescription = (_uiState.value as? ImagenEditingUIState.ImageGenerated)?.contentDescription ?: "Edited image"
        _uiState.value = ImagenEditingUIState.ImageMasked(
            originalBitmap = originalBitmap,
            maskBitmap = maskBitmap,
            contentDescription = originalContentDescription,
        )
        _showMaskEditor.value = false
        _bitmapForMasking.value = null
    }

    fun onCancelMasking() {
        Log.d("ImagenEditingViewModel", "onCancelMasking")
        _showMaskEditor.value = false
        _bitmapForMasking.value = null
        _uiState.value = ImagenEditingUIState.Initial
    }
}
