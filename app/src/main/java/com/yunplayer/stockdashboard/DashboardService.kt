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

    // private var lastDashboard: Dashboard? = null
    private var lastGold: GoldQuote? = null
    private var lastNasdaq: NasdaqQuote? = null

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
        startForeground(NOTIF_ID, buildNotification(readPrefs(), null))
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
        return START_NOT_STICKY
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                /*
                val dashboard = runCatching {
                    withContext(Dispatchers.IO) { StockRepository().fetchDashboard() }
                }.getOrNull()
                if (dashboard != null) lastDashboard = dashboard
                */
                val gold = runCatching {
                    withContext(Dispatchers.IO) { GoldRepository().fetchLatest() }
                }.getOrNull()
                if (gold != null) lastGold = gold

                val nasdaq = runCatching {
                    withContext(Dispatchers.IO) { IndexRepository().fetchNasdaq() }
                }.getOrNull()
                if (nasdaq != null) lastNasdaq = nasdaq

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
        val gold = lastGold
        val nasdaq = lastNasdaq

        val nasdaq100Row = v.findViewById<View>(R.id.float_nasdaq100_row)
        if (nasdaq != null) {
            nasdaq100Row.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.float_nasdaq100_value).apply {
                text = "${formatIndexPrice(nasdaq.price)}  ${formatSignedPercent(nasdaq.changePercent)}"
                setTextColor(floatTrendColor(nasdaq.changePercent))
            }
        } else {
            nasdaq100Row.visibility = View.GONE
        }

        val nasdaqRow = v.findViewById<View>(R.id.float_nasdaq_row)
        val futures = nasdaq?.futuresChangePercent
        if (futures != null) {
            nasdaqRow.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.float_nasdaq_value).apply {
                text = "期货 ${formatSignedPercent(futures)}"
                setTextColor(floatTrendColor(futures))
            }
        } else {
            nasdaqRow.visibility = View.GONE
        }

        val goldRow = v.findViewById<View>(R.id.float_gold_row)
        if (p.showGold && gold != null) {
            goldRow.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.float_gold_value).text =
                "${formatGoldPrice(gold.price)}  ${formatSignedNumber(gold.changeAmount)}"
        } else {
            goldRow.visibility = View.GONE
        }

        v.findViewById<View>(R.id.float_funds_row).visibility = View.GONE
    }

    private fun floatTrendColor(value: Double): Int = when {
        value > 0 -> 0xFF66BB6A.toInt()
        value < 0 -> 0xFFEF5350.toInt()
        else -> 0xFFAAAAAA.toInt()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun refreshNotification(p: Prefs) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(p, lastGold))
    }

    private fun buildNotification(p: Prefs, gold: GoldQuote?): Notification {
        val launchPi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        val showRich = p.showNotification || p.showLiveUpdate
        val nasdaq = lastNasdaq

        val chipParts = buildList {
            if (showRich) {
                if (nasdaq != null) add("纳指 ${formatSignedPercent(nasdaq.changePercent)}")
                if (p.showGold && gold != null) add("金 ${formatGoldPrice(gold.price)}")
            }
        }
        val chipTitle = chipParts.joinToString("  ·  ").ifEmpty { "热门美股" }

        val nativeBuilder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
            .setContentTitle(chipTitle)

        if (!showRich) {
            return nativeBuilder
                .setContentText(if (p.showFloat) "悬浮窗运行中" else "运行中")
                .build()
        }

        val inboxStyle = Notification.InboxStyle()
        if (nasdaq != null) {
            val stateLabel = when (nasdaq.marketState) {
                "PRE", "PREPRE" -> "盘前"
                "POST" -> "盘后"
                "REGULAR" -> "盘中"
                else -> ""
            }
            val label = if (stateLabel.isNotEmpty()) "纳斯达克100 $stateLabel" else "纳斯达克100"
            inboxStyle.addLine("$label   ${formatIndexPrice(nasdaq.price)}  ${formatSignedPercent(nasdaq.changePercent)}")
            if (nasdaq.futuresChangePercent != null) {
                inboxStyle.addLine("NQ期货   ${formatSignedPercent(nasdaq.futuresChangePercent)}")
            }
        }
        if (p.showGold && gold != null) {
            inboxStyle.addLine("现货黄金   ${formatGoldPrice(gold.price)}  ${formatSignedNumber(gold.changeAmount)}")
        }

        return nativeBuilder
            .setStyle(inboxStyle)
            .setSubText("热门美股")
            .build()
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
        val showGold: Boolean,
    )

    private fun readPrefs(): Prefs {
        val sp = getSharedPreferences("theme_preferences", MODE_PRIVATE)
        return Prefs(
            showFloat = sp.getBoolean("show_float", false),
            showNotification = sp.getBoolean("show_notification", false),
            showLiveUpdate = sp.getBoolean("show_live_update", false),
            showGold = sp.getBoolean("overlay_gold", true),
        )
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "热门美股", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "悬浮窗与常驻通知" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
