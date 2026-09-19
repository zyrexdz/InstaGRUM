package com.instagrum.local.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.instagrum.local.model.Media
import com.instagrum.local.model.MediaKind
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

const val MAX_RECORDING_MILLIS = 60_000L

@Composable
fun CameraCapture(
    front: Boolean,
    flash: Boolean,
    captureRequest: Int,
    onCaptured: (Media) -> Unit,
    modifier: Modifier = Modifier,
    recording: Boolean = false,
    onRecordingFinished: (Media) -> Unit = {},
    onRecordingProgress: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var problem by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType =
            PreviewView.ScaleType.FILL_CENTER
        }
    }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var movie by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var session by remember { mutableStateOf<Recording?>(null) }
    val callback by rememberUpdatedState(onCaptured)
    val recorded by rememberUpdatedState(onRecordingFinished)
    val progress by rememberUpdatedState(onRecordingProgress)
    Box(modifier.background(Color(0xFF171717)), contentAlignment = Alignment.Center) {
        if (permitted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            DisposableEffect(owner, front) {
                var disposed = false
                var provider: ProcessCameraProvider? = null
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (!disposed) try {
                        provider = future.get()
                        val preview =
                            Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val photo =
                            ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                        val recorder = Recorder.Builder().setQualitySelector(
                            QualitySelector.from(
                                Quality.HD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                            )
                        ).build()
                        val video = VideoCapture.withOutput(recorder)
                        val selector =
                            if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        provider!!.unbindAll()

                        movie = try {
                            provider!!.bindToLifecycle(owner, selector, preview, photo, video); video
                        } catch (_: Exception) {
                            provider!!.unbindAll()
                            provider!!.bindToLifecycle(owner, selector, preview, photo); null
                        }
                        capture = photo; problem = null
                    } catch (_: Exception) {
                        problem = "Camera unavailable. You can still choose media from your device."
                    }
                }, ContextCompat.getMainExecutor(context))
                onDispose {
                    disposed = true; session?.stop(); session = null
                    provider?.unbindAll(); capture = null; movie = null
                }
            }
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(36.dp)) {
            Text("Capture a moment", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                "Camera access is optional. Photos stay on this device.",
                color = Color.White.copy(alpha = .6f),
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Enable camera") }
        }
        problem?.let { Text(it, color = Color.White, modifier = Modifier.padding(36.dp)) }
    }
    LaunchedEffect(captureRequest) {
        if (captureRequest > 0) {
            val photo = capture
            if (photo == null) {
                problem = "Enable the camera or choose a photo from your device."; return@LaunchedEffect
            }
            photo.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            val folder = File(context.filesDir, "media").apply { mkdirs() }
            val file = File(folder, "${UUID.randomUUID()}.jpg")
            photo.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        callback(Media(path = Uri.fromFile(file).toString()))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete(); problem = "Couldn't capture that photo. Please try again."
                    }
                })
        }
    }
    LaunchedEffect(recording) {
        if (!recording) {
            session?.stop(); session = null; return@LaunchedEffect
        }
        val video = movie
        if (video == null) {
            problem = "Enable the camera or choose a video from your device."; return@LaunchedEffect
        }
        val folder = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(folder, "${UUID.randomUUID()}.mp4")
        val pending = video.output.prepareRecording(context, FileOutputOptions.Builder(file).build())

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) pending.withAudioEnabled()
        session = try {
            pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> progress(
                        (event.recordingStats.recordedDurationNanos / 1_000_000f / MAX_RECORDING_MILLIS)
                            .coerceIn(0f, 1f)
                    )

                    is VideoRecordEvent.Finalize -> {
                        session = null; progress(0f)
                        if (event.hasError()) {
                            file.delete(); problem = "Couldn't record that video. Please try again."
                        } else recorded(Media(path = Uri.fromFile(file).toString(), kind = MediaKind.VIDEO))
                    }

                    else -> Unit
                }
            }
        } catch (_: Exception) {
            file.delete(); problem = "Couldn't record that video. Please try again."; null
        }
        delay(MAX_RECORDING_MILLIS)
        session?.stop(); session = null
    }
}
