package com.instagrum.local.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instagrum.local.model.*

@Composable
fun OnboardingScreen(state: AppState, onAction: (Action) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf("") }
    var keepContent by rememberSaveable { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val valid = username.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.]{1,29}")) && name.isNotBlank()
    val hasExisting = state.posts.isNotEmpty() || state.stories.isNotEmpty()
    fun finish() = onAction(
        Action.CompleteProfile(
            Profile(username = username, displayName = name, bio = bio, avatar = photo),
            keepContent
        )
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Text("Make it yours.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Your name. Your photos. Your own little world.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center
            ) {
                if (photo.isBlank()) Icon(Icons.Default.AddAPhoto, null, Modifier.size(32.dp))
                else MediaContent(Media(path = photo), Modifier.fillMaxSize())
            }
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                PrivateMediaPicker(
                    MediaKind.IMAGE,
                    { photo = it.path },
                    "Add profile photo"
                )
            }
            OutlinedTextField(
                username,
                { username = it.trim().take(30) },
                Modifier.fillMaxWidth().testTag("setupUsername"),
                label = { Text("Username") },
                singleLine = true,
                supportingText = { Text("2–30 letters, numbers, dots or underscores") })
            OutlinedTextField(
                name,
                { name = it.take(80) },
                Modifier.fillMaxWidth().testTag("setupName"),
                label = { Text("Your name") },
                singleLine = true
            )
            OutlinedTextField(
                bio,
                { bio = it.take(300) },
                Modifier.fillMaxWidth(),
                label = { Text("Bio (optional)") },
                maxLines = 4
            )
            if (hasExisting) {
                HorizontalDivider()
                Text("Content from the previous version", fontWeight = FontWeight.SemiBold)
                ToggleSetting("Keep existing posts and stories", keepContent) { keepContent = it }
                Text(
                    "Your new identity starts with zero followers. Nothing is deleted unless you choose to remove the old content.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                enabled = valid,
                onClick = { if (hasExisting && !keepContent) confirmClear = true else finish() },
                modifier = Modifier.fillMaxWidth().testTag("completeProfile")
            ) { Text("Create my profile") }
            Text(
                "No account or login. People and interactions are fictional; your media stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (confirmClear) ConfirmAction(
        "Remove previous content?",
        "The old posts and stories will be removed from the profile. This cannot be undone.",
        { finish() },
        { confirmClear = false })
}
