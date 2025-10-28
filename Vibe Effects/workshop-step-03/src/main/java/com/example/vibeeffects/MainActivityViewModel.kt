package com.example.vibeeffects

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.example.vibeeffects.genai.GenAIEffectParser
import com.example.vibeeffects.genai.jsonSchema
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.launch

@SuppressLint("RestrictedApi")
@UnstableApi
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    var isProcessing by mutableStateOf(false)

    var videoUri by mutableStateOf<Uri?>(null)
    var compositionPlayer by mutableStateOf<CompositionPlayer?>(null)
    var videoDuration = 0L

    var aiModel by mutableStateOf<GenerativeModel?>(null)
    var aiResponse by mutableStateOf<String?>(null)
    var aiThoughts by mutableStateOf<String?>(null)
    var aiThinkingBudget by mutableIntStateOf(8192)
    var aiTokensUsed by mutableIntStateOf(-1)

    fun onVideoUriSelected(uri: Uri) {
        videoUri = uri
        if(videoUri == null) {
            compositionPlayer?.release()
            compositionPlayer = null
            return
        }
        val mediaItem = MediaItem.fromUri(uri)

        // Get video duration - needed for preview
        val retriever = MetadataRetriever.Builder(application, mediaItem).build()
        try {
            videoDuration = retriever.retrieveDurationUs().get()
        } catch (e: Exception) {
            videoDuration = 0L
            Log.e(TAG, "Error retrieving video duration", e)
        } finally {
            retriever.close()
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).setDurationUs(videoDuration).build()
        val sequence = EditedMediaItemSequence.Builder(editedMediaItem).build()
        val composition = Composition.Builder(sequence).build()

        compositionPlayer?.release()
        compositionPlayer = CompositionPlayer.Builder(application).build().apply {
            setComposition(composition)
            prepare()
        }
    }

    suspend fun generateNewEffectSequence(prompt: String) {
        isProcessing = true
        aiTokensUsed = -1
        aiModel = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-2.5-pro",
                generationConfig = generationConfig {
                    temperature = 0f
                    responseMimeType = "application/json"
                    responseSchema = jsonSchema
                    thinkingConfig = thinkingConfig {
                        thinkingBudget = aiThinkingBudget
                        includeThoughts = true
                    }
                }
            )
        val fullPrompt =
            getApplication<Application>()
                .resources
                .getString(R.string.generate_simple_preamble, prompt)
        val response = aiModel?.run { generateContent(fullPrompt) }
        aiThoughts = if(response?.thoughtSummary.isNullOrEmpty()) null else response.thoughtSummary
        aiResponse = if(response?.text.isNullOrEmpty()) null else response.text
        aiTokensUsed = response?.usageMetadata?.thoughtsTokenCount ?: -1
        Log.d(TAG, "Thought summary: $aiThoughts")
        Log.d(TAG, "Response: $aiResponse")
        aiResponse?.let { applyGeneratedEffects(it) }
        isProcessing = false
    }

    fun updateAndApplyEffects(editedResponse: String) {
        aiResponse = editedResponse
        applyGeneratedEffects(editedResponse)
    }

    private fun applyGeneratedEffects(response: String) {
        val effects = GenAIEffectParser.parse(getApplication(), response)

        videoUri?.let {
            val mediaItem = MediaItem.fromUri(it)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setDurationUs(videoDuration)
                .setEffects(Effects(emptyList(), effects))
                .build()
            val sequence = EditedMediaItemSequence.Builder(editedMediaItem).build()
            val composition = Composition.Builder(sequence).build()
            compositionPlayer?.release()
            compositionPlayer = CompositionPlayer.Builder(getApplication()).build().apply {
                setComposition(composition)
                prepare()
                repeatMode = Player.REPEAT_MODE_ALL
                play()
            }
        }
    }

    override fun onCleared() {
        compositionPlayer?.release()
        super.onCleared()
    }

    companion object {
        const val TAG = "MainActivityVM"
    }
}