package com.example.vibeeffects.genai

import com.google.firebase.ai.type.Schema

val jsonSchema = Schema.obj(mapOf(
    "name" to Schema.string("A short and catchy name to show the user"),
    "effects" to Schema.array(
        Schema.obj(mapOf(
            "effectType" to Schema.enumeration(listOf("media3", "custom")),
            "name" to Schema.string(),
            "description" to Schema.string(),
            "parameters" to Schema.obj(mapOf(
                "brightness" to Schema.float("A value from -1.0 (black) to 1.0 (white), with 0.0 being no change."),
                "contrast" to Schema.float("Contrast values range from -1 (all gray pixels) to 1 (maximum difference of colors). 0 means to add no contrast and leaves the frames unchanged"),
                "rgbMatrix" to Schema.array(Schema.float(), "A 16-element array representing a 4x4 column-major matrix. A 9 element 3x3 column-major matrix may also be returned. Make sure order of elements is correct.", minItems = 16, maxItems = 16),
                "factory" to Schema.string("Convenience method to create a pre-defined matrix. Supported values: 'createGrayscaleFilter', 'createInvertedFilter'."),
                "hue" to Schema.float("Hue adjustment in degrees (-180 to 180)."),
                "saturation" to Schema.float("Saturation adjustment. 0.0 is grayscale, 1.0 is no change. Values > 1.0 increase saturation."),
                "lightness" to Schema.float("Lightness adjustment. Values are added to the lightness channel."),
                "alphaScale" to Schema.float("A value to multiply the alpha channel by. 0.0 is fully transparent, 1.0 is no change."),
                "sigmaX" to Schema.float("The standard deviation of the Gaussian distribution in the X direction."),
                "sigmaY" to Schema.float("The standard deviation of the Gaussian distribution in the Y direction."),
                "bitmapUri" to Schema.string("URI to the image file to be used as an overlay."),
                "alpha" to Schema.float("The opacity of the overlay, from 0.0 to 1.0.")
            ), optionalProperties = listOf("brightness", "contrast", "rgbMatrix", "factory", "hue", "saturation", "lightness", "alphaScale", "sigmaX", "sigmaY", "bitmapUri", "alpha")),
            "glslFragmentShader" to Schema.string("GLSL shader code, using the provided template"),
            "uniforms" to Schema.array(Schema.obj(mapOf(
                "parameterName" to Schema.string(),
                "displayName" to Schema.string(),
                "description" to Schema.string(),
                "type" to Schema.enumeration(listOf("float, vec2, vec3, vec4, int")),
                "valueRange" to Schema.array(Schema.float(), minItems = 2, maxItems = 2),
                "defaultValue" to Schema.float()
            )))
        ), optionalProperties = listOf("parameters", "glslFragmentShader", "uniforms"))
    ))
)