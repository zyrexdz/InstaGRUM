package com.instagrum.local.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instagrum.local.model.*
import java.text.NumberFormat
import java.util.Locale

val StoryGradient = Brush.linearGradient(
    listOf(
        Color(0xFFFEDA75),
        Color(0xFFFA7E1E),
        Color(0xFFD62976),
        Color(0xFF962FBF),
        Color(0xFF4F5BD5)
    )
)
private val RingStops = listOf(
    Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976),
    Color(0xFF962FBF), Color(0xFF4F5BD5), Color(0xFFFEDA75)
)
val HeartColor = Color(0xFFFF3040)

fun Modifier.storyRing(unseen: Boolean, stroke: Dp = 2.5.dp): Modifier = drawBehind {
    val width = stroke.toPx()
    val radius = size.minDimension / 2 - width / 2
    if (unseen) drawCircle(Brush.sweepGradient(RingStops), radius, style = Stroke(width))
    else drawCircle(IgSeenRing, radius, style = Stroke(width))
}

@Composable
fun LocalArtwork(
    artwork: Int,
    modifier: Modifier = Modifier,
    rounded: Boolean = true,
    contentDescription: String? = null,
    showPlay: Boolean = false
) {
    val palettes = listOf(0xFF7796A0, 0xFFC29482, 0xFF938BA8, 0xFF8BA894, 0xFFC9AE80, 0xFF7D92BB)
    val skin = listOf(0xFFDEAD89, 0xFFAA7351, 0xFFF1C8A9, 0xFF765040)[artwork.mod(4)]
    Box(
        modifier.then(if (rounded) Modifier.clip(CircleShape) else Modifier).background(Color(palettes[artwork.mod(6)]))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width;
            val h = size.height
            drawCircle(Color.White.copy(alpha = .15f), w * .5f, Offset(w * .8f, h * .1f))
            drawOval(
                Color(0xFF293946),
                Offset(w * .12f, h * .67f),
                androidx.compose.ui.geometry.Size(w * .8f, h * .65f)
            )
            drawOval(
                Color(0xFF382C29),
                Offset(w * .25f, h * .15f),
                androidx.compose.ui.geometry.Size(w * .5f, h * .58f)
            )
            drawOval(Color(skin), Offset(w * .3f, h * .27f), androidx.compose.ui.geometry.Size(w * .4f, h * .43f))
            drawPath(Path().apply {
                moveTo(w * .25f, h * .4f); lineTo(w * .32f, h * .19f); lineTo(
                w * .68f,
                h * .18f
            ); lineTo(w * .76f, h * .4f); lineTo(w * .55f, h * .3f); close()
            }, Color(0xFF382C29))
            drawCircle(Color(0xFF382C29), w * .018f, Offset(w * .42f, h * .46f))
            drawCircle(Color(0xFF382C29), w * .018f, Offset(w * .58f, h * .46f))
            drawLine(Color(0xFF925F52), Offset(w * .46f, h * .58f), Offset(w * .55f, h * .58f), w * .02f)
        }
        if (showPlay) Icon(
            Icons.Default.PlayArrow,
            contentDescription,
            tint = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun Avatar(person: FakePerson?, modifier: Modifier = Modifier.size(44.dp), ring: Boolean = false) {
    Box(
        modifier.then(if (ring) Modifier.border(2.dp, StoryGradient, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape)
    ) {
        if (person?.avatar?.isNotBlank() == true) MediaContent(Media(path = person.avatar), Modifier.fillMaxSize())
        else LocalArtwork(person?.colorIndex ?: 8, Modifier.fillMaxSize(), rounded = false)
    }
}

@Composable
fun AvatarFromProfile(profile: Profile, modifier: Modifier = Modifier.size(86.dp)) {
    Box(modifier.clip(CircleShape)) {
        if (profile.avatar.isNotBlank()) MediaContent(Media(path = profile.avatar), Modifier.fillMaxSize())
        else Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                "Add a profile photo",
                Modifier.fillMaxSize(.7f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnimatedNumber(value: Long, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 16.sp, uppercase: Boolean = false) {
    AnimatedContent(
        formatCount(value, uppercase),
        transitionSpec = {
            (slideInVertically(Motion.enter()) { it / 2 } + fadeIn(Motion.quick()))
                .togetherWith(slideOutVertically(Motion.quick()) { -it / 2 } + fadeOut(Motion.quick()))
        },
        label = "stat",
        modifier = modifier
    ) {
        Text(it, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Stat(label: String, value: Long, modifier: Modifier = Modifier, uppercase: Boolean = true) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value, uppercase = uppercase)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun SmallPill(text: String, icon: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun BottomNav(state: AppState, onAction: (Action) -> Unit) {
    val selected = state.session.tab
    val newFollowers = (state.profile.followers - state.acknowledgedFollowers).coerceAtLeast(0)
    val newLikes = (state.posts.sumOf { it.likes } - state.acknowledgedLikes).coerceAtLeast(0)
    val totalNew = newFollowers + newLikes
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(thickness = .5.dp)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "notifications" to Icons.Default.FavoriteBorder,
                    "create" to Icons.Default.AddBox,
                    "profile" to Icons.Default.Person
                ).forEach { (tab, icon) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = {
                            onAction(Action.Navigate(tab = tab))
                            if (tab == "notifications") onAction(Action.ReadEvents)
                        }) {
                            Icon(
                                icon,
                                tab,
                                Modifier.size(25.dp),
                                tint = if (selected == tab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedContent(
                            targetState = if (tab == "notifications") totalNew else 0L,
                            transitionSpec = { scaleIn(spring(dampingRatio = .5f)) + fadeIn() togetherWith scaleOut() + fadeOut() },
                            label = "new-activity",
                            modifier = Modifier.align(Alignment.Center).offset(x = 15.dp, y = (-11).dp)
                        ) { count ->
                            if (count > 0) Text(
                                "+${formatCount(count)}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(HeartColor, RoundedCornerShape(50))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 10.dp else 0.dp)
        )
        actions()
    }
}

@Composable
fun StatInput(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false
) {
    OutlinedTextField(
        value,
        { raw -> onValue(raw.filter { it.isDigit() || (decimal && it == '.') }.take(16)) },
        modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    )
}

@Composable
fun EmptyGrid(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmAction(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun formatCount(number: Long, uppercase: Boolean = false): String {
    val (divisor, suffix) = when {
        number >= 1_000_000_000 -> 1_000_000_000.0 to "B"
        number >= 1_000_000 -> 1_000_000.0 to "M"
        number >= 1_000 -> 1_000.0 to if (uppercase) "K" else "k"
        else -> return NumberFormat.getIntegerInstance(Locale.US).format(number)
    }
    return String.format(Locale.US, "%.1f", number / divisor).removeSuffix(".0") + suffix
}

fun formatDuration(seconds: Double): String =
    (seconds.toInt().coerceAtLeast(0)).let { "%02d:%02d".format(Locale.US, it / 60, it % 60) }

fun formatTime(timestamp: Long): String = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0)).let {
    when {
        it < 60_000 -> "now"; it < 3_600_000 -> "${it / 60_000}m"; it < 86_400_000 -> "${it / 3_600_000}h"; else -> "${it / 86_400_000}d"
    }
}

fun formatDate(timestamp: Long): String =
    java.text.SimpleDateFormat("MMMM d, yyyy", Locale.US).format(java.util.Date(timestamp))
