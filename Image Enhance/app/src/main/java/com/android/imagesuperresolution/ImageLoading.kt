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

import android.content.ContentResolver
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.graphics.scale
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Longest edge, in pixels, that images are decoded and displayed at.
 *
 * Typical 12 MP photos (4032x3024) stay at full resolution, while larger photos are downsampled.
 * A 48 MP photo decoded at full size is a 192 MB bitmap: drawing it throws "Canvas: trying to
 * draw too large bitmap", and allocations like that put the app at risk of being killed.
 */
internal const val MAX_IMAGE_DIMENSION = 4096

internal data class PixelSize(val width: Int, val height: Int)

/**
 * Returns the largest size with the aspect ratio of [width] x [height] whose longest edge is at
 * most [maxDimension]. Sizes that already fit are returned unchanged.
 */
internal fun fitWithin(width: Int, height: Int, maxDimension: Int): PixelSize {
    require(width > 0 && height > 0) { "Invalid size ${width}x$height" }
    require(maxDimension > 0) { "Invalid max dimension $maxDimension" }
    val longestEdge = max(width, height)
    if (longestEdge <= maxDimension) return PixelSize(width, height)
    val scale = maxDimension.toDouble() / longestEdge
    return PixelSize(
        width = max(1, (width * scale).roundToInt()),
        height = max(1, (height * scale).roundToInt()),
    )
}

/**
 * Decodes [uri] into a bitmap that can be handed to the enhancement service.
 *
 * - EXIF orientation is applied, so portrait camera photos aren't shown (or enhanced) sideways.
 * - The image is downsampled so that its longest edge is at most [maxDimension].
 * - The result is always a software ARGB_8888 bitmap, since it gets parceled to Play services.
 *   ImageDecoder would otherwise return HARDWARE bitmaps, or F16/1010102 ones for HDR images.
 */
@Throws(IOException::class)
internal fun ContentResolver.decodeBitmapForEnhancement(
    uri: Uri,
    maxDimension: Int = MAX_IMAGE_DIMENSION,
): Bitmap = decodeSourceForEnhancement(ImageDecoder.createSource(this, uri), maxDimension)

/**
 * Decodes a bundled drawable resource [resId] into a software ARGB_8888 bitmap suitable for the
 * enhancement service.
 */
@Throws(IOException::class)
internal fun Resources.decodeBitmapForEnhancement(
    @DrawableRes resId: Int,
    maxDimension: Int = MAX_IMAGE_DIMENSION,
): Bitmap = decodeSourceForEnhancement(ImageDecoder.createSource(this, resId), maxDimension)

/**
 * Decodes a local image [file] (e.g. captured via CameraX) into a software ARGB_8888 bitmap with
 * EXIF orientation applied.
 */
@Throws(IOException::class)
internal fun decodeFileForEnhancement(
    file: File,
    maxDimension: Int = MAX_IMAGE_DIMENSION,
): Bitmap = decodeSourceForEnhancement(ImageDecoder.createSource(file), maxDimension)

@Throws(IOException::class)
private fun decodeSourceForEnhancement(
    source: ImageDecoder.Source,
    maxDimension: Int,
): Bitmap {
    val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val target = fitWithin(info.size.width, info.size.height, maxDimension)
        if (target.width != info.size.width || target.height != info.size.height) {
            decoder.setTargetSize(target.width, target.height)
        }
    }
    val decodedConfig = decoded.config
    if (decodedConfig == Bitmap.Config.ARGB_8888) return decoded

    val converted: Bitmap? = decoded.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)
    decoded.recycle()
    return converted ?: throw IOException("Unable to convert $decodedConfig image to ARGB_8888")
}

/**
 * Returns this bitmap, or a downscaled copy if it is too large to draw (see
 * [MAX_IMAGE_DIMENSION]). Enhanced results can be several times larger than the input when
 * "Upscale" is selected.
 */
internal fun Bitmap.scaledForDisplay(maxDimension: Int = MAX_IMAGE_DIMENSION): Bitmap {
    val target = fitWithin(width, height, maxDimension)
    if (target.width == width && target.height == height) return this
    return scale(target.width, target.height)
}

/**
 * Decodes bundled drawable [resId] as a thumbnail: subsampled while decoding, so the full-size
 * bitmap is never built, to the smallest power-of-two reduction whose shortest edge is still at
 * least [minShortEdge] pixels. Blocking: call it off the main thread.
 */
@Throws(IOException::class)
internal fun Resources.decodeThumbnail(@DrawableRes resId: Int, minShortEdge: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(this, resId, bounds)
    val shortEdge = minOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (shortEdge / (sampleSize * 2) >= minShortEdge) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeResource(this, resId, options)
        ?: throw IOException("Unable to decode drawable $resId")
}
