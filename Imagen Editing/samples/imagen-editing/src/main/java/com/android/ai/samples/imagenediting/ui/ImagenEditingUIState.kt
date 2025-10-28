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

sealed interface ImagenEditingUIState {
    data object Initial : ImagenEditingUIState
    data object Loading : ImagenEditingUIState
    data class ImageGenerated(
        val bitmap: Bitmap,
        val contentDescription: String,
    ) : ImagenEditingUIState

    data class ImageMasked(
        val originalBitmap: Bitmap,
        val maskBitmap: Bitmap,
        val contentDescription: String,
    ) : ImagenEditingUIState

    data class Error(val message: String?) : ImagenEditingUIState
}
