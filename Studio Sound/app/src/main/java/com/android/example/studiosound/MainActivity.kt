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
package com.android.example.studiosound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.example.studiosound.ui.audio.AudioSplitScreen
import com.android.example.studiosound.ui.audio.AudioSplitViewModel
import com.android.example.studiosound.ui.theme.StudioSoundDemoTheme
import com.android.example.studiosound.ui.video.CameraScreen
import com.android.example.studiosound.ui.video.VideoResultScreen
import com.android.example.studiosound.ui.video.VideoResultViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // The studio UI is always dark, so the transparent system bars always get light icons.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)
    setContent {
      StudioSoundDemoTheme(darkTheme = true) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          StudioSoundNavHost()
        }
      }
    }
  }
}

private object Routes {
  const val STUDIO = "audio?${AudioSplitViewModel.ARG_SAMPLE}={${AudioSplitViewModel.ARG_SAMPLE}}"
  const val CAMERA = "camera"
  const val VIDEO = "video/{${VideoResultViewModel.ARG_FILE}}"

  fun video(fileName: String) = "video/$fileName"
}

@Composable
private fun StudioSoundNavHost() {
  val navController = rememberNavController()
  NavHost(navController = navController, startDestination = Routes.STUDIO) {
    composable(
      Routes.STUDIO,
      arguments = listOf(
        navArgument(AudioSplitViewModel.ARG_SAMPLE) {
          type = NavType.StringType
          nullable = true
          defaultValue = null
        },
      ),
    ) {
      AudioSplitScreen(
        onBack = if (navController.previousBackStackEntry != null) navController::safePopBackStack else null,
        onRecordVideo = { navController.navigate(Routes.CAMERA) },
      )
    }
    composable(Routes.CAMERA) {
      CameraScreen(
        onBack = navController::safePopBackStack,
        onRecordingFinished = { fileName -> navController.navigate(Routes.video(fileName)) },
      )
    }
    composable(
      Routes.VIDEO,
      arguments = listOf(navArgument(VideoResultViewModel.ARG_FILE) { type = NavType.StringType }),
    ) {
      VideoResultScreen(onBack = navController::safePopBackStack)
    }
  }
}

/** Avoids popping the start destination (and ending up on a blank screen) on double taps. */
private fun NavHostController.safePopBackStack() {
  if (previousBackStackEntry != null) popBackStack()
}
