package com.example.cameraxapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.camera.core.SurfaceRequest
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.concurrent.futures.await
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private val surfaceRequest = mutableStateOf<SurfaceRequest?>(null)
    private val isRecording = mutableStateOf(false)
    private val isVideoRecordingInitializing = mutableStateOf(false)

    @OptIn(PublicPreviewAPI::class)
//    val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
//        modelName = "gemini-live-2.5-flash-preview",
//        generationConfig = liveGenerationConfig {
//            responseModality = ResponseModality.AUDIO
//            speechConfig = SpeechConfig(voice = Voice("FENRIR"))
//        },
//        systemInstruction = systemInstruction,
//        tools = listOf(cameraControlTool))
    @OptIn(PublicPreviewAPI::class)
    private var session : MutableState<LiveSession?> = mutableStateOf(null)

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            CameraApp(
                surfaceRequest = surfaceRequest.value,
                onTakePhoto = { takePhoto() },
                onCaptureVideo = { captureVideo() },
                isRecording = isRecording.value
            )
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        isVideoRecordingInitializing.value = true

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecording.value = true
                        isVideoRecordingInitializing.value = false
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        isRecording.value = false
                    }
                }
            }
    }

    private fun startCamera() {
        lifecycleScope.launch {
            val cameraProvider = ProcessCameraProvider.getInstance(applicationContext).await()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider { request ->
                        surfaceRequest.value = request
                    }
                }

            imageCapture = ImageCapture.Builder().build()

            /*
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }
            */

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this@MainActivity, cameraSelector, preview, imageCapture, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun startVoiceControl() {
        lifecycleScope.launch {
//            session.value = model.connect()
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@launch
            }
            session.value?.startAudioConversation(::functionCallHandler)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun stopVoiceControl() {
        lifecycleScope.launch {
            session.value?.close()
            session.value = null
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun toggleVoiceControl() {
        if (session.value == null) {
            startVoiceControl()
        } else {
            stopVoiceControl()
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    fun functionCallHandler(functionCall: FunctionCallPart): FunctionResponsePart {
        Log.d(TAG, "functionCallHandler: ${functionCall}")
        Log.d(TAG, "functionCall Name: ${functionCall.name}")
        Log.d(TAG, "functionCall isThought: ${functionCall.isThought}")
        return when (functionCall.name) {
            "takePicture" -> {
                takePhoto()
                val response = JsonObject(
                    mapOf(
                        "success" to JsonPrimitive(true),
                        "message" to JsonPrimitive("Initiated photo capture.")
                    )
                )
                FunctionResponsePart(functionCall.name, response)
            }
            "startVideoCapture" -> {
                var response: JsonObject? = null
                if (isRecording.value == false) {
                    captureVideo()
                    response = JsonObject(
                        mapOf(
                            "success" to JsonPrimitive(true),
                            "message" to JsonPrimitive("Started video capture.")
                        )
                    )
                } else {
                    response = JsonObject(
                        mapOf(
                            "success" to JsonPrimitive(false),
                            "message" to JsonPrimitive("Video capture already in progress.")
                        )
                    )
                }
                FunctionResponsePart(functionCall.name, response)
            }
            "stopVideoCapture" -> {
                var response: JsonObject? = null
                if (isRecording.value == true) {
                    captureVideo()
                    response = JsonObject(
                        mapOf(
                            "success" to JsonPrimitive(true),
                            "message" to JsonPrimitive("Initiated video save.")
                        )
                    )
                } else {
                    response = JsonObject(
                        mapOf(
                            "success" to JsonPrimitive(false),
                            "message" to JsonPrimitive("There's no video capture in progress.")
                        )
                    )
                }
                FunctionResponsePart(functionCall.name, response)
            }
            else -> {
                val response = JsonObject(
                    mapOf(
                        "error" to JsonPrimitive("Unknown function: ${functionCall.name}")
                    )
                )
                FunctionResponsePart(functionCall.name, response)
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    @OptIn(PublicPreviewAPI::class)
    @Composable
    fun CameraApp(
        surfaceRequest: SurfaceRequest?,
        onTakePhoto: () -> Unit,
        onCaptureVideo: () -> Unit,
        isRecording: Boolean
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (surfaceRequest != null) {
                CameraXViewfinder(
                    modifier = Modifier.fillMaxSize(),
                    implementationMode = ImplementationMode.EXTERNAL,
                    surfaceRequest = surfaceRequest,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = { toggleVoiceControl() },
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    val modifier = Modifier.size(100.dp).let {
                        if (session.value != null) {
                            it.border(2.dp, Color.White, RoundedCornerShape(5.dp))
                        } else {
                            it
                        }
                    }
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        modifier,
                        Color.White
                    )
                }
                if (session.value != null) {
                    if (session.value?.isAudioConversationActive() == true) {
                        Icon(
                            Icons.Filled.NoiseAware,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            Color.White
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(50.dp)
            ) {
                Button(
                    onClick = onTakePhoto,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.size(110.dp)
                ) {
                    Text(
                        textAlign = TextAlign.Center,
                        text = stringResource(R.string.take_photo)
                    )
                }
                Button(
                    onClick = onCaptureVideo,
                    shape = RoundedCornerShape(0.dp),
                    enabled = !isVideoRecordingInitializing.value,
                    modifier = Modifier.size(110.dp)
                ) {
                    val videoCaptureButtonText = if (isRecording) {
                        stringResource(R.string.stop_video_capture)
                    } else {
                        stringResource(R.string.start_video_capture)
                    }
                    Text(
                        textAlign = TextAlign.Center,
                        text = videoCaptureButtonText
                    )
                }
            }
        }
    }
}

private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {

        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        listener(luma)

        image.close()
    }
}

val systemInstruction = content {
    text("You are a helpful camera assistant. Your main role is to take photos and start/stop video capture. If the user asks you for anything non-camera-related, you should redirect them to ask you something you can help with instead in the context of controlling the camera. If you're not 100% sure that a user command maps to a provided camera function, then you should respond back to the user asking them to clarify their command.")}

val takePictureFunctionDeclaration = FunctionDeclaration(
    name = "takePicture",
    description = "Use the app's camera to take a photo that will be saved to the user's device. This function is the equivalent of the user pressing the 'Take Photo' button.",
    parameters = emptyMap()
)

val startVideoCaptureFunctionDeclaration = FunctionDeclaration(
    name = "startVideoCapture",
    description = "Start a video recording using the app's camera. This function is the equivalent of the user pressing the 'Start Video Capture' button.",
    parameters = emptyMap()
)

val stopVideoCaptureFunctionDeclaration = FunctionDeclaration(
    name = "stopVideoCapture",
    description = "Stop the current video recording. This function is the equivalent of the user pressing the 'Stop Video Capture' button.",
    parameters = emptyMap()
)

val cameraControlTool = Tool.functionDeclarations(listOf(takePictureFunctionDeclaration, startVideoCaptureFunctionDeclaration, stopVideoCaptureFunctionDeclaration))
