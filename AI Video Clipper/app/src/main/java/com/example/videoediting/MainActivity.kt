package com.example.videoediting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.example.videoediting.ui.theme.GeminiVideoEditingTheme

class MainActivity : ComponentActivity() {
  @OptIn(UnstableApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      GeminiVideoEditingTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MediaSelectionScreen(
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}