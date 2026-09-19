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
import java.io.File
import java.util.UUID

/** Only previews/captures locally. A simulated live never sends camera frames anywhere. */
@Composable
fun CameraCapture(
    front: Boolean,
    flash: Boolean,
    captureRequest: Int,
    onCaptured: (Media) -> Unit,
    modifier: Modifier = Modifier,
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
    val callback by rememberUpdatedState(onCaptured)
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
                        val selector =
                            if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        provider!!.unbindAll()
                        provider!!.bindToLifecycle(owner, selector, preview, photo)
                        capture = photo; problem = null
                    } catch (_: Exception) {
                        problem = "Camera unavailable. You can still choose media from your device."
                    }
                }, ContextCompat.getMainExecutor(context))
                onDispose { disposed = true; provider?.unbindAll(); capture = null }
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
}
