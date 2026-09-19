package com.instagrum.local.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instagrum.local.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun InstaApp(state: AppState, onAction: (Action) -> Unit, backup: BackupActions? = null) {
    if (!state.profileCreated) {
        OnboardingScreen(state, onAction); return
    }
    val session = state.session
    BackHandler(
        session.openPostId != null || session.openStoryId != null || session.screen != "main" || session.tab !in listOf(
            "home",
            "profile"
        )
    ) {
        when {
            session.openStoryId != null -> onAction(Action.OpenStory(null))
            session.openPostId != null -> onAction(Action.OpenPost(null))
            else -> onAction(Action.Navigate())
        }
    }
    val route = when {
        session.openStoryId != null -> "story:${session.openStoryId}"
        session.openPostId != null -> "post:${session.openPostId}"
        session.screen != "main" -> session.screen
        else -> session.tab
    }
    AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "navigation") { destination ->
        when {
            destination.startsWith("story:") -> StoryViewer(state, destination.removePrefix("story:"), onAction)
            destination.startsWith("post:") -> PostDetail(state, destination.removePrefix("post:"), onAction)
            destination == "settings" -> SettingsScreen(state, onAction, backup)
            destination == "create" -> CreateScreen(state, onAction)
            destination == "live" -> LiveScreen(state, onAction)
            destination == "history" -> LiveHistory(state, onAction)
            destination == "archive" -> StoryArchive(state, onAction)
            destination == "reels" || destination == "explore" -> ReelsScreen(state, onAction)
            destination == "notifications" -> NotificationsScreen(state, onAction)
            else -> ProfileScreen(state, onAction)
        }
    }
}

@Composable
private fun AccountSwitcher(state: AppState, onAction: (Action) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Accounts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.accounts.forEach { account ->
                    Surface(
                        onClick = {
                            if (account.id != state.activeAccountId) onAction(Action.SwitchAccount(account.id))
                            dismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (account.id == state.activeAccountId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarFromProfile(
                                Profile(
                                    username = account.username,
                                    displayName = account.displayName,
                                    avatar = account.avatar,
                                    avatarArtwork = account.avatarArtwork
                                ),
                                Modifier.size(40.dp)
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(account.username.ifBlank { "New account" }, fontWeight = FontWeight.SemiBold)
                                Text(account.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                            if (account.id == state.activeAccountId) Icon(Icons.Default.CheckCircle, "Current account")
                        }
                    }
                }
                TextButton(onClick = { onAction(Action.CreateAccount); dismiss() }) {
                    Icon(Icons.Default.AddCircleOutline, null)
                    Text("  Add account")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } }
    )
}

@Composable
fun AppScaffold(state: AppState, onAction: (Action) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNav(state, onAction) },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(state: AppState, onAction: (Action) -> Unit) {
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var peopleSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var accountsOpen by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing, state.lastSavedAt) {
        if (refreshing) {
            delay(450)
            refreshing = false
        }
    }
    val profile = state.profile
    val activeStories = state.stories.filter { it.expiresAt > System.currentTimeMillis() }
    val highlights = state.stories.filter { it.highlight.isNotBlank() }.distinctBy { it.highlight }
    val grid = rememberLazyGridState(state.session.gridIndex, state.session.gridOffset)
    val currentTab by rememberUpdatedState(state.session.gridTab)
    LaunchedEffect(grid) {
        snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.distinctUntilChanged()
            .collect { (index, offset) ->
                onAction(Action.GridPosition(index, offset, currentTab))
            }
    }
    val posts = when (state.session.gridTab) {
        1 -> state.posts.filter { it.media.kind != MediaKind.IMAGE }
        2 -> state.posts.filter { it.saved }
        else -> state.posts
    }
    AppScaffold(state, onAction) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; onAction(Action.Refresh) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = grid,
                modifier = Modifier.fillMaxSize().testTag("profileGrid"),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).clickable { accountsOpen = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    profile.username,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.KeyboardArrowDown, "Switch account", Modifier.size(22.dp))
                            }
                            IconButton(onClick = { onAction(Action.Navigate("create")) }) {
                                Icon(
                                    Icons.Default.AddBox,
                                    "Create content"
                                )
                            }
                            IconButton(onClick = { onAction(Action.Navigate("settings")) }) {
                                Icon(
                                    Icons.Default.Tune,
                                    "Simulation settings"
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(90.dp).then(
                                    if (activeStories.isNotEmpty()) Modifier.border(
                                        2.dp,
                                        StoryGradient,
                                        CircleShape
                                    ) else Modifier
                                ).padding(5.dp)
                                    .clickable {
                                        activeStories.firstOrNull()?.let { onAction(Action.OpenStory(it.id)) }
                                            ?: run { editOpen = true }
                                    }) {
                                AvatarFromProfile(profile, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(14.dp))
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat("posts", state.posts.size.toLong())
                                Stat("followers", profile.followers, Modifier.clickable { peopleSheet = "Followers" })
                                Stat("likes", state.posts.sumOf { it.likes })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(profile.displayName, fontWeight = FontWeight.SemiBold)
                            if (profile.verified) Icon(
                                Icons.Default.Verified,
                                "Simulated verification",
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { editOpen = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(9.dp)
                            ) { Text("Edit profile") }
                            FilledTonalButton(
                                onClick = { onAction(Action.Navigate("settings")) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(9.dp)
                            ) { Text("Your pace") }
                        }
                        Surface(
                            onClick = { onAction(Action.Navigate("settings")) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Profile insights", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "${formatCount(profile.visits)} profile visits",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (state.settings.paused || state.settings.frozen) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    "Simulation state"
                                )
                            }
                        }
                        if (state.activeLive != null) TextButton(onClick = { onAction(Action.Navigate("live")) }) {
                            Text(
                                "● Return to live · ${
                                    formatCount(
                                        state.activeLive.viewers.toLong()
                                    )
                                } watching"
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(Modifier.padding(horizontal = 18.dp)) {
                            SectionTitle(
                                "Highlights",
                                "Archive"
                            ) { onAction(Action.Navigate("archive")) }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(highlights, key = { it.id }) { story ->
                                Column(
                                    Modifier.width(66.dp)
                                        .clickable {
                                            onAction(
                                                Action.OpenStory(
                                                    story.id,
                                                    highlight = story.highlight
                                                )
                                            )
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(62.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            .padding(4.dp).clip(CircleShape)
                                    ) { MediaContent(story.media, Modifier.fillMaxSize()) }
                                    Text(
                                        story.highlight,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                            item {
                                Column(
                                    Modifier.width(66.dp).clickable { onAction(Action.Navigate("archive")) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(62.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Add, "Add highlight") }
                                    Text(
                                        "New",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                Icons.Default.GridOn to "Posts",
                                Icons.Default.PlayCircleOutline to "Reels",
                                Icons.Default.BookmarkBorder to "Saved"
                            ).forEachIndexed { index, (icon, label) ->
                                IconButton(onClick = {
                                    onAction(
                                        Action.GridPosition(
                                            grid.firstVisibleItemIndex,
                                            grid.firstVisibleItemScrollOffset,
                                            index
                                        )
                                    )
                                }) {
                                    Icon(
                                        icon,
                                        label,
                                        tint = if (currentTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = .5.dp)
                    }
                }
                if (posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyGrid("No ${if (currentTab == 1) "videos" else if (currentTab == 2) "saved posts" else "posts"} yet. Create your next moment.") }
                items(posts, key = { it.id }) { post ->
                    Box(
                        Modifier.aspectRatio(.8f).testTag("grid-${post.id}")
                            .clickable { onAction(Action.OpenPost(post.id)) }) {
                        MediaContent(post.media, Modifier.fillMaxSize(), description = post.caption)
                        if (post.media.kind != MediaKind.IMAGE) Icon(
                            Icons.Default.PlayArrow,
                            "Video",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        )
                    }
                }
            }
        }
    }
    if (editOpen) EditProfileDialog(profile, onAction) { editOpen = false }
    if (accountsOpen) AccountSwitcher(state, onAction) { accountsOpen = false }
    peopleSheet?.let { label ->
        PeopleSheet(
            state,
            label,
            if (label == "Following") state.people.filter { it.followed } else state.people.filter { it.followsYou },
            onAction
        ) { peopleSheet = null }
    }
}
