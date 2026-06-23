package com.example.videoediting

import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {

    private val aiModel = Firebase.ai(backend = GenerativeBackend.vertexAI())
        .generativeModel(
            modelName = "gemini-2.5-pro",
            generationConfig = generationConfig {
                temperature = 0f
                responseMimeType = "application/json"
                responseSchema = jsonSchema
                thinkingConfig = thinkingConfig {
                    thinkingBudget = 1024
                    includeThoughts = true
                }
            }
        )

    suspend fun generateEditResponse(promptData: String, uriPairs: List<Pair<String, Uri>>): GeminiResponse = withContext(Dispatchers.IO) {
        val gsUris = uriPairs.map { it.first }
        val requestContent = content {
            gsUris.forEach { gsUri ->
                fileData(gsUri, "video/mp4")
            }

            text(promptData)
        }
        val response = aiModel.generateContent(requestContent)
        
        val aiThoughts = if (response.thoughtSummary.isNullOrEmpty()) null else response.thoughtSummary
        val aiResponseText = if (response.text.isNullOrEmpty()) null else response.text
        Log.d("GeminiRepository", "response $aiResponseText")
        val aiTokensUsed = response.usageMetadata?.thoughtsTokenCount ?: -1

        GeminiResponse(aiThoughts, aiResponseText, aiTokensUsed)
    }
}

data class GeminiResponse(
    val thoughts: String?,
    val responseText: String?,
    val tokensUsed: Int
)
