package com.instagrum.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instagrum.local.notifications.LocalNotifications
import com.instagrum.local.ui.*

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotification(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(state?.profileCreated) {
                if (state?.profileCreated == true && Build.VERSION.SDK_INT >= 33) {
                    val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
                    if (!prefs.getBoolean("notifications_asked", false)) {
                        prefs.edit().putBoolean("notifications_asked", true).apply()
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            InstaTheme(state?.settings?.darkMode ?: true) {
                MediaProvider {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val value = state
                        if (value != null) {
                            val context = this@MainActivity
                            InstaApp(
                                value, viewModel::dispatch, BackupActions(
                                    export = { uri -> viewModel.exportBackup(uri, context) { toast(it) } },
                                    import = { uri -> viewModel.importBackup(uri, context) { toast(it) } },
                                    deleteAccount = viewModel::deleteAccount
                                )
                            )
                            if (error != null) AlertDialog(
                                onDismissRequest = {},
                                title = { Text("Couldn't save changes") },
                                text = { Text("$error\n\nActivity is paused until saving succeeds. Your last saved profile is preserved.") },
                                confirmButton = { TextButton(onClick = viewModel::retry) { Text("Retry") } })
                        } else Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                            if (error == null) CircularProgressIndicator() else Column(
                                Modifier.padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("Couldn't open your local profile", style = MaterialTheme.typography.titleLarge)
                                Text(error.orEmpty()); Button(onClick = viewModel::retry) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleNotification(intent)
    }

    private fun handleNotification(intent: Intent?) {
        intent?.getStringExtra(LocalNotifications.EXTRA_ACTIVITY)?.let {
            viewModel.openNotification(it)
            intent.removeExtra(LocalNotifications.EXTRA_ACTIVITY)
        }
    }

    override fun onStart() {
        super.onStart(); viewModel.setForeground(true)
        // Android may have killed the service; restore it if the user wants it.
        if (getSharedPreferences("growth_service", MODE_PRIVATE).getBoolean("enabled", false)) {
            runCatching { LocalNotifications.startContinuous(this) }
        }
    }

    override fun onStop() {
        viewModel.setForeground(false); super.onStop()
    }
}
