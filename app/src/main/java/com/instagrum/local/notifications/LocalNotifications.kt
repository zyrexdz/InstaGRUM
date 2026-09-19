package com.instagrum.local.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.instagrum.local.MainActivity
import com.instagrum.local.R
import com.instagrum.local.data.SimulatorRuntime
import com.instagrum.local.model.*
import java.util.concurrent.TimeUnit

class LocalNotifications(private val context: Context) {
    companion object {
        const val CHANNEL = "local_social_activity";
        const val EXTRA_ACTIVITY = "activity_id"

        fun startContinuous(context: Context) = GrowthService.start(context)
        fun stopContinuous(context: Context) = GrowthService.stop(context)
    }

    private val preferences = context.getSharedPreferences("notification_delivery", Context.MODE_PRIVATE)

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Likes, comments and followers",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Activity from fictional profiles in your private simulator"
                enableVibration(true)
            })
    }

    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun deliver(before: AppState, after: AppState) {
        val oldIds = before.activity.map { it.id }.toSet()
        val fresh = after.activity.filter { it.id !in oldIds }.reversed()
        if (fresh.isEmpty() || !after.settings.notificationsEnabled || !allowed()) return
        val last = preferences.getLong("last_alert", 0L)
        // One audible notification per short window; busy periods update a grouped card silently.
        val latest = fresh.last()
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_ACTIVITY, latest.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            latest.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val lines = fresh.takeLast(5).map { "${it.person.username} ${it.message()}" }
        val portrait = runCatching {
            context.assets.open("avatars/${latest.person.colorIndex.mod(24)}.jpg")
                .use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(latest.person.username)
            .setContentText(latest.message())
            .setSubText("InstaGRUM · private activity")
            .setContentIntent(pending).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(System.currentTimeMillis() - last < 15000)
            .setGroup("instagrum.activity")
        portrait?.let { builder.setLargeIcon(it) }
        if (lines.size > 1) builder.setStyle(
            NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) }
                .setSummaryText("${fresh.size} new interactions")
        )
        try {
            NotificationManagerCompat.from(context).notify(1001, builder.build())
            preferences.edit().putLong("last_alert", System.currentTimeMillis()).apply()
        } catch (_: SecurityException) { /* Permission can be revoked between the check and notify. */
        }
    }

    fun scheduleBackground(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) manager.enqueueUniquePeriodicWork(
            "private-activity", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ActivityWorker>(15, TimeUnit.MINUTES).build()
        )
        else manager.cancelUniqueWork("private-activity")
    }
}

class ActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        SimulatorRuntime.get(applicationContext).advanceBackground()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
