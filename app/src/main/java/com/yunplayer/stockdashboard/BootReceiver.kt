package com.yunplayer.stockdashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val sp = context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
        val needed = sp.getBoolean("show_float", false) ||
                sp.getBoolean("show_notification", false) ||
                sp.getBoolean("show_live_update", false)
        if (needed) {
            context.startForegroundService(Intent(context, DashboardService::class.java))
        }
    }
}
