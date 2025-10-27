package com.example.vibeeffects

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.example.vibeeffects.ui.theme.VibeEffectsWorkshopTheme

@SuppressLint("RestrictedApi")
@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibeEffectsWorkshopTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VideoEditScreen(
                        viewModel = viewModel(),
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun VideoEditScreen(viewModel: MainActivityViewModel, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VideoPreview(viewModel.compositionPlayer, Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PickVideo({ uri -> viewModel.onVideoUriSelected(uri) })
            }
        }
    }

    @Composable
    fun VideoPreview(player: CompositionPlayer?, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier, contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    TODO("Implement PlayerSurface")

                    val buttonState = rememberPlayPauseButtonState(player)
                    PlayPauseButton(buttonState)
                }
            } else {
                Text("No video selected")
            }
        }
    }

    @Composable
    fun PlayPauseButton(buttonState: PlayPauseButtonState) {
        val icon =
            if (buttonState.showPlay) painterResource(R.drawable.rounded_play_arrow_24) else painterResource(
                R.drawable.rounded_pause_24
            )
        val contentDescription = if (buttonState.showPlay) "Play" else "Pause"
        FilledIconButton(onClick = buttonState::onClick, shapes = IconButtonDefaults.shapes(), enabled = buttonState.isEnabled) {
            Icon(icon, contentDescription)
        }
    }

    @Composable
    fun PickVideo(
        updateUri: (Uri) -> Unit, // Callback for when a new item is selected
        modifier: Modifier = Modifier
    ) {
        val pickMedia =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    updateUri(uri)
                    Log.d(TAG, "Selected: $uri")
                } else {
                    // Nothing selected
                    Log.w(TAG, "Nothing selected")
                }
            }
        // Button to launch the photo picker
        FilledTonalButton(
            onClick = {
                // Update based on if you want images and/or videos
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            shapes = ButtonDefaults.shapes(),
            modifier = modifier
        ) {
            Text("Select video")
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}