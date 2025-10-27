package com.example.pastport.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pastport.R
import com.example.pastport.ui.components.OptionRadioButton
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

const val TAG = "PastPortLog"


@Composable
@Preview
fun ImageTransformer() {

    val context = LocalContext.current
    var inputBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var expanded by remember { mutableStateOf(false) }
    var submenuExpanded by remember { mutableStateOf(false) }
    var counter by remember { mutableIntStateOf(0) }

    var promptBuilder by remember { mutableStateOf(PromptBuilder()) }
    var promptText by remember { mutableStateOf("") }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // Create a launcher to pick images
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.i(TAG, "Image URI: $uri")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputBitmap = BitmapFactory.decodeStream(inputStream)
            }
            resultBitmap = null
            isUploading = false
            statusMessage = null
        }
    }

    Text("$counter")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 50.dp, start = 25.dp, end = 25.dp)
    ) {
        Text(
            text = "PastPort ✨", style = TextStyle(
                fontFamily = FontFamily.Cursive,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            ), modifier = Modifier.padding(5.dp)
        )

        // BEFORE pane
        Box(
            modifier = Modifier
                .weight(0.4f)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                    ), contentAlignment = Alignment.Center
            ) {
                if (inputBitmap != null) {
                    Image(
                        bitmap = inputBitmap!!.asImageBitmap(),
                        contentDescription = "Input image",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Upload image",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = "Before", style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary
                ), modifier = Modifier.padding(12.dp)
            )
        }

        // AFTER pane
        Box(
            modifier = Modifier
                .weight(0.4f)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                    ), contentAlignment = Alignment.Center
            ) {
                if (resultBitmap != null) {
                    Image(
                        bitmap = resultBitmap!!.asImageBitmap(),
                        contentDescription = "Edited result",
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isUploading) {
                    Text(
                        "Processing...",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Result will appear here",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = "After", style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary
                ), modifier = Modifier.padding(12.dp)
            )
        }

        // Prompt pane
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(0.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var customPromptText by remember { mutableStateOf("") }
            TextField(
                value = customPromptText,
                onValueChange = { customPromptText = it },
                label = {
                    Text(
                        "Enter prompt...", style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(0.3f)
                    .padding(top = 10.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.weight(0.4f), horizontalArrangement = Arrangement.Center) {

                Button(
                    onClick = {
                        pickPhotoLauncher.launch("image/*")
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.upload),
                        contentDescription = "Upload Image",
                        modifier = Modifier
                            .size(25.dp)
                            .padding(2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Button(
                    onClick = {
                        expanded = true
                        counter++
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.pencil),
                        contentDescription = "Edit Options",
                        modifier = Modifier.size(25.dp)
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.width(130.dp)
                    ) {
                        OptionRadioButton(
                            label = "Restore", selected = promptBuilder.get("restore"), onClick = {
                                counter++
                                promptBuilder.toggle("restore")
                            })
                        OptionRadioButton(
                            label = "Colorize",
                            selected = promptBuilder.get("colorize"),
                            onClick = {
                                counter++
                                promptBuilder.toggle("colorize")
                            })
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    counter++
                                    promptBuilder.toggle("artistic")
                                    submenuExpanded = true
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Open Submenu",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Artistic")
                        }
                    }

                    // Submenu for Artistic Option
                    DropdownMenu(
                        expanded = submenuExpanded,
                        onDismissRequest = { submenuExpanded = false },
                        modifier = Modifier.width(130.dp)
                    ) {
                        val artisticModes = listOf("Japanese", "Persian", "European")
                        artisticModes.forEach { mode ->
                            OptionRadioButton(
                                label = mode,
                                selected = promptBuilder.get(mode.lowercase(Locale.ROOT)),
                                onClick = {
                                    counter++
                                    promptBuilder.toggleArtisticMode(mode.lowercase(Locale.ROOT))
                                },
                            )
                        }
                    }

                }

                Spacer(modifier = Modifier.width(20.dp))

                Button(
                    onClick = {
                        isUploading = true
                        resultBitmap = null
                        statusMessage = "Processing..."

                        Log.i(TAG, " --------------- START --------------- ")

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                                    .generativeModel(
                                        modelName = "gemini-2.5-flash-image-preview",
                                        generationConfig = generationConfig {
                                            responseModalities = listOf(
                                                ResponseModality.TEXT, ResponseModality.IMAGE
                                            )
                                        })

                                val prompt = content {
                                    image(inputBitmap!!)
                                    promptText =
                                        if (customPromptText != "") customPromptText else promptBuilder.build()
                                    Log.i(TAG, "Prompt: $promptText")
                                    text(promptText)
                                }

                                val generatedImageAsBitmap =
                                    model.generateContent(prompt).candidates.first().content.parts.filterIsInstance<ImagePart>()
                                        .firstOrNull()?.image

                                if (generatedImageAsBitmap == null) {
                                    Log.e("NanoBanana", "No image generated")
                                    statusMessage = "No image generated"
                                }
                                resultBitmap = generatedImageAsBitmap

                                statusMessage = "Done"
                                Log.i(TAG, " --------------- END --------------- ")
                            } catch (e: Exception) {
                                Log.e("NanoBanana", "upload failed", e)
                                statusMessage = "Upload failed: ${e.message}"
                            } finally {
                                promptText = ""
                                promptBuilder = PromptBuilder()
                                isUploading = false
                            }
                        }

                        Log.i(TAG, "Status: $statusMessage")
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.next),
                        contentDescription = "Upload Image",
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}