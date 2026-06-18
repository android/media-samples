package com.example.videoediting

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseStorageRepository(private val storage: FirebaseStorage) {
    
    suspend fun uploadFile(uri: Uri): String? {
        return try {
            val storageRef = storage.reference
            val videoRef = storageRef.child("videos/${UUID.randomUUID()}.mp4")
            val uploadTask = videoRef.putFile(uri)
            uploadTask.await() // Wait for the upload to complete
            val path = videoRef.path
            val bucket = videoRef.bucket
            val storageUrl = "gs://$bucket$path"
            Log.d("FirebaseStorageRepo", "File uploaded successfully: $storageUrl")
            storageUrl
        } catch (e: Exception) {
            Log.e("FirebaseStorageRepo", "Error uploading file", e)
            null
        }
    }
}
