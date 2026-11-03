/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.imagesuperresolution

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.android.imagesuperresolution.ui.theme.ImageSuperResolutionTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Enum to represent the different postures of the device
enum class DevicePosture {
    NORMAL,
    BOOK_MODE, // Vertical fold
    TABLETOP_MODE // Horizontal fold
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The editor is always dark, so the transparent system bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        val viewModel: EnhancementViewModel by viewModels()

        var devicePosture by mutableStateOf(DevicePosture.NORMAL)

        // Create a new WindowInfoTracker instance
        val windowInfoTracker = WindowInfoTracker.getOrCreate(this)

        // Observe the window layout info in a lifecycle-aware manner
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                windowInfoTracker.windowLayoutInfo(this@MainActivity)
                    .map { windowLayoutInfo ->
                        val foldingFeature = windowLayoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                        when {
                            foldingFeature?.isSeparating == true && foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL ->
                                DevicePosture.BOOK_MODE
                            foldingFeature?.isSeparating == true && foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL ->
                                DevicePosture.TABLETOP_MODE
                            else ->
                                DevicePosture.NORMAL
                        }
                    }
                    .collect { value -> devicePosture = value }
            }
        }

        setContent {
            ImageSuperResolutionTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ImageComparisonScreen(
                        devicePosture = devicePosture,
                        enhancementViewModel = viewModel
                    )
                }
            }
        }
    }
}
