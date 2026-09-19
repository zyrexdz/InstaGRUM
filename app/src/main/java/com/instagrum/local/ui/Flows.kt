package com.instagrum.local.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.instagrum.local.model.*

@Composable
fun ReelsScreen(state: AppState, onAction: (Action) -> Unit) {
    val reels = state.posts.filter { it.media.kind != MediaKind.IMAGE }
    val pager = rememberPagerState { reels.size }
    AppScaffold(state, onAction) { padding ->
        if (reels.isEmpty()) Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyGrid("Your videos belong here")
            TextButton(onClick = {
                onAction(Action.SaveDraft(state.draft.copy(mode = 3, stage = "camera"))); onAction(
                Action.Navigate("create")
            )
            }) { Text("Create a reel") }
        } else VerticalPager(pager, Modifier.fillMaxSize().padding(padding), key = { reels[it].id }) { page ->
            val post = reels[page]
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                MediaContent(
                    post.media,
                    Modifier.fillMaxSize(),
                    playVideo = page == pager.settledPage,
                    controls = false
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = .8f)
                            )
                        )
                    )
                )
                Text(
                    "Reels",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(18.dp)
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 70.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarFromProfile(
                            state.profile,
                            Modifier.size(32.dp)
                        ); Text("  ${state.profile.username}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(post.caption, color = Color.White, maxLines = 3)
                    Text(
                        "${formatCount(post.views)} views",
                        color = Color.White.copy(alpha = .7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp, 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { onAction(Action.LikePost(post.id)) }) {
                        Icon(
                            if (post.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Like reel",
                            tint = if (post.liked) HeartColor else Color.White
                        )
                    }
                    Text(formatCount(post.likes), color = Color.White)
                    IconButton(onClick = { onAction(Action.OpenPost(post.id)) }) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            "Open reel comments",
                            tint = Color.White
                        )
                    }
                    Text(formatCount(post.commentCount), color = Color.White)
                    IconButton(onClick = { onAction(Action.SavePost(post.id)) }) {
                        Icon(
                            if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            "Save reel",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(state: AppState, onAction: (Action) -> Unit) {
    var selected by remember { mutableStateOf<FakePerson?>(null) }
    val now = System.currentTimeMillis()
    val groups = state.activity.groupBy {
        when {
            now - it.createdAt < 86400000 -> "Today"
            now - it.createdAt < 7 * 86400000L -> "This week"
            else -> "Earlier"
        }
    }
    AppScaffold(state, onAction) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("activityList"),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Row(Modifier.padding(horizontal = 18.dp)) {
                    SectionTitle(
                        "Activity",
                        "Mark read"
                    ) { onAction(Action.ReadEvents) }
                }
            }
            if (state.activity.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, null, Modifier.size(46.dp))
                    Text(
                        "Your activity starts here",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "Share a moment. Likes, comments and new followers will arrive at your chosen pace.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            groups.forEach { (label, entries) ->
                item { Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp, 14.dp)) }
                items(entries, key = { it.id }) { event ->
                    val person = state.people.find { it.id == event.person.id } ?: event.person
                    val media = state.posts.find { it.id == event.postId }?.media
                        ?: state.stories.find { it.id == event.storyId }?.media
                    Row(
                        Modifier.animateItem().fillMaxWidth()
                            .background(if (event.read) Color.Transparent else ActionBlue.copy(alpha = .07f))
                            .clickable {
                                onAction(Action.OpenActivity(event.id))
                                if (event.kind == InteractionKind.FOLLOW) selected = person
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(person, Modifier.size(46.dp).clickable { selected = person })
                        Spacer(Modifier.width(12.dp))
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(person.username) }
                            append(" ${event.message()} ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(
                                    formatTime(
                                        event.createdAt
                                    )
                                )
                            }
                        }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(10.dp))
                        if (event.kind == InteractionKind.FOLLOW) Button(
                            onClick = { onAction(Action.Follow(person.id)) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(12.dp, 5.dp)
                        ) { Text(if (person.followed) "Following" else "Follow back") }
                        else if (media != null) MediaContent(media, Modifier.size(44.dp, 52.dp))
                    }
                }
            }
        }
    }
    selected?.let { PersonSheet(state, it, onAction) { selected = null } }
}

@Composable
fun PersonRow(person: FakePerson, onAction: (Action) -> Unit, onOpen: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(person, Modifier.size(44.dp).clickable(onClick = onOpen))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(person.username, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                person.displayName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAction(Action.Follow(person.id)) },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(14.dp, 5.dp)
        ) {
            AnimatedContent(person.followed, label = "follow-state") { Text(if (it) "Following" else "Follow") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleSheet(
    state: AppState,
    title: String,
    people: List<FakePerson>,
    onAction: (Action) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<FakePerson?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(20.dp, 8.dp)
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(20.dp)) {
            items(people, key = { it.id }) { person ->
                PersonRow(state.people.find { it.id == person.id } ?: person,
                    onAction) { selected = person }
            }
            if (people.isEmpty()) item { Text("No profiles yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    selected?.let { PersonSheet(state, it, onAction) { selected = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSheet(state: AppState, original: FakePerson, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    val person = state.people.find { it.id == original.id } ?: original
    val seed = person.id.hashCode().toLong().and(0x7fffffff)
    val bios = listOf(
        "little moments, big memories",
        "coffee first ☕",
        "somewhere outside 🌿",
        "creating as I go",
        "less scrolling, more living",
        "film • music • late nights",
        "just here for the good stuff",
        "one day at a time"
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(person, Modifier.size(80.dp)); Spacer(Modifier.width(18.dp))
                Stat("followers", 80 + seed % 3200, Modifier.weight(1f)); Stat(
                "following",
                60 + seed % 800,
                Modifier.weight(1f)
            )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (person.verified) Icon(
                    Icons.Default.Verified,
                    "Simulated verified badge",
                    Modifier.padding(start = 6.dp).size(18.dp),
                    tint = ActionBlue
                )
            }
            Text("${person.displayName}\n${bios[(seed % bios.size).toInt()]}")
            Text(
                "Fictional profile",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Button(
                onClick = { onAction(Action.Follow(person.id)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (person.followed) "Unfollow" else "Follow") }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    MediaContent(
                        Media(artwork = (seed % 9).toInt() + it),
                        Modifier.weight(1f).aspectRatio(1f)
                    )
                }
            }
        }
    }
}
