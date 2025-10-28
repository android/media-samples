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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.example.vibeeffects.ui.theme.VibeEffectsWorkshopTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
                            .padding(4.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun VideoEditScreen(viewModel: MainActivityViewModel, modifier: Modifier = Modifier) {
        var showPromptDialog by remember { mutableStateOf(false) }
        var showResponseDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        if (showPromptDialog) {
            AddEffectsDialogue(
                dismissDialog = { showPromptDialog = false },
                thinkingBudget = viewModel.aiThinkingBudget,
                updateThinkingBudget = { newBudget -> viewModel.aiThinkingBudget = newBudget },
                submitPrompt = { promptText -> viewModel.generateNewEffectSequence(promptText) },
                coroutineScope = coroutineScope
            )
        }

        if (showResponseDialog) {
            ResponseDialogue(
                dismissDialog = { showResponseDialog = false },
                responseText = viewModel.aiResponse ?: "",
                updateResponseAndApply = { newResponse ->
                    showResponseDialog = false
                    viewModel.updateAndApplyEffects(newResponse)
                                         },
                thoughtsText = viewModel.aiThoughts,
                tokensUsed = viewModel.aiTokensUsed
            )
        }

        Box(
            modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
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
                    if (viewModel.videoUri != null) {
                        Button(
                            onClick = { showPromptDialog = true },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text("Add Effects")
                        }
                    }
                    if (viewModel.aiResponse != null) {
                        Button(
                            onClick = { showResponseDialog = true },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text("Show Response")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = viewModel.isProcessing, enter = fadeIn(), exit = fadeOut()
            ) {
                ProcessingOverlay()
            }
        }
    }

    @Composable
    fun ResponseDialogue(
        dismissDialog: () -> Unit,
        responseText: String,
        updateResponseAndApply: (String) -> Unit,
        thoughtsText: String?,
        tokensUsed: Int
    ) {
        val sheetState = rememberModalBottomSheetState()
        var selectedTab by remember { mutableIntStateOf(0) }
        var editedResponse by remember { mutableStateOf(responseText) }

        ModalBottomSheet(
            onDismissRequest = dismissDialog,
            sheetState = sheetState
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Response") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Thoughts") }
                )
            }
            Box(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> Column {
                        TextField(
                            value = editedResponse,
                            onValueChange = { editedResponse = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { updateResponseAndApply(editedResponse) }) {
                            Text("Apply")
                        }
                    }
                    1 -> Column {
                        Text("Used $tokensUsed tokens", fontStyle = FontStyle.Italic)
                        Spacer(Modifier.height(16.dp))
                        Text(thoughtsText ?: "No thought summary")
                    }
                }
            }
        }
    }

    @Composable
    fun AddEffectsDialogue(
        dismissDialog: () -> Unit,
        thinkingBudget: Int,
        updateThinkingBudget: (Int) -> Unit,
        submitPrompt: suspend (String) -> Unit,
        coroutineScope: CoroutineScope
    ) {
        var effectsText by remember { mutableStateOf("") }
        var dynamicThinkingEnabled by remember { mutableStateOf(thinkingBudget == -1) }

        AlertDialog(onDismissRequest = dismissDialog, title = { Text("Add Effects") }, text = {
            Column {
                TextField(
                    value = effectsText,
                    onValueChange = { effectsText = it },
                    label = { Text("Describe the effects you want") })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Thinking Budget", fontWeight = FontWeight.Bold)
                if (thinkingBudget != -1) {
                    Text("Current: $thinkingBudget")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dynamicThinkingEnabled, onCheckedChange = {
                            dynamicThinkingEnabled = it
                            if (it) {
                                updateThinkingBudget(-1)
                            } else {
                                updateThinkingBudget(8192)
                            }
                        })
                    Text("Dynamic Thinking")
                }
                Slider(
                    value = thinkingBudget.toFloat(),
                    onValueChange = { updateThinkingBudget(it.toInt()) },
                    valueRange = 128f..32768f,
                    enabled = !dynamicThinkingEnabled
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Easy task")
                    Text("Hard task")
                }
            }
        }, confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        submitPrompt(effectsText)
                    }
                    dismissDialog()
                }) {
                Text("OK")
            }
        }, dismissButton = {
            Button(onClick = dismissDialog) {
                Text("Cancel")
            }
        })
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
                    PlayerSurface(
                        player = player, modifier = Modifier.weight(1f)
                    )

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

    @Composable
    fun ProcessingOverlay() {
        Surface(
            color = Color.Black.copy(alpha = 0.7f), modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
            ) {
                CircularWavyProgressIndicator()
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}