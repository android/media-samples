package com.example.videoediting

import com.google.firebase.ai.type.Schema

val jsonSchema = Schema.obj(mapOf(
  "videos" to Schema.array(
    Schema.obj(mapOf(
      "uri" to Schema.string(),
      "mostEngagingSegment" to Schema.obj(
        mapOf(
          "startMs" to Schema.long("A start ms value for clipping"),
          "endMs" to Schema.long("End ms value for clipping"),
          "reasoning" to Schema.string("reasoning for not finding a clipping range")
        )
      ),
    ))
  ))
)