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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private fun ProfileStat(value: Long, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        AnimatedNumber(value, size = 16.sp, uppercase = true)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
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
    val unseenStories = activeStories.any { !it.seen }
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
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().height(44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                                Icon(
                                    Icons.Default.Add,
                                    "Create content",
                                    Modifier.size(26.dp).clickable { onAction(Action.Navigate("create")) })
                            }
                            Row(
                                Modifier.weight(1f).clickable { accountsOpen = true },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    profile.username,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    "Switch account",
                                    Modifier.size(19.dp).padding(start = 3.dp)
                                )
                            }
                            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(
                                    Icons.Default.Menu,
                                    "Settings",
                                    Modifier.size(25.dp).clickable { onAction(Action.Navigate("settings")) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(80.dp)) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .storyRing(unseenStories, 2.5.dp)
                                        .padding(3.dp)
                                        .clickable {
                                            activeStories.firstOrNull { !it.seen }
                                                ?.let { onAction(Action.OpenStory(it.id)) }
                                                ?: activeStories.firstOrNull()
                                                    ?.let { onAction(Action.OpenStory(it.id)) }
                                                ?: run { editOpen = true }
                                        }
                                ) { AvatarFromProfile(profile, Modifier.fillMaxSize()) }
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(1.5.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { onAction(Action.Navigate("create")) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Add, "Add", Modifier.size(13.dp), tint = Color.Black) }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 24.dp, end = 6.dp)
                            ) {
                                Text(
                                    profile.displayName.ifBlank { profile.username },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    ProfileStat(state.posts.size.toLong(), "posts")
                                    ProfileStat(
                                        profile.followers,
                                        "followers",
                                        Modifier.clickable { peopleSheet = "Followers" })
                                    ProfileStat(
                                        profile.following,
                                        "following",
                                        Modifier.clickable { peopleSheet = "Following" })
                                }
                            }
                        }
                        if (profile.bio.isNotBlank()) Text(
                            profile.bio,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            onClick = { onAction(Action.Navigate("settings")) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Your dashboard", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatCount(state.posts.sumOf { it.views })} views in the last 30 days.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileButton("Edit profile", Modifier.weight(1f)) { editOpen = true }
                            ProfileButton("Share profile", Modifier.weight(1f)) { editOpen = true }
                        }
                        if (state.activeLive != null) TextButton(onClick = { onAction(Action.Navigate("live")) }) {
                            Text("● Return to live · ${formatCount(state.activeLive.viewers.toLong())} watching")
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
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            listOf(
                                Icons.Default.GridOn to "Posts",
                                Icons.Default.PlayCircleOutline to "Reels"
                            ).forEachIndexed { index, (icon, label) ->
                                val active = currentTab == index
                                Column(
                                    Modifier.weight(1f).clickable {
                                        onAction(
                                            Action.GridPosition(
                                                grid.firstVisibleItemIndex,
                                                grid.firstVisibleItemScrollOffset,
                                                index
                                            )
                                        )
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        icon,
                                        label,
                                        Modifier.padding(vertical = 10.dp).size(24.dp),
                                        tint = if (active) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(
                                        Modifier.fillMaxWidth().height(1.5.dp).background(
                                            if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyGrid("No ${if (currentTab == 1) "videos" else if (currentTab == 2) "saved posts" else "posts"} yet. Create your next moment.") }
                items(posts, key = { it.id }) { post ->
                    Box(
                        Modifier.aspectRatio(.75f).testTag("grid-${post.id}")
                            .clickable { onAction(Action.OpenPost(post.id)) }) {
                        MediaContent(post.media, Modifier.fillMaxSize(), description = post.caption)
                        if (post.media.kind != MediaKind.IMAGE) Icon(
                            Icons.Default.PlayArrow,
                            "Video",
                            tint = Color.White,
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
