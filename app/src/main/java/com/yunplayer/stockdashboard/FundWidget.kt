package com.yunplayer.stockdashboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FundWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // val dashboard = runCatching { StockRepository().fetchDashboard() }.getOrNull()
            val gold = runCatching { GoldRepository().fetchLatest() }.getOrNull()
            val prefs = loadWidgetPrefs(context)
            withContext(Dispatchers.Main) {
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(context, prefs, gold))
                }
                pending.finish()
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, FundWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, FundWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        private data class WidgetPrefs(
            val showGold: Boolean,
        )

        private fun loadWidgetPrefs(context: Context): WidgetPrefs {
            val prefs = context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
            return WidgetPrefs(
                showGold = prefs.getBoolean("overlay_gold", true),
            )
        }

        private fun buildViews(
            context: Context,
            prefs: WidgetPrefs,
            gold: GoldQuote?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_time, timeStr)

            // Nasdaq row hidden (no longer fetching index data)
            views.setViewVisibility(R.id.widget_nasdaq_row, View.GONE)

            // Gold row
            views.setViewVisibility(R.id.widget_gold_row, if (prefs.showGold) View.VISIBLE else View.GONE)
            if (prefs.showGold) {
                if (gold != null) {
                    views.setTextViewText(R.id.widget_gold_price, formatGoldPrice(gold.price))
                    views.setTextViewText(R.id.widget_gold_change, formatSignedPercent(gold.changePercent))
                    views.setTextColor(R.id.widget_gold_change, trendColor(gold.changePercent))
                } else {
                    views.setTextViewText(R.id.widget_gold_price, "--")
                    views.setTextViewText(R.id.widget_gold_change, "")
                }
            }

            // Funds row hidden (no longer fetching fund data)
            views.setViewVisibility(R.id.widget_funds_row, View.GONE)

            return views
        }

        private fun trendColor(v: Double): Int = when {
            v > 0 -> 0xFFFF9999.toInt()
            v < 0 -> 0xFF99FFCC.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }
}
