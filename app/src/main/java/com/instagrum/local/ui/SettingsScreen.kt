package com.instagrum.local.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instagrum.local.model.*
import com.instagrum.local.notifications.LocalNotifications
import com.instagrum.local.simulation.GrowthPresets

@Composable
fun SettingsScreen(state: AppState, onAction: (Action) -> Unit, backup: BackupActions? = null) {
    val context = LocalContext.current
    Scaffold(topBar = { ScreenHeader("Your pace", { onAction(Action.Navigate()) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("settingsList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "How should your account grow?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "One choice for posts, stories and lives. No percentages to tune. Everything unfolds in real time, with quiet moments as well as busy ones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(GrowthPresets.choices) { preset ->
                val selected = state.settings.preset == preset
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface,
                    label = "growth-choice"
                )
                Surface(
                    onClick = { onAction(Action.ChooseGrowth(preset)) },
                    color = color,
                    border = BorderStroke(1.dp, if (selected) ActionBlue else MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("pace-${preset.name}")
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(GrowthPresets.label(preset), fontWeight = FontWeight.SemiBold)
                            Text(
                                GrowthPresets.description(preset),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (selected) Icon(
                            Icons.Default.CheckCircle,
                            "Selected",
                            tint = ActionBlue,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                ToggleSetting(
                    "Pause activity",
                    state.settings.paused
                ) { onAction(Action.SaveSettings(state.settings.copy(paused = it))) }
                ToggleSetting(
                    "Dark appearance",
                    state.settings.darkMode
                ) { onAction(Action.SaveSettings(state.settings.copy(darkMode = it))) }
                ToggleSetting(
                    "Occasional surprises",
                    state.settings.randomEvents
                ) { onAction(Action.SaveSettings(state.settings.copy(randomEvents = it))) }
                NotificationControls(state, onAction)
                ToggleSetting(
                    "Activity while I'm away",
                    state.settings.backgroundActivity
                ) { onAction(Action.SaveSettings(state.settings.copy(backgroundActivity = it))) }
                ContinuousGrowthControls(state)
                Text(
                    "Periodic catch-up uses Android scheduling and may be delayed by battery settings. For second-by-second growth, keep the service above running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onAction(Action.Navigate("history")) }) { Text("Past livestreams") }
                if (backup != null) BackupControls(state, backup)
                Text(
                    "This is a private simulation, not Instagram. Growth is research-informed but cannot predict a real account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "InstaGRUM",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Built by zyrexdz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/zyrexdz"))
                            )
                        }
                    }) { Text("github.com/zyrexdz") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ContinuousGrowthControls(state: AppState) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("growth_service", android.content.Context.MODE_PRIVATE) }
    var running by remember { mutableStateOf(preferences.getBoolean("enabled", false)) }
    val power = context.getSystemService(android.os.PowerManager::class.java)
    var unrestricted by remember { mutableStateOf(power?.isIgnoringBatteryOptimizations(context.packageName) == true) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    ToggleSetting("Keep growing while closed", running) { enabled ->
        running = enabled
        preferences.edit().putBoolean("enabled", enabled).apply()
        if (enabled) LocalNotifications.startContinuous(context) else LocalNotifications.stopContinuous(context)
        unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    if (running && !unrestricted) Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Battery optimisation will stop this", fontWeight = FontWeight.SemiBold)
            Text(
                "Android may end the service within minutes. Allow unrestricted battery use to keep it running.",
                style = MaterialTheme.typography.bodySmall
            )
            FilledTonalButton(onClick = { requestUnrestrictedBattery(context) }) {
                Text("Allow unrestricted battery")
            }
        }
    }
    if (running) Text(
        "A silent notification stays in your shade while this is on. Android requires it, and it is how the app is allowed to keep running.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun requestUnrestrictedBattery(context: android.content.Context) {
    val targets = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(android.net.Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )
    for (intent in targets) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

class BackupActions(
    val export: (android.net.Uri) -> Unit,
    val import: (android.net.Uri) -> Unit,
    val deleteAccount: () -> Unit,
)

@Composable
private fun BackupControls(state: AppState, backup: BackupActions) {
    var confirmDelete by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(backup.export)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backup.import)
    }
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Text("Your data", fontWeight = FontWeight.SemiBold)
    Text(
        "Your accounts stay on this device and are included in Android's backup, so an app update never affects them. Save a file as well if you want a copy you control.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = { save.launch("instagrum-backup.json") },
            modifier = Modifier.weight(1f)
        ) { Text("Save backup") }
        FilledTonalButton(
            onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.weight(1f)
        ) { Text("Restore") }
    }
    if (state.accounts.size > 1) TextButton(onClick = { confirmDelete = true }) {
        Text("Delete this account", color = MaterialTheme.colorScheme.error)
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this account?") },
        text = { Text("${state.profile.username} and its posts, stories and followers are removed from this device. Your other accounts are untouched. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = { backup.deleteAccount(); confirmDelete = false }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
    )
}

@Composable
fun NotificationControls(state: AppState, onAction: (Action) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(LocalNotifications(context).allowed()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        onAction(Action.SaveSettings(state.settings.copy(notificationsEnabled = granted)))
    }
    ToggleSetting("Likes & follower notifications", state.settings.notificationsEnabled) { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= 33 && !LocalNotifications(context).allowed()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onAction(Action.SaveSettings(state.settings.copy(notificationsEnabled = enabled)))
    }
    if (!allowed) TextButton(onClick = {
        if (Build.VERSION.SDK_INT >= 33 && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                context as android.app.Activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                Settings.EXTRA_APP_PACKAGE,
                context.packageName
            )
        )
    }) { Text("Allow notifications in Android") }
}

@Composable
fun ToggleSetting(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onChange)
    }
}
