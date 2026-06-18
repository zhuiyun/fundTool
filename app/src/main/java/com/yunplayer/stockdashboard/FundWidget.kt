package com.yunplayer.stockdashboard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FundWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val result = runCatching { StockRepository().fetchDashboard() }
            withContext(Dispatchers.Main) {
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(context, result.getOrNull()))
                }
                pending.finish()
            }
        }
    }

    companion object {
        fun buildViews(context: Context, dashboard: Dashboard?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            if (dashboard == null) {
                views.setTextViewText(R.id.widget_value, "更新失败")
                views.setTextColor(R.id.widget_value, 0xCCFFFFFF.toInt())
                views.setTextViewText(R.id.widget_sub, "")
                views.setTextViewText(R.id.widget_time, "")
                return views
            }

            val up = dashboard.funds.count { it.estimatedImpact > 0 }
            val down = dashboard.funds.count { it.estimatedImpact < 0 }
            val top = if (up >= down)
                dashboard.funds.maxByOrNull { it.estimatedImpact }
            else
                dashboard.funds.minByOrNull { it.estimatedImpact }

            views.setTextViewText(R.id.widget_value, "涨$up / 跌$down")
            views.setTextColor(
                R.id.widget_value,
                if (up >= down) 0xFFFFCCCC.toInt() else 0xFFCCFFEE.toInt()
            )
            views.setTextViewText(
                R.id.widget_sub,
                top?.let { "${it.name.take(5)} ${formatSignedPercent(it.estimatedImpact)}" } ?: ""
            )
            views.setTextViewText(R.id.widget_time, dashboard.timestamp)

            return views
        }
    }
}
