package com.instagrum.local.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Resumes continuous growth after a reboot, but only if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context
            .getSharedPreferences("growth_service", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
        if (enabled) GrowthService.start(context)
    }
}
