package com.instagrum.local.ui

import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.instagrum.local.data.MediaStore
import com.instagrum.local.model.Media
import com.instagrum.local.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalMediaLoader = staticCompositionLocalOf<ImageLoader> { error("Media loader is not installed") }

@Composable
fun MediaProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val loader =
        remember(context) { ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    CompositionLocalProvider(LocalMediaLoader provides loader, content = content)
}

@Composable
fun MediaContent(
    media: Media,
    modifier: Modifier = Modifier,
    showPlay: Boolean = false,
    playVideo: Boolean = false,
    paused: Boolean = false,
    controls: Boolean = true,
    description: String? = null,
) {
    val context = LocalContext.current
    val video = media.kind != MediaKind.IMAGE && media.path.isNotBlank()
    val source = media.path.ifBlank { "file:///android_asset/photos/${media.artwork.mod(9)}.jpg" }

    val uri =
        remember(source) { Uri.parse(source).takeIf { it.scheme in listOf("file", "content", "android.resource") } }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            uri == null -> Text(
                "Only local media is supported",
                color = Color.White,
                modifier = Modifier.padding(20.dp)
            )

            video && playVideo -> LocalVideo(uri, paused, controls)
            else -> {
                SubcomposeAsyncImage(
                    model = remember(source, video) {
                        ImageRequest.Builder(context).data(uri).crossfade(160).apply {
                            if (video) decoderFactory(VideoFrameDecoder.Factory())
                        }.build()
                    },
                    imageLoader = LocalMediaLoader.current,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
                    error = {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { Text("Media unavailable", color = Color.White) }
                    },
                )
                if (video || showPlay) Icon(
                    Icons.Default.PlayArrow,
                    "Video",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun LocalVideo(uri: Uri, paused: Boolean, controls: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var userPaused by rememberSaveable(uri.toString()) { mutableStateOf(false) }
    var muted by rememberSaveable(uri.toString()) { mutableStateOf(true) }
    var problem by remember(uri) { mutableStateOf<String?>(null) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    problem = "This video cannot be played on this device."
                }
            })
            prepare()
        }
    }
    DisposableEffect(lifecycle, player) {
        val observer =
            LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    LaunchedEffect(paused, foreground, userPaused, muted, player) {
        player.playWhenReady = foreground && !paused && !userPaused
        player.volume = if (muted) 0f else 1f
    }
    AndroidView(
        factory = { TextureView(it).also(player::setVideoTextureView) },
        modifier = Modifier.fillMaxSize(),
    )
    if (problem != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            problem!!,
            color = Color.White,
            modifier = Modifier.padding(20.dp)
        )
    }
    if (controls) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Row(Modifier.padding(8.dp)) {
            IconButton(onClick = {
                userPaused = !userPaused
            }) {
                Icon(
                    if (userPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    "Pause or play video",
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                muted = !muted
            }) {
                Icon(
                    if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    "Toggle video sound",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun PrivateMediaPicker(
    kind: MediaKind,
    onSelected: (Media) -> Unit,
    label: String = "Import from device",
    compact: Boolean = false,
    mixed: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentKind by rememberUpdatedState(kind)
    val callback by rememberUpdatedState(onSelected)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            val selectedKind = currentKind
            scope.launch {
                try {
                    val actualKind = if (mixed && context.contentResolver.getType(uri).orEmpty()
                            .startsWith("video/")
                    ) MediaKind.VIDEO else selectedKind
                    callback(MediaStore.import(context, uri, actualKind))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Import failed. Choose another local file."
                } finally {
                    importing = false
                }
            }
        }
    }
    val launch = {
        error = null
        launcher.launch(
            if (mixed) arrayOf(
                "image/*",
                "video/*"
            ) else arrayOf(if (kind == MediaKind.IMAGE) "image/*" else "video/*")
        )
    }
    if (compact) {
        IconButton(onClick = launch, enabled = !importing, modifier = Modifier.size(48.dp)) {
            if (importing) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Default.PhotoLibrary, "Open gallery", Modifier.size(28.dp), tint = Color.White)
        }
        error?.let { message ->
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("Couldn't import media") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
        }
    } else Column {
        OutlinedButton(onClick = launch, enabled = !importing) {
            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (importing) "Copying privately…" else label)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun GalleryButton(kind: MediaKind, onSelected: (Media) -> Unit, mixed: Boolean = false) {
    PrivateMediaPicker(kind, onSelected, compact = true, mixed = mixed)
}
