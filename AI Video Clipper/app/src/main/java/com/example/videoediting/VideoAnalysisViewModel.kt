package com.example.videoediting

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@UnstableApi
@OptIn(ExperimentalApi::class)
data class VideoAnalysisUiState(
    val videos: List<GenAIParser.VideoSegment> = emptyList(),
    val durations: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true,
    val player: CompositionPlayer? = null,
)

@UnstableApi
class VideoAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VideoAnalysisUiState())
    val uiState: StateFlow<VideoAnalysisUiState> = _uiState.asStateFlow()

    fun initialize(jsonResponse: String, uriPairs: List<Pair<String, Uri>>) {
        if (_uiState.value.videos.isNotEmpty()) return // Already initialized

        val videos = GenAIParser.parseVideoSegments(jsonResponse)
        _uiState.update { it.copy(videos = videos) }

        val context = getApplication<Application>()

        viewModelScope.launch {
            val durations = coroutineScope {
                videos.map { video ->
                    async {
                        val localUri = uriPairs.find { it.first == video.uri }?.second
                        if (localUri != null) {
                            try {
                                val mediaItem = MediaItem.fromUri(localUri)
                                val retriever = MetadataRetriever.Builder(context, mediaItem).build()
                                val durationUs = retriever.retrieveDurationUs().await()
                                retriever.close()
                                video.uri to durationUs
                            } catch (e: Exception) {
                                Log.e("VideoAnalysisVM", "Error retrieving duration for ${video.uri}", e)
                                null
                            }
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
            _uiState.update { it.copy(durations = durations, isLoading = false) }
            setupPlayer(uriPairs)
        }
    }

    @UnstableApi
    @OptIn(ExperimentalApi::class)
    private fun setupPlayer(uriPairs: List<Pair<String, Uri>>) {
        val context = getApplication<Application>()
        val currentState = _uiState.value
        val editedMediaItems = currentState.videos.map { video ->
            val localUri = uriPairs.find { it.first == video.uri }?.second
            val durationUs = currentState.durations[video.uri] ?: 0L

            val mediaItemBuilder = MediaItem.Builder().setUri(localUri)

            video.segmentTimes?.let {
                val totalDurationMs = durationUs / 1000
                val endMs = it.endMs.coerceIn(0, totalDurationMs)
                val startMs = it.startMs.coerceIn(0, endMs)
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
            }

            EditedMediaItem.Builder(mediaItemBuilder.build())
                .setDurationUs(durationUs)
                .build()
        }

        val composition = Composition.Builder(
            EditedMediaItemSequence.withAudioAndVideoFrom(editedMediaItems)
        ).setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()

        val player = CompositionPlayer.Builder(context).build().apply {
            setComposition(composition)
            prepare()
        }

        _uiState.update { it.copy(player = player) }
    }

    fun releasePlayer() {
        _uiState.value.player?.release()
        _uiState.update { it.copy(player = null) }
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
