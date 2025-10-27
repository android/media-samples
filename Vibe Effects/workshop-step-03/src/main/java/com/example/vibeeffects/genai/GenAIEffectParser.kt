package com.example.vibeeffects.genai

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import com.example.vibeeffects.utils.NumericUniform
import com.example.vibeeffects.utils.Range
import com.example.vibeeffects.utils.Uniform
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A parser to convert a JSON string (from Gemini) into a list of Media3 [androidx.media3.common.Effect] objects. This
 * class handles both standard Media3 effects and custom GLSL shader effects.
 */
@UnstableApi // Media3 Effects APIs are still unstable.
object GenAIEffectParser {
  private const val TAG = "GenAIEffectParser"

  /**
   * Parses a JSON string and returns a list of Media3 effects.
   *
   * @param context The application context, needed for creating some effects.
   * @param jsonString The JSON string response from the Gemini agent.
   * @return A [List] of [androidx.media3.common.Effect] objects. Returns an empty list on failure.
   */
  fun parse(context: Context, jsonString: String): List<Effect> {
    val effects: MutableList<Effect> = ArrayList()
    try {
      val root = JSONObject(jsonString)
      val effectsArray = root.getJSONArray("effects")
      for (i in 0 until effectsArray.length()) {
        val effectJson = effectsArray.getJSONObject(i)
        val effectType = effectJson.getString("effectType")
        var effect: Effect? = null
        when (effectType) {
          "media3" -> {
            effect = parseMedia3Effect(context, effectJson)
          }
          "custom" -> {
            effect =
              parseCustomEffect(
                context,
                GenAICustomShaderEffect.CustomEffectType.SIMPLE_FRAGMENT,
                effectJson,
              )
          }
          else -> {
            Log.w(TAG, "Unknown effect type: $effectType")
          }
        }
        if (effect != null) {
          effects.add(effect)
        }
      }
    } catch (e: JSONException) {
      Log.e(TAG, "Failed to parse video effects JSON", e)
      // Return an empty list in case of parsing error
      return ArrayList()
    }
    return effects
  }

  /** Parses a JSONObject representing a standard Media3 effect. */
  private fun parseMedia3Effect(context: Context, effectJson: JSONObject): Effect? {
    val name = effectJson.getString("name")
    val params = effectJson.optJSONObject("parameters") ?: JSONObject() // Create empty object to avoid null checks
    when (name) {
      "Brightness" -> {
        val brightness = params.optDouble("brightness", 0.0).toFloat()
        return Brightness(brightness)
      }
      "Contrast" -> {
        val contrast = params.optDouble("contrast", 1.0).toFloat()
        return Contrast(contrast)
      }
      "AlphaScale" -> {
        val alphaScale = params.optDouble("alphaScale", 1.0).toFloat()
        return AlphaScale(alphaScale)
      }
      "RgbFilter" -> {
        // The factory method is preferred for simplicity if provided.
        val factory = params.optString("factory")
        if (!factory.isEmpty()) {
          when (factory) {
            "createGrayscaleFilter" -> {
              return RgbFilter.createGrayscaleFilter()
            }
            "createInvertedFilter" -> {
              return RgbFilter.createInvertedFilter()
            }
          }
        }
        // Fallback to raw matrix if factory is not used or unknown.
        val matrixArray = params.optJSONArray("rgbMatrix")
        if (matrixArray != null && (matrixArray.length() == 16 || matrixArray.length() == 9)) {
          val matrix = FloatArray(16)
          if (matrixArray.length() == 16) {
            for (i in 0 until matrixArray.length()) {
              matrix[i] = matrixArray.getDouble(i).toFloat()
            }
          }
          if (matrixArray.length() == 9) {
            for (i in 0 until matrixArray.length()) {
              matrix[i] = matrixArray.getDouble(i).toFloat()
            }
            matrix[10] = 0f
            matrix[11] = 0f
            matrix[12] = 0f
            matrix[13] = 0f
            matrix[14] = 0f
            matrix[15] = 1f
          }
          return RgbMatrix { presentationTimeUs: Long, useHdr: Boolean -> matrix }
        }
        return null // Invalid RgbFilter definition
      }
      "HslAdjustment" -> {
        val hslBuilder = HslAdjustment.Builder()
        if (params.has("hue")) {
          hslBuilder.adjustHue(params.getDouble("hue").toFloat())
        }
        if (params.has("saturation")) {
          hslBuilder.adjustSaturation(params.getDouble("saturation").toFloat())
        }
        if (params.has("lightness")) {
          hslBuilder.adjustLightness(params.getDouble("lightness").toFloat())
        }
        return hslBuilder.build()
      }
      else -> {
        Log.w(TAG, "Unsupported Media3 effect: $name")
        return null
      }
    }
  }

  /** Parses a JSONObject representing a custom GLSL shader effect. */
  private fun parseCustomEffect(
      context: Context,
      customType: GenAICustomShaderEffect.CustomEffectType,
      effectJson: JSONObject,
  ): Effect {
    val name = effectJson.getString("name")
    val description = effectJson.getString("description")
    val glslFragmentShader = effectJson.getString("glslFragmentShader")
    val uniformsArray = effectJson.optJSONArray("uniforms")
    val uniforms = parseUniforms(uniformsArray)
    return GenAICustomShaderEffect(glslFragmentShader, customType, uniforms)
  }

  private fun parseUniforms(uniformsArray: JSONArray?): List<Uniform<*>> {
    val uniforms: MutableList<Uniform<*>> = ArrayList()
    if (uniformsArray != null) {
      for (i in 0 until uniformsArray.length()) {
        val uniformJson = uniformsArray.getJSONObject(i)
        val type = uniformJson.getString("type")
        val uName = uniformJson.getString("parameterName")
        val uDisplayName = uniformJson.getString("displayName")
        val uDescription = uniformJson.getString("description")
        val rangeArray = uniformJson.getJSONArray("valueRange")
        when (type) {
          "float" -> {
            uniforms.add(
                NumericUniform(
                    uName,
                    type,
                    uDisplayName,
                    uDescription,
                    uniformJson.getDouble("defaultValue").toFloat(),
                    Range(rangeArray.getDouble(0).toFloat(), rangeArray.getDouble(1).toFloat()),
                )
            )
          }
          "int" -> {
            uniforms.add(
                NumericUniform(
                    uName,
                    type,
                    uDisplayName,
                    uDescription,
                    uniformJson.getInt("defaultValue"),
                    Range(rangeArray.getInt(0), rangeArray.getInt(1)),
                )
            )
          }
          "vec2",
          "vec3",
          "vec4" -> {
            uniforms.add(
                Uniform(
                    uName,
                    type,
                    uDisplayName,
                    uDescription,
                    uniformJson.getJSONArray("defaultValue"), // Keep as Object to handle different
                    // types
                )
            )
          }
        }
      }
    }
    return uniforms
  }
}