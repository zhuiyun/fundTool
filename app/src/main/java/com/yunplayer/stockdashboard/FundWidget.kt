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
            val dashboard = runCatching { StockRepository().fetchDashboard() }.getOrNull()
            val gold = runCatching { GoldRepository().fetchLatest() }.getOrNull()
            val prefs = loadWidgetPrefs(context)
            withContext(Dispatchers.Main) {
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(context, prefs, dashboard, gold))
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
            val showNasdaq: Boolean,
            val showGold: Boolean,
            val showFunds: Boolean
        )

        private fun loadWidgetPrefs(context: Context): WidgetPrefs {
            val prefs = context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
            return WidgetPrefs(
                showNasdaq = prefs.getBoolean("overlay_nasdaq", true),
                showGold = prefs.getBoolean("overlay_gold", true),
                showFunds = prefs.getBoolean("overlay_funds", true)
            )
        }

        fun buildViews(
            context: Context,
            prefs: WidgetPrefs = loadWidgetPrefs(context),
            dashboard: Dashboard?,
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

            // Nasdaq row
            val nasdaq = dashboard?.let { findNasdaq(it) }
            val showNasdaqRow = prefs.showNasdaq && nasdaq != null
            views.setViewVisibility(R.id.widget_nasdaq_row, if (showNasdaqRow) View.VISIBLE else View.GONE)
            if (showNasdaqRow && nasdaq != null) {
                val pct = parsePercentText(nasdaq.changePercent)
                views.setTextViewText(R.id.widget_nasdaq_value, nasdaq.changePercent)
                views.setTextColor(R.id.widget_nasdaq_value, trendColor(pct))
            }

            // Gold row
            views.setViewVisibility(R.id.widget_gold_row, if (prefs.showGold) View.VISIBLE else View.GONE)
            if (prefs.showGold) {
                if (gold != null) {
                    views.setTextViewText(R.id.widget_gold_price, formatGoldPrice(gold.price))
                    val change = gold.changePercent
                    views.setTextViewText(R.id.widget_gold_change, formatSignedPercent(change))
                    views.setTextColor(R.id.widget_gold_change, trendColor(change))
                } else {
                    views.setTextViewText(R.id.widget_gold_price, "--")
                    views.setTextViewText(R.id.widget_gold_change, "")
                }
            }

            // Funds row
            views.setViewVisibility(R.id.widget_funds_row, if (prefs.showFunds) View.VISIBLE else View.GONE)
            if (prefs.showFunds) {
                if (dashboard != null) {
                    val up = dashboard.funds.count { it.estimatedImpact > 0 }
                    val down = dashboard.funds.count { it.estimatedImpact < 0 }
                    val net = up - down
                    views.setTextViewText(R.id.widget_funds_count, "涨$up / 跌$down")
                    views.setTextColor(R.id.widget_funds_count, trendColor(net.toDouble()))
                    val top = if (up >= down)
                        dashboard.funds.maxByOrNull { it.estimatedImpact }
                    else
                        dashboard.funds.minByOrNull { it.estimatedImpact }
                    views.setTextViewText(
                        R.id.widget_funds_top,
                        top?.let { " ${it.name.take(6)} ${formatSignedPercent(it.estimatedImpact)}" } ?: ""
                    )
                } else {
                    views.setTextViewText(R.id.widget_funds_count, "--")
                    views.setTextViewText(R.id.widget_funds_top, "")
                }
            }

            return views
        }

        private fun findNasdaq(d: Dashboard): IndexImpact? =
            d.indexes.firstOrNull {
                it.name.contains("纳斯达克") || it.name.contains("NASDAQ", ignoreCase = true)
            }

        private fun trendColor(v: Double): Int = when {
            v > 0 -> 0xFFFF9999.toInt()
            v < 0 -> 0xFF99FFCC.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }
}
