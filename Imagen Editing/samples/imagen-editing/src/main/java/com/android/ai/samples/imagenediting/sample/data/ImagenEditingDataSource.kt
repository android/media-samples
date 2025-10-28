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
package com.android.ai.samples.imagenediting.sample.data

import android.graphics.Bitmap
import com.google.firebase.ai.type.Dimensions
import com.google.firebase.ai.type.PublicPreviewAPI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A data source that provides methods for interacting with the Firebase Imagen API
 * for various image generation and editing tasks.
 *
 * This class encapsulates the logic for initializing Imagen models and calling
 * their respective functions for image generation, inpainting, outpainting, and style transfer.
 * It leverages the Firebase AI SDK for seamless integration with Vertex AI backends.
 *
 * Note: This class uses `@OptIn(PublicPreviewAPI::class)` as Imagen features
 * are currently in public preview.
 */
@Singleton
class ImagenEditingDataSource @Inject constructor() {
    private companion object {
        // TODO #1 - Define constants for Imagen model names and default values.
        const val IMAGEN_MODEL_NAME = ""
        const val IMAGEN_EDITING_MODEL_NAME = ""
        const val DEFAULT_EDIT_STEPS = 50
    }

    // TODO #2 - Implement Firebase calls using Imagen models
    // @OptIn(PublicPreviewAPI::class)
    // private val imagenModel =

    // @OptIn(PublicPreviewAPI::class)
    // private val editingModel =

    /**
     * Generates an image based on the provided prompt.
     *
     * This function uses the Imagen model to generate an image from a textual description.
     * It returns the generated image as a Bitmap.
     *
     * @param prompt The textual description to generate the image from.
     * @return The generated image as a [Bitmap].
     * @throws Exception if the image generation fails.
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun generateImage(prompt: String): Bitmap {
        val imageResponse = imagenModel.generateImages(
            prompt = prompt,
        )
        val image = imageResponse.images.first()
        return image.asBitmap()
    }

    /**
     * Performs inpainting on a source image using a provided mask and prompt.
     *
     * This function utilizes the Imagen editing model to fill in the masked areas
     * of the source image based on the textual prompt.
     *
     * @param sourceImage The original image to be inpainted.
     * @param maskImage A bitmap representing the mask, where white areas indicate
     *                  regions to be inpainted and black areas indicate regions to be preserved.
     * @param prompt A textual description of what should be generated in the masked areas.
     * @param editSteps The number of editing steps to perform. Defaults to `DEFAULT_EDIT_STEPS`.
     * @return A [Bitmap] representing the inpainted image.
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun inpaintImage(sourceImage: Bitmap, maskImage: Bitmap, prompt: String, editSteps: Int = DEFAULT_EDIT_STEPS): Bitmap {
        // TODO #3 - Implement data source for inpainting;
        return sourceImage;
    }

    /**
     * Outpaints an image to the target dimensions using the Firebase Imagen API.
     * This function extends the original image by generating content around it
     * based on the provided prompt and target dimensions.
     *
     * @param sourceImage The original bitmap image to be outpainted.
     * @param targetDimensions The desired dimensions of the outpainted image.
     * @param prompt An optional text prompt to guide the outpainting process.
     * @return The outpainted bitmap image.
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun outpaintImage(sourceImage: Bitmap, targetDimensions: Dimensions, prompt: String): Bitmap {
        // TODO #Bonus - Implement data source for outpainting;
        return sourceImage;
    }
}
