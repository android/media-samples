package com.example.cameraxapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as GraphicsColor
import androidx.compose.ui.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.camera.core.CameraEffect.IMAGE_CAPTURE
import androidx.camera.core.CameraEffect.PREVIEW
import androidx.camera.core.CameraEffect.VIDEO_CAPTURE
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.UseCaseGroup
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.concurrent.futures.await
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.graphics.createBitmap

typealias LumaListener = (luma: Double) -> Unit

const val greenScreenBackgroundRemovalThreshold = 0.8
lateinit var mask: Bitmap
lateinit var bitmap: Bitmap

class MainActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private val surfaceRequest = mutableStateOf<SurfaceRequest?>(null)
    private val isRecording = mutableStateOf(false)
    private val isVideoRecordingInitializing = mutableStateOf(false)

    private val effectMode = mutableStateOf(EffectMode.NONE)
    private lateinit var greenScreenEffect: OverlayEffect

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

        greenScreenEffect = OverlayEffect(
            PREVIEW or IMAGE_CAPTURE or VIDEO_CAPTURE,
            5,
            Handler(Looper.getMainLooper()),
            {},
        )

        // Create a Paint object to draw the mask layer.
        val paint = Paint()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        paint.colorFilter = ColorMatrixColorFilter(
            floatArrayOf(
                0f, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

        greenScreenEffect.setOnDrawListener { frame ->
            if (!::mask.isInitialized || !::bitmap.isInitialized) {
                // Do not change the drawing if the frame doesn't match the analysis
                // result.
                return@setOnDrawListener true
            }

            // Clear the previously drawn frame.
            frame.overlayCanvas.drawColor(GraphicsColor.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // Draw the bitmap and mask, positioning the overlay in the bottom right corner.
            val rect = Rect(4*bitmap.width/3, bitmap.height/2, 7 * bitmap.width/3, 3*bitmap.height/2)
            frame.overlayCanvas.drawBitmap(bitmap, null, rect, null)
            frame.overlayCanvas.drawBitmap(mask, null, rect, paint)

            true
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

    private fun toggleGreenScreenEffect() {
        effectMode.value = if (effectMode.value == EffectMode.NONE) {
            EffectMode.GREEN_SCREEN
        } else {
            EffectMode.NONE
        }
        startCamera()
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

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()

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
                if (effectMode.value == EffectMode.NONE) {
                    cameraProvider.bindToLifecycle(
                        this@MainActivity, cameraSelector,
                        preview, imageCapture, videoCapture
                    )
                } else if (effectMode.value == EffectMode.GREEN_SCREEN) {
                    imageAnalyzer.setAnalyzer(
                        ContextCompat.getMainExecutor(application),
                        SelfieSegmentationAnalyzer(),
                    )

                    // Concurrent camera setup for green screen effect
                    var primaryCameraSelector: CameraSelector? = null
                    var secondaryCameraSelector: CameraSelector? = null

                    // Iterate through available concurrent camera infos to find suitable primary
                    // (front-facing) and secondary (back-facing) cameras.
                    for (cameraInfos in cameraProvider.availableConcurrentCameraInfos) {
                        primaryCameraSelector = cameraInfos.first {
                            it.lensFacing == CameraSelector.LENS_FACING_FRONT
                        }.cameraSelector
                        secondaryCameraSelector = cameraInfos.first {
                            it.lensFacing == CameraSelector.LENS_FACING_BACK
                        }.cameraSelector

                        if (primaryCameraSelector == null || secondaryCameraSelector == null) {
                            // If either a primary or secondary selector wasn't found, reset both
                            // to move on to the next list of CameraInfos.
                            primaryCameraSelector = null
                            secondaryCameraSelector = null
                        } else {
                            // If both primary and secondary camera selectors were found, we can
                            // conclude the search.
                            break
                        }
                    }

                    if (primaryCameraSelector != null && secondaryCameraSelector != null) {
                        val useCaseGroupBuilder = UseCaseGroup.Builder()
                        useCaseGroupBuilder.addUseCase(preview)
                        useCaseGroupBuilder.addUseCase(imageCapture!!)
                        useCaseGroupBuilder.addUseCase(videoCapture!!)
                        useCaseGroupBuilder.addEffect(greenScreenEffect)

                        val segmentedSelfieUseCaseGroupBuilder = UseCaseGroup.Builder()
                            .addUseCase(imageAnalyzer)

                        val primary = ConcurrentCamera.SingleCameraConfig(
                            primaryCameraSelector,
                            segmentedSelfieUseCaseGroupBuilder.build(),
                            this@MainActivity,
                        )

                        val secondary = ConcurrentCamera.SingleCameraConfig(
                            secondaryCameraSelector,
                            useCaseGroupBuilder.build(),
                            this@MainActivity,
                        )

                        val concurrentCamera = cameraProvider.bindToLifecycle(
                            listOf(primary, secondary),
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            this@MainActivity, cameraSelector,
                            preview, imageCapture, videoCapture
                        )
                    }
                }

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
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
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { toggleGreenScreenEffect() },
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    val modifier = Modifier.size(100.dp).let {
                        if (effectMode.value == EffectMode.GREEN_SCREEN) {
                            it.border(2.dp, Color.White, RoundedCornerShape(5.dp))
                        } else {
                            it
                        }
                    }
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier,
                        Color.White
                    )
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

private class SelfieSegmentationAnalyzer : ImageAnalysis.Analyzer {

    val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        .enableRawSizeMask()
        .build()
    val selfieSegmenter = Segmentation.getClient(options)
    lateinit var maskBuffer: ByteBuffer
    lateinit var maskBitmap: Bitmap

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            selfieSegmenter.process(image)
                .addOnSuccessListener { results ->
                    // Get foreground probabilities for each pixel. Since ML Kit returns this
                    // in a byte buffer with each 4 bytes representing a float, convert it to
                    // a FloatBuffer for easier use.
                    val maskProbabilities = results.buffer.asFloatBuffer()

                    // Initialize our mask buffer and intermediate mask bitmap
                    if (!::maskBuffer.isInitialized) {
                        maskBitmap =
                            createBitmap(results.width, results.height, Bitmap.Config.ALPHA_8)
                        maskBuffer = ByteBuffer.allocateDirect(
                            maskBitmap.allocationByteCount,
                        )
                    }
                    maskBuffer.rewind()

                    // Convert the mask to an A8 image from the mask probabilities.
                    // We use a line buffer hear to optimize reads from the FloatBuffer.
                    val lineBuffer = FloatArray(results.width)
                    for (y in 0..<results.height) {
                        maskProbabilities.get(lineBuffer)
                        for (point in lineBuffer) {
                            maskBuffer.put(
                                if (point > greenScreenBackgroundRemovalThreshold) {
                                    255.toByte()
                                } else {
                                    0
                                },
                            )
                        }
                    }
                    maskBuffer.rewind()
                    // Convert the mask buffer to a Bitmap so we can easily rotate and
                    // mirror.
                    maskBitmap.copyPixelsFromBuffer(maskBuffer)

                    // Transformation matrix to mirror and rotate our bitmaps
                    val matrix = Matrix().apply {
                        setScale(-1f, 1f)
                    }

                    // Mirror the ImageProxy
                    bitmap = Bitmap.createBitmap(
                        imageProxy.toBitmap(),
                        0,
                        0,
                        imageProxy.width,
                        imageProxy.height,
                        matrix,
                        false,
                    )

                    // Rotate and mirror the mask. When the rotation is 90 or 270, we need
                    // to swap the width and height.
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val (rotWidth, rotHeight) = when (rotation) {
                        90, 270 ->
                            Pair(maskBitmap.height, maskBitmap.width)

                        else ->
                            Pair(maskBitmap.width, maskBitmap.height)
                    }
                    mask = Bitmap.createBitmap(
                        maskBitmap,
                        0,
                        0,
                        rotWidth,
                        rotHeight,
                        matrix.apply { preRotate(-rotation.toFloat()) },
                        false,
                    )
                }
                .addOnCompleteListener {
                    // Final cleanup. Close imageProxy for next analysis frame.
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

enum class EffectMode {
    NONE,
    GREEN_SCREEN
}