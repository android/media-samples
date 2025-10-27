package com.example.pastport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pastport.ui.screen.ImageTransformer
import com.example.pastport.ui.theme.PastPortTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastPortTheme {
                ImageTransformer()
            }
        }
    }
}