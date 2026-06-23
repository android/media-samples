package com.example.videoediting

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.buttons.PlayPauseButton
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState

@UnstableApi
@OptIn(ExperimentalApi::class)
@Composable
fun VideoAnalysisResultScreen(
  jsonResponse: String,
  uriPairs: List<Pair<String, Uri>>,
  modifier: Modifier = Modifier,
  viewModel: VideoAnalysisViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    LaunchedEffect(jsonResponse, uriPairs) {
        viewModel.initialize(jsonResponse, uriPairs)
    }

    if (uiState.isLoading) {
        LoadingScreen(modifier)
    } else {
        ResultContent(
            uiState = uiState,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@UnstableApi
@OptIn(ExperimentalApi::class)
@Composable
private fun ResultContent(
  uiState: VideoAnalysisUiState,
  modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            VideoPlayerSection(uiState.player)
        }

        item {
            ReasoningHeader()
        }

        itemsIndexed(uiState.videos) { index, video ->
            ReasoningCard(index, video)
        }
    }
}

@UnstableApi
@OptIn(ExperimentalApi::class)
@Composable
private fun VideoPlayerSection(player: CompositionPlayer?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        if (player != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerSurface(
                    player = player, modifier = Modifier.weight(1f)
                )

                val buttonState = rememberPlayPauseButtonState(player)
                PlayPauseButton(buttonState)
              }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayPauseButton(buttonState: PlayPauseButtonState) {
    val icon =
        if (buttonState.showPlay) painterResource(R.drawable.rounded_play_arrow_24) else painterResource(
            R.drawable.rounded_pause_24
        )
    val contentDescription = if (buttonState.showPlay) "Play" else "Pause"
    FilledIconButton(onClick = buttonState::onClick, enabled = buttonState.isEnabled) {
        Icon(icon, contentDescription)
    }
}
@Composable
private fun ReasoningHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Reasonings",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ReasoningCard(index: Int, video: GenAIParser.VideoSegment) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Segment ${index + 1}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.reasoning ?: "No reasoning provided",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
