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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.instagrum.local.model.*

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
    var progress by rememberSaveable(storyId) { mutableFloatStateOf(state.session.storyProgress) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val current = state.session.openStoryId == storyId
    val paused = held || drag != 0f || insights || editingReply || reply.isNotBlank() || !foreground || !current
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
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        sequence.forEachIndexed { i, _ ->
                            LinearProgressIndicator(
                                progress = {
                                    when {
                                        i < index -> 1f; i == index -> progress; else -> 0f
                                    }
                                },
                                modifier = Modifier.weight(1f).height(2.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = .3f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarFromProfile(state.profile, Modifier.size(31.dp)); Spacer(Modifier.width(9.dp))
                        Text(
                            state.profile.username,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "  ${formatTime(story.createdAt)}",
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
                        IconButton(onClick = { insights = true }) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                "Story insights",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { dispatch(Action.OpenStory(null)) },
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.Default.Close, "Close story", tint = Color.White) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (story.caption.isNotBlank()) Text(
                        story.caption,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { insights = true }) {
                        Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Text("  ${formatCount(story.views)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    dispatch(
                        Action.StoryReply(
                            story.id,
                            "🤍"
                        )
                    )
                }) { Icon(Icons.Default.FavoriteBorder, "Send a heart reply", tint = Color.White) }
                OutlinedTextField(
                    reply,
                    { reply = it.take(500) },
                    Modifier.weight(1f).testTag("storyReply").onFocusChanged { editingReply = it.isFocused },
                    placeholder = { Text("Send message", style = MaterialTheme.typography.bodySmall) },
                    maxLines = 2,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedBorderColor = Color.White
                    )
                )
                IconButton(
                    enabled = reply.isNotBlank(),
                    onClick = {
                        dispatch(Action.StoryReply(story.id, reply)); reply = ""
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Send story reply", tint = Color.White) }
            }
        }
        if (insights) StoryInsightsPanel(state, story.id, onAction) { insights = false }
    }
}

@Composable
fun StoryInsightsPanel(state: AppState, initialId: String, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(initialId) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var highlightEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf<FakePerson?>(null) }
    val story = state.stories.find { it.id == selectedId } ?: return
    val details = story.insights
    val interactions = details.likes + details.replies + details.shares + details.stickerTaps
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        InstaTheme(true) {
            Column(Modifier.fillMaxSize().background(Color(0xFF171717)).safeDrawingPadding().testTag("storyInsights")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Story insights",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 16.dp)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close insights", tint = Color.White) }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.stories, key = { it.id }) { item ->
                        Box(
                            Modifier.size(if (item.id == selectedId) 65.dp else 55.dp, 100.dp).border(
                                if (item.id == selectedId) 2.dp else 0.dp,
                                if (item.id == selectedId) Color.White else Color.Transparent
                            ).clickable { selectedId = item.id }) {
                            MediaContent(item.media, Modifier.fillMaxSize())
                            Row(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Color.Black.copy(alpha = .6f)).padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                ); Text(" ${formatCount(item.views)}", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF242424)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { tab = 0 }) {
                        Icon(
                            Icons.Default.Visibility,
                            null,
                            modifier = Modifier.size(19.dp),
                            tint = if (tab == 0) ActionBlue else Color.White
                        ); Text("  ${formatCount(story.views)}", color = if (tab == 0) ActionBlue else Color.White)
                    }
                    TextButton(onClick = { tab = 1 }) {
                        Icon(
                            Icons.Default.BarChart,
                            "Insights tab",
                            tint = if (tab == 1) ActionBlue else Color.White
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { highlightEditor = true }) {
                        Icon(
                            Icons.Default.Stars,
                            "Add to highlight",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { deleting = true }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            "Delete story",
                            tint = Color.White
                        )
                    }
                }
                AnimatedContent(
                    tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "insights-tab",
                    modifier = Modifier.weight(1f)
                ) { page ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        if (page == 0) {
                            item {
                                Text("Reaction summary", fontWeight = FontWeight.SemiBold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 12.dp)
                                ) {
                                    listOf("❤️", "🔥", "😍", "👏").forEach { emoji ->
                                        val count =
                                            story.reactionCounts[emoji] ?: story.viewers.count { it.reaction == emoji }
                                                .toLong()
                                        Column(
                                            Modifier.weight(1f).background(Color(0xFF282828), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(emoji, fontSize = 25.sp); Text(
                                            formatCount(count),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        }
                                    }
                                }
                            }
                            item { Text("Viewers", fontWeight = FontWeight.SemiBold) }
                            if (story.viewers.isEmpty()) item {
                                Text(
                                    "No viewers yet. Give your audience time to arrive.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(story.viewers, key = { it.person.id }) { visit ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { person = visit.person },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(
                                        visit.person,
                                        Modifier.size(42.dp),
                                        ring = visit.reaction.isNotEmpty()
                                    ); Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            visit.person.username,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        ); Text(
                                        visit.person.displayName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    }
                                    Text(visit.reaction)
                                    IconButton(onClick = { person = visit.person }) {
                                        Icon(
                                            Icons.Default.MoreHoriz,
                                            "Viewer profile"
                                        )
                                    }
                                }
                            }
                            if (story.views > story.viewers.size) item {
                                Text(
                                    "Showing ${story.viewers.size} recent viewers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val replies =
                                state.activity.filter { it.storyId == story.id && it.kind == InteractionKind.STORY_REPLY }
                            if (replies.isNotEmpty()) item { Text("Replies", fontWeight = FontWeight.Bold) }
                            items(replies, key = { it.id }) {
                                Text(
                                    "${it.person.username}  ${it.text}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            item { InsightSection("Interactions", interactions, "Actions taken on this story") }
                            item {
                                InsightLine("Likes", details.likes); InsightLine(
                                "Shares",
                                details.shares
                            ); InsightLine("Replies", details.replies); InsightLine(
                                "Profile visits",
                                details.profileVisits
                            ); InsightLine("Sticker taps", details.stickerTaps)
                            }
                            item {
                                HorizontalDivider(); InsightSection(
                                "Discovery",
                                story.views,
                                "Accounts reached with this story"
                            )
                            }
                            item {
                                InsightLine("Impressions", details.impressions); InsightLine(
                                "Follows",
                                details.follows
                            )
                            }
                            item {
                                HorizontalDivider(); InsightSection(
                                "Navigation",
                                details.forward + details.back + details.nextStory + details.exited,
                                "How people moved through your story"
                            )
                            }
                            item {
                                InsightLine("Back", details.back); InsightLine(
                                "Forward",
                                details.forward
                            ); InsightLine("Next story", details.nextStory); InsightLine("Exited", details.exited)
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
                            onAction(
                                Action.Highlight(
                                    story.id,
                                    name
                                )
                            ); highlightEditor = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { highlightEditor = false }) { Text("Cancel") } })
            }
            if (deleting) ConfirmAction(
                "Delete story?",
                "It will also be removed from highlights.",
                { onAction(Action.DeleteStory(story.id)); onDismiss() },
                { deleting = false })
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
