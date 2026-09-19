package com.instagrum.local.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.instagrum.local.MainActivity
import com.instagrum.local.R
import com.instagrum.local.data.SimulatorRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GrowthService : Service() {
    companion object {
        const val CHANNEL = "continuous_growth"
        private const val ID = 4411
        const val ACTION_STOP = "com.instagrum.local.STOP_GROWTH"

        fun start(context: Context) {
            val intent = Intent(context, GrowthService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GrowthService::class.java))
        }
    }

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(); return START_NOT_STICKY
        }
        startForeground(ID, buildNotification())
        if (scope == null) {
            val created = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = created
            created.launch {
                val runtime = SimulatorRuntime.get(applicationContext)
                while (isActive) {
                    runCatching { runtime.advanceWhileClosed() }
                    delay(5_000)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Continuous growth", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps your simulated accounts growing while the app is closed"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GrowthService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("InstaGRUM is running")
            .setContentText("Your accounts keep growing in the background.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }
}
