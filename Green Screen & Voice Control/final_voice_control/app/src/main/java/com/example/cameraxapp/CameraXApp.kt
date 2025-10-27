package com.example.cameraxapp

import android.app.Application
import com.google.firebase.FirebaseApp

class CameraXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}