package com.yunplayer.stockdashboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardService : Service() {

    private lateinit var wm: WindowManager
    private var floatView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastDashboard: Dashboard? = null
    private var lastGold: GoldQuote? = null

    companion object {
        private val _floatRunning = MutableStateFlow(false)
        val floatRunning: StateFlow<Boolean> = _floatRunning.asStateFlow()

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

        private val _floatClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val floatClosed: SharedFlow<Unit> = _floatClosed.asSharedFlow()

        const val CHANNEL_ID = "dashboard_service"
        private const val NOTIF_ID = 1001

        fun update(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DashboardService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DashboardService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        _serviceRunning.value = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(readPrefs(), null, null))
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = readPrefs()
        if (!p.showFloat && !p.showNotification && !p.showLiveUpdate) {
            stopSelf()
            return START_NOT_STICKY
        }
        applyFloatState(p)
        refreshNotification(p)
        return START_STICKY
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                val dashboard = runCatching {
                    withContext(Dispatchers.IO) { StockRepository().fetchDashboard() }
                }.getOrNull()
                val gold = runCatching {
                    withContext(Dispatchers.IO) { GoldRepository().fetchLatest() }
                }.getOrNull()
                if (dashboard != null) lastDashboard = dashboard
                if (gold != null) lastGold = gold

                val p = readPrefs()
                applyFloatState(p)
                updateFloatContent(p)
                refreshNotification(p)
                delay(60_000L)
            }
        }
    }

    // ── Float window ──────────────────────────────────────────────────────────

    private fun applyFloatState(p: Prefs) {
        if (p.showFloat && floatView == null) attachFloat()
        else if (!p.showFloat && floatView != null) detachFloat()
        _floatRunning.value = floatView != null
    }

    private fun attachFloat() {
        val view = LayoutInflater.from(this).inflate(R.layout.float_window, null, false)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }

        var initX = 0; var initY = 0
        var touchX = 0f; var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX; val dy = event.rawY - touchY
                    if (moved || dx * dx + dy * dy > 100f) {
                        moved = true
                        params.x = (initX + dx).toInt().coerceAtLeast(0)
                        params.y = (initY + dy).toInt().coerceAtLeast(0)
                        wm.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val closeBtn = view.findViewById<View>(R.id.float_close)
                        val bounds = Rect(); closeBtn.getHitRect(bounds)
                        if (bounds.contains(event.x.toInt(), event.y.toInt())) {
                            getSharedPreferences("theme_preferences", MODE_PRIVATE)
                                .edit().putBoolean("show_float", false).apply()
                            detachFloat()
                            _floatRunning.value = false
                            _floatClosed.tryEmit(Unit)
                            val rp = readPrefs(); if (!rp.showNotification && !rp.showLiveUpdate) stopSelf()
                        } else {
                            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }?.let { startActivity(it) }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        floatView = view
        wm.addView(view, params)
        updateFloatContent(readPrefs())
    }

    private fun detachFloat() {
        floatView?.let { wm.removeView(it) }
        floatView = null
    }

    private fun updateFloatContent(p: Prefs) {
        val v = floatView ?: return
        val dashboard = lastDashboard
        val gold = lastGold

        val nasdaqEntries = dashboard?.let { findNasdaqEntries(it) } ?: emptyList()
        val composite = nasdaqEntries.getOrNull(0)
        val n100 = nasdaqEntries.getOrNull(1)

        val nasdaqRow = v.findViewById<View>(R.id.float_nasdaq_row)
        if (p.showNasdaq && composite != null) {
            nasdaqRow.visibility = View.VISIBLE
            val tv = v.findViewById<TextView>(R.id.float_nasdaq_value)
            tv.text = composite.changePercent
            tv.setTextColor(trendColorInt(parsePercentText(composite.changePercent)))
        } else {
            nasdaqRow.visibility = View.GONE
        }

        val nasdaq100Row = v.findViewById<View>(R.id.float_nasdaq100_row)
        if (p.showNasdaq && n100 != null) {
            nasdaq100Row.visibility = View.VISIBLE
            val tv100 = v.findViewById<TextView>(R.id.float_nasdaq100_value)
            tv100.text = n100.changePercent
            tv100.setTextColor(trendColorInt(parsePercentText(n100.changePercent)))
        } else {
            nasdaq100Row.visibility = View.GONE
        }

        val goldRow = v.findViewById<View>(R.id.float_gold_row)
        if (p.showGold && gold != null) {
            goldRow.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.float_gold_value).text =
                "${formatGoldPrice(gold.price)}  ${formatSignedNumber(gold.changeAmount)}"
        } else {
            goldRow.visibility = View.GONE
        }

        val fundsRow = v.findViewById<View>(R.id.float_funds_row)
        if (p.showFunds && dashboard != null) {
            fundsRow.visibility = View.VISIBLE
            val up = dashboard.funds.count { it.estimatedImpact > 0 }
            val down = dashboard.funds.count { it.estimatedImpact < 0 }
            val top = if (up >= down)
                dashboard.funds.maxByOrNull { it.estimatedImpact }
            else
                dashboard.funds.minByOrNull { it.estimatedImpact }
            val countTv = v.findViewById<TextView>(R.id.float_funds_count)
            countTv.text = "涨$up / 跌$down"
            countTv.setTextColor(if (up >= down) 0xFFEE6E70.toInt() else 0xFF34C77E.toInt())
            v.findViewById<TextView>(R.id.float_funds_top).text =
                top?.let { "${it.name.take(5)} ${formatSignedPercent(it.estimatedImpact)}" } ?: ""
        } else {
            fundsRow.visibility = View.GONE
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun refreshNotification(p: Prefs) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(p, lastDashboard, lastGold))
    }

    private fun buildNotification(p: Prefs, dashboard: Dashboard?, gold: GoldQuote?): Notification {
        val launchPi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        val showRich = p.showNotification || p.showLiveUpdate
        val nasdaqEntries = dashboard?.let { findNasdaqEntries(it) } ?: emptyList()

        val nativeBuilder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
            .setContentTitle("基金估值")

        if (!showRich) {
            return nativeBuilder
                .setContentText(if (p.showFloat) "悬浮窗运行中" else "运行中")
                .build()
        }

        // Compact one-liner shown in collapsed notification view
        val compactParts = buildList {
            if (p.showNasdaq && nasdaqEntries.isNotEmpty())
                add("纳 " + nasdaqEntries.joinToString(" / ") { it.changePercent })
            if (p.showGold && gold != null) add("金 ${formatGoldPrice(gold.price)}")
            if (p.showFunds && dashboard != null) {
                val up = dashboard.funds.count { it.estimatedImpact > 0 }
                val down = dashboard.funds.count { it.estimatedImpact < 0 }
                add("涨$up 跌$down")
            }
        }
        nativeBuilder.setContentText(compactParts.joinToString("  ·  ").ifEmpty { "加载中..." })

        val inboxStyle = Notification.InboxStyle()
        if (p.showNasdaq) {
            nasdaqEntries.forEach { inboxStyle.addLine("${it.name}   ${it.changePercent}") }
        }
        if (p.showGold && gold != null) {
            inboxStyle.addLine("现货黄金   ${formatGoldPrice(gold.price)}  ${formatSignedNumber(gold.changeAmount)}")
        }
        if (p.showFunds && dashboard != null) {
            val up = dashboard.funds.count { it.estimatedImpact > 0 }
            val down = dashboard.funds.count { it.estimatedImpact < 0 }
            val top = if (up >= down) dashboard.funds.maxByOrNull { it.estimatedImpact }
                      else dashboard.funds.minByOrNull { it.estimatedImpact }
            val topText = top?.let { "  ${it.name.take(5)} ${formatSignedPercent(it.estimatedImpact)}" } ?: ""
            inboxStyle.addLine("涨$up / 跌$down$topText")
        }
        // Use raw timestamp as summary — avoids "更新于 更新于 …" double prefix on ColorOS
        if (dashboard != null) inboxStyle.setSummaryText(dashboard.timestamp)

        if (p.showLiveUpdate) {
            // Remove API-36 gate: reflection calls fail gracefully on lower APIs,
            // so it's safe to attempt on any version OPPO ships Android 16 on.
            applyRequestPromotedOngoing(nativeBuilder)
            val metricApplied = tryBuildWithMetricStyle(nativeBuilder, p, nasdaqEntries, dashboard, gold)
            if (!metricApplied) nativeBuilder.setStyle(inboxStyle)
            val notif = nativeBuilder.build()
            // Belt-and-suspenders: some OEMs also need this set post-build
            runCatching { notif.extras.putBoolean("android.requestPromotedOngoing", true) }
            return notif
        }

        return nativeBuilder.setStyle(inboxStyle).build()
    }

    /**
     * Calls Notification.Builder.setRequestPromotedOngoing(true) via reflection — must happen
     * BEFORE build() for the Live Update chip to appear on OPPO ColorOS and other Android 16 OEMs.
     */
    private fun applyRequestPromotedOngoing(builder: Notification.Builder) {
        try {
            builder.javaClass
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            return
        } catch (_: Exception) { }
        // Fallback: inject via addExtras() before build()
        try {
            val b = Bundle()
            b.putBoolean("android.requestPromotedOngoing", true)
            builder.javaClass
                .getMethod("addExtras", Bundle::class.java)
                .invoke(builder, b)
        } catch (_: Exception) { }
    }

    /** Returns true if MetricStyle was applied successfully. */
    private fun tryBuildWithMetricStyle(
        builder: Notification.Builder,
        p: Prefs,
        nasdaqEntries: List<IndexImpact>,
        dashboard: Dashboard?,
        gold: GoldQuote?
    ): Boolean {
        return try {
            val msCls = Class.forName("android.app.Notification\$MetricStyle")
            val ms = msCls.getDeclaredConstructor().newInstance() as Notification.Style
            // setRequestPromotedOngoing is on Notification.Builder, NOT on MetricStyle —
            // it is handled by applyRequestPromotedOngoing() called before build().

            try {
                val mCls = Class.forName("android.app.Notification\$Metric")
                val mbCls = Class.forName("android.app.Notification\$Metric\$Builder")
                val ftCls = Class.forName("android.app.Notification\$Metric\$FixedText")
                val mvCls = Class.forName("android.app.Notification\$Metric\$MetricValue")

                fun metric(label: String, value: String): Any {
                    val mb = mbCls.getDeclaredConstructor().newInstance()
                    mbCls.getMethod("setLabel", CharSequence::class.java).invoke(mb, label)
                    val ft = ftCls.getDeclaredConstructor(CharSequence::class.java).newInstance(value)
                    mbCls.getMethod("setValue", mvCls).invoke(mb, ft)
                    return mbCls.getMethod("build").invoke(mb)!!
                }

                val items = buildList {
                    if (p.showNasdaq && nasdaqEntries.isNotEmpty()) {
                        add(metric("纳斯达克", nasdaqEntries[0].changePercent))
                        nasdaqEntries.getOrNull(1)?.let { add(metric("纳100", it.changePercent)) }
                    }
                    if (p.showGold && gold != null) add(metric("黄金", formatGoldPrice(gold.price)))
                    if (p.showFunds && dashboard != null) {
                        val up = dashboard.funds.count { it.estimatedImpact > 0 }
                        val down = dashboard.funds.count { it.estimatedImpact < 0 }
                        add(metric("涨跌", "涨$up 跌$down"))
                    }
                }
                if (items.isNotEmpty()) {
                    val arr = java.lang.reflect.Array.newInstance(mCls, items.size)
                    items.forEachIndexed { i, m -> java.lang.reflect.Array.set(arr, i, m) }
                    msCls.getMethod("setMetrics", arr.javaClass).invoke(ms, arr)
                }
            } catch (_: Exception) { /* metrics optional — chip still appears via requestPromotedOngoing */ }

            builder.setStyle(ms)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findNasdaqEntries(d: Dashboard): List<IndexImpact> {
        val all = d.indexes.filter {
            it.name.contains("纳斯达克") || it.name.contains("NASDAQ", ignoreCase = true)
        }
        val composite = all.firstOrNull { !it.name.contains("100") }
        val n100 = all.firstOrNull { it.name.contains("100") }
        return listOfNotNull(composite, n100)
    }

    private fun trendColorInt(v: Double) = when {
        v > 0 -> 0xFFEE6E70.toInt()
        v < 0 -> 0xFF34C77E.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onDestroy() {
        scope.cancel()
        detachFloat()
        _serviceRunning.value = false
        _floatRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── SharedPrefs read ──────────────────────────────────────────────────────

    private data class Prefs(
        val showFloat: Boolean,
        val showNotification: Boolean,
        val showLiveUpdate: Boolean,
        val showNasdaq: Boolean,
        val showGold: Boolean,
        val showFunds: Boolean
    )

    private fun readPrefs(): Prefs {
        val sp = getSharedPreferences("theme_preferences", MODE_PRIVATE)
        return Prefs(
            showFloat = sp.getBoolean("show_float", false),
            showNotification = sp.getBoolean("show_notification", false),
            showLiveUpdate = sp.getBoolean("show_live_update", false),
            showNasdaq = sp.getBoolean("overlay_nasdaq", true),
            showGold = sp.getBoolean("overlay_gold", true),
            showFunds = sp.getBoolean("overlay_funds", true)
        )
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "基金估值", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "悬浮窗与常驻通知" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
