package com.example.vibeeffects.genai

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.vibeeffects.utils.Uniform

@UnstableApi
class GenAICustomShaderEffect(val glslFragmentShader: String, val customType: CustomEffectType, val uniforms: List<Uniform<*>>) : GlEffect {
    enum class CustomEffectType {
        SIMPLE_FRAGMENT
    }

    private val isRealtime = true

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        when(customType) {
            CustomEffectType.SIMPLE_FRAGMENT -> {
                return GenAICustomShaderProgram(context, glslFragmentShader, uniforms, useHdr)
            }
            else -> {
                throw VideoFrameProcessingException("Unknown custom effect type: $customType")
            }
        }
    }
}