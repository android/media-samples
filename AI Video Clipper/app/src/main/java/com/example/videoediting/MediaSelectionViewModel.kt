package com.example.videoediting

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaSelectionUiState(
    val selectedVideoUris: List<Uri> = emptyList(),
    val isLoading: Boolean = false,
    val intentText: String = "",
    val errorMessage: String? = null,
    val uriPairs: List<Pair<String, Uri>> = emptyList(),
    val aiResponse: String? = null,
    val aiThoughts: String? = null
)

class MediaSelectionViewModel(application: Application) : AndroidViewModel(application) {
    
    // In a real app with DI, these would be injected
    private val storageRepository by lazy {
        val storage = Firebase.storage.apply {
            maxUploadRetryTimeMillis = 30000
            maxOperationRetryTimeMillis = 30000
        }
        FirebaseStorageRepository(storage)
    }
    private val geminiRepository = GeminiRepository()

    private val _uiState = MutableStateFlow(MediaSelectionUiState())
    val uiState: StateFlow<MediaSelectionUiState> = _uiState.asStateFlow()

    fun updateSelectedVideos(uris: List<Uri>) {
        _uiState.update { it.copy(selectedVideoUris = uris) }
    }

    fun updateIntentText(text: String) {
        _uiState.update { it.copy(intentText = text) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onEditWithGeminiClicked() {
        val currentState = _uiState.value
        if (currentState.selectedVideoUris.isEmpty()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // 1. Upload files in parallel
                val newUriPairs = coroutineScope {
                    currentState.selectedVideoUris.map { localUri ->
                        async {
                            val uploadedUri = storageRepository.uploadFile(localUri)
                            if (uploadedUri != null) {
                                uploadedUri to localUri
                            } else {
                                Log.e("MediaSelectionVM", "Failed to upload $localUri")
                                _uiState.update { it.copy(errorMessage = "Failed to upload video: $localUri") }
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                _uiState.update { it.copy(uriPairs = newUriPairs) }

                if (newUriPairs.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = "No files were uploaded successfully. Cannot proceed."
                        ) 
                    }
                    return@launch
                }

                // 2. Generate content
                // include a list of files to analyze
                val gsUris = newUriPairs.map { it.first }
                val uriListForPrompt = gsUris.joinToString("\n") { "- $it" }

                val context = getApplication<Application>()
                val promptData = context.resources.getString(R.string.generate_simple_preamble, currentState.intentText, uriListForPrompt)
                
                val response = geminiRepository.generateEditResponse(promptData, newUriPairs)
                
                if (response.responseText != null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            aiResponse = response.responseText,
                            aiThoughts = response.thoughts
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = "Failed to get a response from Gemini."
                        ) 
                    }
                }

            } catch (e: Exception) {
                Log.e("MediaSelectionVM", "Error processing video", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "An error occurred: ${e.localizedMessage}"
                    ) 
                }
            }
        }
    }
}
