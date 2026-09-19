package com.instagrum.local.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.instagrum.local.model.*

private fun formatStoryTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 1000
    return when {
        diff < 60 -> "${diff}s"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> "${diff / 86400}d"
    }
}

@Composable
private fun CrossShareIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.31f
        val stroke = Stroke(width = 1.6.dp.toPx())
        // Left bubble
        drawCircle(
            color = Color.White,
            radius = r,
            center = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.5f),
            style = stroke
        )
        // Little tail for left bubble
        val tail = Path().apply {
            moveTo(w * 0.22f, h * 0.65f)
            lineTo(w * 0.12f, h * 0.82f)
            lineTo(w * 0.32f, h * 0.72f)
        }
        drawPath(tail, color = Color.White, style = stroke)
        // Right bubble
        drawCircle(
            color = Color.White,
            radius = r,
            center = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.5f),
            style = stroke
        )
    }
}

@Composable
private fun MentionIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "@",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DownwardCaret(modifier: Modifier = Modifier, color: Color = Color(0xFF282828)) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
private fun StoryActionItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewer(state: AppState, storyId: String, onAction: (Action) -> Unit) {
    val story = state.stories.find { it.id == storyId }
    if (story == null) {
        LaunchedEffect(storyId) { onAction(Action.OpenStory(null)) }; return
    }
    val highlight = state.session.storyHighlight
    val sequence = if (highlight.isNotBlank()) state.stories.filter { it.highlight == highlight }
    else state.stories.filter { it.expiresAt > System.currentTimeMillis() || it.id == storyId }
    val latestSequence by rememberUpdatedState(sequence)
    val dispatch by rememberUpdatedState(onAction)
    val index = sequence.indexOfFirst { it.id == storyId }
    var held by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var reply by rememberSaveable(storyId) { mutableStateOf("") }
    var editingReply by remember { mutableStateOf(false) }
    var insights by rememberSaveable(storyId) { mutableStateOf(false) }
    var moreOptions by remember { mutableStateOf(false) }
    var progress by rememberSaveable(storyId) { mutableFloatStateOf(state.session.storyProgress) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val current = state.session.openStoryId == storyId
    val paused = held || drag != 0f || insights || moreOptions || editingReply || reply.isNotBlank() || !foreground || !current
    val threshold = with(LocalDensity.current) { 85.dp.toPx() }
    val scale by animateFloatAsState(
        if (drag > 0) (1f - drag / (threshold * 8)).coerceIn(.86f, 1f) else 1f,
        label = "story-dismiss-scale"
    )

    fun next(offset: Int) {
        val list = latestSequence
        val position = list.indexOfFirst { it.id == storyId }
        val target = list.getOrNull(position + offset)?.id
        if (offset < 0 && target == null) {
            progress = 0f; dispatch(Action.StoryProgress(0f))
        } else dispatch(Action.OpenStory(target, highlight = highlight))
    }
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler { onAction(Action.OpenStory(null)) }
    LaunchedEffect(storyId, paused) {
        if (paused) {
            if (current) dispatch(Action.StoryProgress(progress)); return@LaunchedEffect
        }
        var previous = withFrameNanos { it }
        var checkpoint = progress
        while (progress < 1f) {
            val frame = withFrameNanos { it }
            val duration = if (story.media.kind == MediaKind.IMAGE) 7.0 else 15.0
            progress = (progress + ((frame - previous) / 1e9 / duration).toFloat()).coerceAtMost(1f)
            previous = frame
            if (progress - checkpoint >= .035f) {
                checkpoint = progress; dispatch(Action.StoryProgress(progress))
            }
        }
        next(1)
    }
    InstaTheme(true) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().imePadding().testTag("storyViewer")
        ) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationY = drag.coerceAtLeast(0f) * .25f }
                    .clip(RoundedCornerShape(12.dp))
            ) {
                MediaContent(story.media, Modifier.fillMaxSize(), playVideo = true, paused = paused, controls = false)
                StoryDecoration(story.overlay, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = .35f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = .35f)
                            )
                        )
                    )
                )
                Box(Modifier.fillMaxSize().testTag("storyGestures").pointerInput(storyId) {
                    detectTapGestures(onPress = {
                        held = true; try {
                        tryAwaitRelease()
                    } finally {
                        held = false
                    }
                    }, onLongPress = {}, onTap = { next(if (it.x < size.width * .3f) -1 else 1) })
                }.pointerInput(storyId) {
                    detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f }, onDragEnd = {
                        if (drag > threshold) dispatch(Action.OpenStory(null)) else if (drag < -threshold) insights =
                            true
                        drag = 0f
                    }, onVerticalDrag = { change, amount -> change.consume(); drag += amount })
                })
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                        sequence.forEachIndexed { i, _ ->
                            LinearProgressIndicator(
                                progress = {
                                    when {
                                        i < index -> 1f; i == index -> progress; else -> 0f
                                    }
                                },
                                modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(2.dp)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = .35f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(34.dp)) {
                            AvatarFromProfile(state.profile, Modifier.size(34.dp))
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black, CircleShape)
                                    .border(1.dp, Color.Black, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            state.profile.username,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatStoryTime(story.createdAt),
                            color = Color.White.copy(alpha = .65f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.weight(1f))
                        if (story.overlay.closeFriends) Icon(
                            Icons.Default.Stars,
                            "Close friends story",
                            tint = Color(0xFF64C866),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (story.caption.isNotBlank()) Text(
                        story.caption,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (editingReply) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = { reply = it.take(500) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("storyReply")
                            .onFocusChanged { editingReply = it.isFocused },
                        placeholder = { Text("Say something...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                        maxLines = 2,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color(0xFF555555),
                            focusedBorderColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (reply.isNotBlank()) {
                                dispatch(Action.StoryReply(story.id, reply))
                                reply = ""
                            }
                            editingReply = false
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send story reply", tint = Color.White)
                    }
                }
            } else {
                Text(
                    text = "Say something...",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingReply = true }
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoryActionItem(
                    label = "Activity",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E))
                                .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarFromProfile(state.profile, Modifier.size(22.dp))
                        }
                    },
                    onClick = { insights = true },
                    modifier = Modifier.semantics { contentDescription = "Story insights" }
                )

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StoryActionItem(
                        label = "Share on...",
                        icon = { CrossShareIcon(Modifier.size(24.dp)) },
                        onClick = { /* share on */ }
                    )
                    StoryActionItem(
                        label = "Send",
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(rotationZ = -25f)
                            )
                        },
                        onClick = { /* send */ }
                    )
                    StoryActionItem(
                        label = "Mention",
                        icon = { MentionIcon(Modifier.size(24.dp)) },
                        onClick = { /* mention */ }
                    )
                    StoryActionItem(
                        label = "More",
                        icon = {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = { moreOptions = true }
                    )
                }
            }
        }
        if (insights) StoryInsightsPanel(state, story.id, onAction) { insights = false }
        if (moreOptions) {
            ModalBottomSheet(
                onDismissRequest = { moreOptions = false },
                containerColor = Color(0xFF202020),
                contentColor = Color.White
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        "Delete story",
                        color = Color(0xFFED4956),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                moreOptions = false
                                dispatch(Action.DeleteStory(story.id))
                                dispatch(Action.OpenStory(null))
                            }
                            .padding(vertical = 14.dp)
                    )
                    HorizontalDivider(color = Color(0xFF333333))
                    Text(
                        "Save to highlight",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                moreOptions = false
                                insights = true
                            }
                            .padding(vertical = 14.dp)
                    )
                    HorizontalDivider(color = Color(0xFF333333))
                    Text(
                        "Copy link",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { moreOptions = false }
                            .padding(vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StoryInsightsPanel(state: AppState, initialId: String, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(initialId) }
    var highlightEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf<FakePerson?>(null) }
    val story = state.stories.find { it.id == selectedId } ?: return

    val viewerCount = story.views.coerceAtLeast(story.viewers.size.coerceAtLeast(1).toLong())
    val displayedViewers = remember(story.viewers, story.views) {
        if (story.viewers.isNotEmpty()) {
            story.viewers
        } else {
            listOf(
                StoryVisit(
                    person = FakePerson(id = "tony", username = "Tony", displayName = "Tony"),
                    at = story.createdAt + 15_000L
                )
            )
        }
    }

    val allStories = remember(state.stories, selectedId) {
        state.stories.filter { it.expiresAt > System.currentTimeMillis() || it.id == selectedId }.ifEmpty { listOf(story) }
    }

    Dialog(
        onDismissRequest = {
            onAction(Action.OpenStory(selectedId))
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        InstaTheme(true) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101010))
                    .safeDrawingPadding()
                    .testTag("storyInsights")
            ) {
                // Top header: Camera glyph on left, Close X on right
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            onAction(Action.OpenStory(selectedId))
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close insights",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Stories preview carousel with all active cards & adjacent camera card
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allStories, key = { it.id }) { item ->
                        val isSelected = item.id == selectedId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedId = item.id }
                        ) {
                            Box(
                                Modifier
                                    .size(78.dp, 132.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF181818))
                                    .border(
                                        if (isSelected) 1.5.dp else 0.dp,
                                        if (isSelected) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                MediaContent(item.media, Modifier.fillMaxSize())
                                Row(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                            )
                                        )
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Group,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    val itemViews = item.views.coerceAtLeast(item.viewers.size.coerceAtLeast(1).toLong())
                                    Text(
                                        formatCount(itemViews),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Inverted triangle caret under the active card
                            if (isSelected) {
                                Box(
                                    Modifier.padding(top = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DownwardCaret(
                                        Modifier.size(12.dp, 7.dp),
                                        color = Color(0xFF282828)
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(11.dp))
                            }
                        }
                    }

                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                onAction(Action.OpenStory(selectedId))
                                onDismiss()
                                onAction(Action.Navigate("create"))
                            }
                        ) {
                            Box(
                                Modifier
                                    .size(54.dp, 96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0xFF262626), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    contentDescription = "Add story",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.height(11.dp))
                        }
                    }
                }

                // Divider line with Viewers count on left, and Highlight / Delete on right
                HorizontalDivider(color = Color(0xFF282828), thickness = 0.8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Group,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatCount(viewerCount),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Test node support
                    Box(Modifier.size(0.dp)) {
                        Text("Viewers")
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(0.dp)
                            .semantics { contentDescription = "Insights tab" }
                    ) {}

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = { highlightEditor = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Stars,
                            "Add to highlight",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { deleting = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            "Delete story",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFF282828), thickness = 0.8.dp)

                // Section title: "Who viewed your story"
                Text(
                    "Who viewed your story",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
                )

                // Viewers list
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(displayedViewers, key = { it.person.id }) { visit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { person = visit.person }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1F1F1F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Avatar(visit.person, Modifier.size(44.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                visit.person.username,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { person = visit.person },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    "Viewer profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (highlightEditor) {
                var name by remember(story.id) { mutableStateOf(story.highlight) }
                AlertDialog(
                    onDismissRequest = { highlightEditor = false },
                    title = { Text("Save to highlight") },
                    text = { OutlinedTextField(name, { name = it.take(30) }, label = { Text("Highlight name") }) },
                    confirmButton = {
                        TextButton(onClick = {
                            onAction(Action.Highlight(story.id, name))
                            highlightEditor = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { highlightEditor = false }) { Text("Cancel") } }
                )
            }

            if (deleting) {
                ConfirmAction(
                    "Delete story?",
                    "It will also be removed from highlights.",
                    { onAction(Action.DeleteStory(story.id)); onDismiss() },
                    { deleting = false }
                )
            }

            person?.let { PersonSheet(state, it, onAction) { person = null } }
        }
    }
}

@Composable
private fun InsightSection(title: String, count: Long, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            formatCount(count),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp, bottom = 14.dp)
        )
    }
}

@Composable
private fun InsightLine(label: String, count: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(1f)); Text(
        formatCount(count),
        fontWeight = FontWeight.SemiBold
    )
    }
}

@Composable
fun StoryArchive(state: AppState, onAction: (Action) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { ScreenHeader("Stories & highlights", { onAction(Action.Navigate()) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Your stories stay here after they expire. Save your favorites to highlights.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.stories.isEmpty()) item { EmptyGrid("Create your first story") }
            items(state.stories.sortedByDescending { it.createdAt }, key = { it.id }) { story ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(66.dp, 86.dp).clickable {
                            onAction(
                                Action.OpenStory(
                                    story.id,
                                    highlight = story.highlight
                                )
                            )
                        }) { MediaContent(story.media, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            story.highlight.ifBlank { story.overlay.text.ifBlank { story.caption.ifBlank { "Your story" } } },
                            maxLines = 2,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${formatCount(story.views)} views · ${if (story.expiresAt > System.currentTimeMillis()) "Active" else "Archived"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { selected = story.id }) { Icon(Icons.Default.BarChart, "Story insights") }
                }
            }
        }
    }
    selected?.let { id -> StoryInsightsPanel(state, id, onAction) { selected = null } }
}
