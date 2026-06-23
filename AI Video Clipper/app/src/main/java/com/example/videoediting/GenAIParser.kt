package com.example.videoediting

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GenAIParser {
  private const val TAG = "GenAIParser"

  // 1. JSON DTOs matching the exact schema returned by Gemini
  @Serializable
  private data class MostEngagingSegmentDto(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val reasoning: String? = null
  )

  @Serializable
  private data class VideoSegmentDto(
    val uri: String,
    val mostEngagingSegment: MostEngagingSegmentDto? = null
  )

  @Serializable
  private data class AIResponseDto(
    val videos: List<VideoSegmentDto> = emptyList()
  )

  // 2. Public Domain Models (Unchanged to prevent breaking callers)
  data class SegmentTimes(val startMs: Long, val endMs: Long)
  data class VideoSegment(val uri: String, val segmentTimes: SegmentTimes?, val reasoning: String?)

  private val jsonConfig = Json {
    ignoreUnknownKeys = true // Resilient to extra fields returned by the AI
  }

  /**
   * Parses a JSON string conforming to AIResponseJsonSchema and returns a list of VideoSegment objects.
   *
   * @param jsonString The JSON string response from the Gemini agent.
   * @return A [List] of [VideoSegment] objects. Returns an empty list on failure.
   */
  fun parseVideoSegments(jsonString: String): List<VideoSegment> {
    return try {
      val responseDto = jsonConfig.decodeFromString<AIResponseDto>(jsonString)
      responseDto.videos.map { dto ->
        val segmentDto = dto.mostEngagingSegment
        var segmentTimes: SegmentTimes? = null
        var reasoning: String? = null

        if (segmentDto != null) {
          val startMs = segmentDto.startMs ?: -1L
          val endMs = segmentDto.endMs ?: -1L
          reasoning = segmentDto.reasoning

          if (startMs >= 0 && endMs > startMs) {
            segmentTimes = SegmentTimes(startMs, endMs)
          }
        }
        VideoSegment(dto.uri, segmentTimes, reasoning)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse video segments JSON with kotlinx-serialization", e)
      emptyList()
    }
  }
}
