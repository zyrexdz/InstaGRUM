package com.instagrum.local.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.instagrum.local.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
fun PostDetail(state: AppState, postId: String, onAction: (Action) -> Unit) {
    val start = state.posts.indexOfFirst { it.id == postId }
    if (start < 0) {
        LaunchedEffect(postId) { onAction(Action.OpenPost(null)) }; return
    }
    val pagerState = rememberPagerState(initialPage = start) { state.posts.size }
    val posts by rememberUpdatedState(state.posts)
    LaunchedEffect(pagerState) {

        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().drop(1)
            .collect { page -> posts.getOrNull(page)?.let { onAction(Action.OpenPost(it.id)) } }
    }
    val current = state.posts.getOrNull(pagerState.currentPage) ?: state.posts[start]
    Scaffold(topBar = {
        ScreenHeader(
            if (current.media.kind == MediaKind.REEL) "Reels" else "Posts",
            { onAction(Action.OpenPost(null)) })
    }) { padding ->
        VerticalPager(
            pagerState,
            Modifier.fillMaxSize().padding(padding),
            key = { state.posts[it].id }
        ) { page ->
            PostPage(state, state.posts[page], page == pagerState.currentPage, onAction)
        }
    }
}

@Composable
private fun PostPage(state: AppState, post: Post, active: Boolean, onAction: (Action) -> Unit) {
    var comments by rememberSaveable(post.id) { mutableStateOf(false) }
    var controls by rememberSaveable(post.id) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(post.id) { mutableStateOf(false) }
    var insights by rememberSaveable(post.id) { mutableStateOf(false) }
    var heart by remember(post.id) { mutableIntStateOf(0) }
    var showHeart by remember(post.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(heart) {
        if (heart > 0) {
            showHeart = true; delay(650); showHeart = false
        }
    }
    val like: () -> Unit =
        { onAction(Action.LikePost(post.id)); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarFromProfile(state.profile, Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.profile.username, fontWeight = FontWeight.SemiBold)
                    if (post.location.isNotBlank()) Text(post.location, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { controls = true }) { Icon(Icons.Default.MoreHoriz, "Post controls") }
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(if (post.media.kind == MediaKind.REEL) .7f else 1f)
                    .then(if (active) Modifier.testTag("postMedia") else Modifier)
                    .pointerInput(post.id) {
                        detectTapGestures(onDoubleTap = {
                            onAction(Action.LikePost(post.id, onlyLike = true)); heart++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        })
                    }, contentAlignment = Alignment.Center
            ) {
                MediaContent(
                    post.media,
                    Modifier.fillMaxSize(),
                    playVideo = true,
                    paused = !active || comments || controls || fullScreen
                )
                AnimatedVisibility(
                    showHeart,
                    enter = scaleIn(spring(dampingRatio = .55f), initialScale = .4f) + fadeIn(),
                    exit = scaleOut(tween(160), targetScale = 1.2f) + fadeOut()
                ) {
                    Icon(Icons.Default.Favorite, "Liked", tint = Color.White, modifier = Modifier.size(96.dp))
                }
                IconButton(onClick = { fullScreen = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        Icons.Default.Fullscreen,
                        "Open full-screen media",
                        tint = Color.White
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "View insights",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { insights = true }
                )
                HorizontalDivider(thickness = .5.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountAction(
                        if (post.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Like post",
                        post.likes,
                        if (post.liked) HeartColor else LocalContentColor.current,
                        like
                    )
                    Spacer(Modifier.width(18.dp))
                    CountAction(
                        Icons.Default.ChatBubbleOutline,
                        "Open comments",
                        post.commentCount,
                        LocalContentColor.current
                    ) { comments = true }
                    Spacer(Modifier.width(18.dp))
                    CountAction(
                        Icons.Default.Repeat,
                        "Shares",
                        post.views / 400,
                        LocalContentColor.current
                    ) { insights = true }
                    Spacer(Modifier.width(18.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Send post",
                        Modifier.size(25.dp).clickable { insights = true })
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        "Save post",
                        Modifier.size(25.dp).clickable { onAction(Action.SavePost(post.id)) })
                }
                if (post.likes > 0) LikedByRow(post)
                if (post.caption.isNotBlank()) Text("${state.profile.username}  ${post.caption}")
                Text(
                    formatDate(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.engine.events.any { it.postId == post.id }) SmallPill("Your post is taking off ✨")
                if (post.frozen) SmallPill("Statistics frozen")
            }
        }
    }
    if (comments) CommentsSheet(state, post, onAction) { comments = false }
    if (controls) PostControls(post, onAction) { controls = false }
    if (fullScreen) FullScreenMedia(post.media) { fullScreen = false }
    if (insights) PostInsightsSheet(post) { insights = false }
}

@Composable
private fun CountAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    count: Long,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, description, Modifier.size(25.dp), tint = tint)
        if (count > 0) Text(
            "  ${formatCount(count)}",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LikedByRow(post: Post) {
    val faces = post.sampledLikers.take(3)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (faces.isNotEmpty()) {
            Box(Modifier.width((18 * (faces.size - 1) + 26).dp).height(26.dp)) {
                faces.forEachIndexed { index, person ->
                    Avatar(
                        person,
                        Modifier.size(26.dp).offset(x = (index * 18).dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        val name = faces.firstOrNull()?.username
        Text(
            if (name != null) "Liked by $name and others" else "${formatCount(post.likes)} likes",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostInsightsSheet(post: Post, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Post insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("views", post.views)
                Stat("likes", post.likes)
                Stat("comments", post.commentCount)
            }
            HorizontalDivider()
            val rate = if (post.views > 0) post.likes.toDouble() / post.views * 100 else 0.0
            Text("Engagement rate: ${String.format(java.util.Locale.US, "%.1f", rate)}%")
            Text(
                "Shared with your followers and through recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(state: AppState, post: Post, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var replyId by rememberSaveable { mutableStateOf<String?>(null) }
    var person by remember { mutableStateOf<FakePerson?>(null) }
    val roots =
        post.comments.filter { it.parentId == null || post.comments.none { parent -> parent.id == it.parentId } }
    val ordered = remember(post.comments) {
        buildList<Pair<Comment, Int>> {
            val visited = mutableSetOf<String>()
            fun append(comment: Comment, depth: Int) {
                if (!visited.add(comment.id)) return
                add(comment to depth)
                post.comments.filter { it.parentId == comment.id }.forEach { append(it, depth + 1) }
            }
            roots.forEach { append(it, 0) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).imePadding()) {
            Text(
                "Comments",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                "${formatCount(post.commentCount)} total · showing a local sample",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
            LazyColumn(Modifier.weight(1f).testTag("commentsList"), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(ordered, key = { it.first.id }) { (comment, depth) ->
                    Row(
                        Modifier.animateItem().padding(
                            start = (16 + depth.coerceAtMost(2) * 20).dp,
                            end = 8.dp,
                            top = 10.dp,
                            bottom = 10.dp
                        )
                    ) {
                        Avatar(
                            comment.person,
                            Modifier.size(34.dp).clickable { if (!comment.own) person = comment.person })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comment.person.username,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.clickable { if (!comment.own) person = comment.person })
                                if (comment.person.verified) Icon(
                                    Icons.Default.Verified,
                                    "Simulated verified badge",
                                    Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    " · ${formatTime(comment.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { replyId = comment.id },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) { Text("Reply", style = MaterialTheme.typography.labelSmall) }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { onAction(Action.LikeComment(post.id, comment.id)) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (comment.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    "Like comment",
                                    Modifier.size(17.dp),
                                    tint = if (comment.liked) HeartColor else LocalContentColor.current
                                )
                            }
                            Text(formatCount(comment.likes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (ordered.isEmpty()) item { EmptyGrid("Start the conversation") }
            }
            if (replyId != null) Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Replying to ${post.comments.find { it.id == replyId }?.person?.username.orEmpty()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = { replyId = null }) {
                    Icon(
                        Icons.Default.Close,
                        "Cancel reply",
                        Modifier.size(16.dp)
                    )
                }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarFromProfile(state.profile, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    input,
                    { input = it.take(2000) },
                    Modifier.weight(1f).testTag("commentInput"),
                    placeholder = { Text("Add a comment…") },
                    maxLines = 3
                )
                IconButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        onAction(Action.AddComment(post.id, input, replyId)); input = ""; replyId = null
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Post comment") }
            }
        }
    }
    person?.let { PersonSheet(state, it, onAction) { person = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostControls(post: Post, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    var delete by remember { mutableStateOf(false) }
    var frozen by rememberSaveable(post.id) { mutableStateOf(post.frozen) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Post options",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text(if (post.saved) "Remove from saved" else "Save post") },
                leadingContent = {
                    Icon(
                        if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        null
                    )
                },
                modifier = Modifier.clickable { onAction(Action.SavePost(post.id)); onDismiss() }
            )
            ListItem(
                headlineContent = { Text(if (frozen) "Resume activity on this post" else "Pause activity on this post") },
                leadingContent = { Icon(if (frozen) Icons.Default.PlayArrow else Icons.Default.Pause, null) },
                trailingContent = {
                    Switch(
                        frozen,
                        {
                            frozen = it; onAction(
                            Action.PostStats(
                                post.id,
                                post.likes,
                                post.commentCount,
                                post.views,
                                it,
                                post.viralPotential
                            )
                        )
                        })
                }
            )
            ListItem(
                headlineContent = { Text("Delete post", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { delete = true }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    if (delete) ConfirmAction(
        "Delete post?",
        "It will be removed from your profile along with its comments and activity.",
        { onAction(Action.DeletePost(post.id)); onDismiss() },
        { delete = false })
}

@Composable
fun FullScreenMedia(media: Media, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Box(
                Modifier.fillMaxSize().pointerInput(media) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f); x =
                        (x + pan.x).coerceIn(-size.width.toFloat(), size.width.toFloat()); y =
                        (y + pan.y).coerceIn(-size.height.toFloat(), size.height.toFloat())
                    }
                }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = if (scale > 1f) x else 0f; translationY =
                        if (scale > 1f) y else 0f
                    }) {
                MediaContent(media, Modifier.fillMaxSize(), playVideo = true)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Default.Close,
                    "Close media",
                    tint = Color.White
                )
            }
        }
    }
}
