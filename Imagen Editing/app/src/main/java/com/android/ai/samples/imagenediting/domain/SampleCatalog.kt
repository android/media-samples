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
package com.android.ai.samples.imagenediting.domain

import android.Manifest
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.android.ai.samples.imagenediting.R
import com.android.ai.samples.imagenediting.sample.ui.ImagenEditingScreen
import com.android.ai.theme.extendedColorScheme

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
val sampleCatalog = listOf(
    SampleCatalogItem(
        title = R.string.imagen_editing_sample_title,
        description = R.string.imagen_editing_sample_description,
        route = "ImagenMaskEditing",
        sampleEntryScreen = { ImagenEditingScreen() },
        tags = listOf(SampleTags.IMAGEN, SampleTags.FIREBASE),
        needsFirebase = true,
        keyArt = R.drawable.img_keyart_imagen,
    ),
    // To create a new sample entry, add a new SampleCatalogItem here.
)

data class SampleCatalogItem(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val route: String,
    val sampleEntryScreen: @Composable () -> Unit,
    val tags: List<SampleTags> = emptyList(),
    val needsFirebase: Boolean = false,
    val isFeatured: Boolean = false,
    @DrawableRes val keyArt: Int? = null,
)

enum class SampleTags(
    val label: String,
    val backgroundColor: Color,
) {
    FIREBASE("Firebase", extendedColorScheme.firebase),
    GEMINI_FLASH("Gemini Flash", extendedColorScheme.geminiProFlash),
    IMAGEN("Imagen", extendedColorScheme.imagen),
}
