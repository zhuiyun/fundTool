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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.Build
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
        const val CHANNEL_LIVE_ID = "dashboard_live_update"  // legacy, kept for deletion
        const val CHANNEL_CHIP_ID = "dashboard_chip"
        private const val NOTIF_ID = 1001
        private const val NOTIF_ID_CHIP = 1002

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
        startForeground(NOTIF_ID, buildServiceNotification())
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
        if (p.showNasdaq100 && n100 != null) {
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_CHIP)
        val launchPi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = when {
            p.showLiveUpdate -> buildChipNotification(p, lastDashboard, lastGold)
            p.showNotification -> buildInboxNotification(launchPi, p, lastDashboard, lastGold)
            else -> buildServiceNotification()
        }
        startForeground(NOTIF_ID, notif)
        // OPPO ColorOS may only re-render the chip pill on a NotificationManager.notify()
        // event rather than startForeground(), so post via both paths on every refresh.
        nm.notify(NOTIF_ID, notif)
    }

    private fun buildServiceNotification(): Notification {
        val launchPi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("基金估值")
            .setContentText("服务运行中")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
            .build()
    }

    private fun buildChipNotification(p: Prefs, dashboard: Dashboard?, gold: GoldQuote?): Notification {
        val launchPi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val nasdaqEntries = dashboard?.let { findNasdaqEntries(it) } ?: emptyList()
        fun signedEntry(e: IndexImpact, prefix: String): String {
            val v = parsePercentText(e.changePercent)
            return "$prefix${(if (v >= 0) "+" else "")}${e.changePercent.trimStart('+', '-')}"
        }
        // Chip pill shows only the single most prominent value (no merging)
        val chipTitle = when {
            p.showNasdaq && nasdaqEntries.getOrNull(0) != null ->
                signedEntry(nasdaqEntries[0], "纳")
            p.showNasdaq100 && nasdaqEntries.getOrNull(1) != null ->
                signedEntry(nasdaqEntries[1], "百")
            p.showGold && gold != null -> "金${gold.price.toInt()}"
            p.showFunds && dashboard != null -> {
                val up = dashboard.funds.count { it.estimatedImpact > 0 }
                val down = dashboard.funds.count { it.estimatedImpact < 0 }
                "涨${up}跌${down}"
            }
            else -> "行情"
        }
        val builder = Notification.Builder(this, CHANNEL_CHIP_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(chipTitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
        return buildMetricNotification(builder, p, nasdaqEntries, dashboard, gold)
    }

    private fun buildMetricNotification(
        builder: Notification.Builder,
        p: Prefs,
        nasdaqEntries: List<IndexImpact>,
        dashboard: Dashboard?,
        gold: GoldQuote?
    ): Notification {
        // Confirmed available on OPPO ColorOS 16 via reflection; promotes notification to chip
        runCatching {
            builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }

        // Try MetricStyle for stock Android 16 (will fail with ClassNotFoundException on OPPO)
        val metricStyleApplied = try {
            val msCls = Class.forName("android.app.Notification\$MetricStyle")
            val style = msCls.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
            runCatching {
                msCls.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(style, true)
            }
            runCatching {
                val mCls  = Class.forName("android.app.Notification\$Metric")
                val mbCls = Class.forName("android.app.Notification\$Metric\$Builder")
                val ftCls = Class.forName("android.app.Notification\$Metric\$FixedText")
                val mvCls = Class.forName("android.app.Notification\$Metric\$MetricValue")
                fun buildMetric(label: String, value: String): Any? = runCatching {
                    val mb = mbCls.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
                    mbCls.getMethod("setLabel", CharSequence::class.java).invoke(mb, label)
                    val ft = ftCls.getDeclaredConstructor(CharSequence::class.java).also { it.isAccessible = true }.newInstance(value)
                    mbCls.getMethod("setValue", mvCls).invoke(mb, ft)
                    mbCls.getMethod("build").invoke(mb)
                }.getOrNull()
                val items = buildList {
                    nasdaqEntries.getOrNull(0)?.takeIf { p.showNasdaq }?.let { e ->
                        buildMetric("纳斯达克", e.changePercent)?.let { add(it) }
                    }
                    nasdaqEntries.getOrNull(1)?.takeIf { p.showNasdaq100 }?.let { e ->
                        buildMetric("纳100", e.changePercent)?.let { add(it) }
                    }
                    if (p.showGold && gold != null)
                        buildMetric("黄金", formatGoldPrice(gold.price))?.let { add(it) }
                    if (p.showFunds && dashboard != null) {
                        val up   = dashboard.funds.count { it.estimatedImpact > 0 }
                        val down = dashboard.funds.count { it.estimatedImpact < 0 }
                        buildMetric("基金", "涨$up / 跌$down")?.let { add(it) }
                    }
                }
                if (items.isNotEmpty()) {
                    val arr = java.lang.reflect.Array.newInstance(mCls, items.size)
                    items.forEachIndexed { i, m -> java.lang.reflect.Array.set(arr, i, m) }
                    msCls.getMethod("setMetrics", arr.javaClass).invoke(style, arr)
                }
            }
            builder.setStyle(style as Notification.Style)
            true
        } catch (_: Exception) {
            // OPPO only promotes chip for BigTextStyle; InboxStyle breaks chip display.
            // Shared update time goes into setBigContentTitle so it appears in the title row.
            // Gold+funds shown first (within OPPO's ~2-line visible limit); nasdaq below.
            val COLOR_LABEL = 0xFF90CAF9.toInt()
            val COLOR_UP    = 0xFFEE6E70.toInt()
            val COLOR_DOWN  = 0xFF34C77E.toInt()
            val COLOR_TIME  = 0xFF999999.toInt()
            fun signedColor(v: Double) = if (v > 0) COLOR_UP else if (v < 0) COLOR_DOWN else COLOR_LABEL
            fun signedPct(raw: String, v: Double) = (if (v >= 0) "+" else "") + raw.trimStart('+', '-')
            fun SpannableStringBuilder.ac(text: String, color: Int): SpannableStringBuilder {
                val s = length; append(text)
                setSpan(ForegroundColorSpan(color), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return this
            }

            // Shared update time: prefer dashboard timestamp, fall back to gold's clock
            val updateTime = dashboard?.timestamp?.takeIf { it.isNotEmpty() }
                ?: gold?.let { formatClockTime(it.updatedAtMillis).take(5) }

            val sb = SpannableStringBuilder()
            // Gold first — no inline timestamp (shared time shown in title row)
            if (p.showGold && gold != null) {
                val c = gold.changeAmount
                val absStr = String.format(java.util.Locale.US, "%.2f", if (c < 0) -c else c)
                if (sb.isNotEmpty()) sb.append("\n")
                sb.ac("现货黄金  ", COLOR_LABEL)
                  .ac(formatGoldPrice(gold.price), COLOR_LABEL)
                  .ac("  ", COLOR_LABEL)
                  .ac(if (c >= 0) "+$absStr" else "-$absStr", signedColor(c))
            }
            // Funds second — no inline timestamp
            if (p.showFunds && dashboard != null) {
                val up = dashboard.funds.count { it.estimatedImpact > 0 }
                val down = dashboard.funds.count { it.estimatedImpact < 0 }
                val top = if (up >= down) dashboard.funds.maxByOrNull { it.estimatedImpact }
                          else dashboard.funds.minByOrNull { it.estimatedImpact }
                if (sb.isNotEmpty()) sb.append("\n")
                sb.ac("基金  ", COLOR_LABEL)
                  .ac("涨$up", COLOR_UP)
                  .ac(" / ", COLOR_LABEL)
                  .ac("跌$down", COLOR_DOWN)
                if (top != null) {
                    val v = top.estimatedImpact
                    val pctAbs = formatPercent(if (v < 0) -v else v)
                    sb.ac("  ${top.name.take(5)} ", COLOR_LABEL)
                      .ac(if (v >= 0) "+$pctAbs" else "-$pctAbs", signedColor(v))
                }
            } else if (p.showFunds) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.ac("基金  加载中...", COLOR_TIME)
            }
            // Nasdaq below — already shown in chip pill, lower priority for expanded view
            if (p.showNasdaq) nasdaqEntries.getOrNull(0)?.let { e ->
                val v = parsePercentText(e.changePercent)
                if (sb.isNotEmpty()) sb.append("\n")
                sb.ac("纳斯达克  ", COLOR_LABEL).ac(signedPct(e.changePercent, v), signedColor(v))
            }
            if (p.showNasdaq100) nasdaqEntries.getOrNull(1)?.let { e ->
                val v = parsePercentText(e.changePercent)
                if (sb.isNotEmpty()) sb.append("\n")
                sb.ac("纳斯达克100  ", COLOR_LABEL).ac(signedPct(e.changePercent, v), signedColor(v))
            }

            val bigContentTitle = if (updateTime != null) "更新 $updateTime" else " "
            val bigText: CharSequence = if (sb.isEmpty()) "加载中..." else sb
            val compactText = buildList {
                if (p.showNasdaq) nasdaqEntries.getOrNull(0)?.let { add("纳 ${it.changePercent}") }
                if (p.showNasdaq100) nasdaqEntries.getOrNull(1)?.let { add("百 ${it.changePercent}") }
                if (p.showGold && gold != null) add("金 ${formatGoldPrice(gold.price)}")
                if (p.showFunds && dashboard != null) {
                    val up = dashboard.funds.count { it.estimatedImpact > 0 }
                    val down = dashboard.funds.count { it.estimatedImpact < 0 }
                    add("涨$up 跌$down")
                }
            }.joinToString("  ·  ").ifEmpty { "加载中..." }
            builder
                .setStyle(Notification.BigTextStyle().setBigContentTitle(bigContentTitle).bigText(bigText))
                .setContentText(compactText)
            false
        }

        val notif = builder.build()
        // Post-build extras as final fallback
        runCatching { notif.extras.putBoolean("android.requestPromotedOngoing", true) }
        return notif
    }


    private fun buildInboxNotification(launchPi: PendingIntent, p: Prefs, dashboard: Dashboard?, gold: GoldQuote?): Notification {
        val nasdaqEntries = dashboard?.let { findNasdaqEntries(it) } ?: emptyList()

        val compactParts = buildList {
            val nasdaqParts2 = buildList {
                if (p.showNasdaq) nasdaqEntries.getOrNull(0)?.let { add("纳 ${it.changePercent}") }
                if (p.showNasdaq100) nasdaqEntries.getOrNull(1)?.let { add(it.changePercent) }
            }
            if (nasdaqParts2.isNotEmpty()) add(nasdaqParts2.joinToString("/"))
            if (p.showGold && gold != null) add("金 ${formatGoldPrice(gold.price)}")
            if (p.showFunds && dashboard != null) {
                val up = dashboard.funds.count { it.estimatedImpact > 0 }
                val down = dashboard.funds.count { it.estimatedImpact < 0 }
                add("涨$up 跌$down")
            }
        }

        val inboxStyle = Notification.InboxStyle()
        if (p.showNasdaq) nasdaqEntries.getOrNull(0)?.let { inboxStyle.addLine("${it.name}   ${it.changePercent}") }
        if (p.showNasdaq100) nasdaqEntries.getOrNull(1)?.let { inboxStyle.addLine("${it.name}   ${it.changePercent}") }
        if (p.showGold && gold != null)
            inboxStyle.addLine("现货黄金   ${formatGoldPrice(gold.price)}  ${formatSignedNumber(gold.changeAmount)}")
        if (p.showFunds && dashboard != null) {
            val up = dashboard.funds.count { it.estimatedImpact > 0 }
            val down = dashboard.funds.count { it.estimatedImpact < 0 }
            val top = if (up >= down) dashboard.funds.maxByOrNull { it.estimatedImpact }
                      else dashboard.funds.minByOrNull { it.estimatedImpact }
            val topText = top?.let { "  ${it.name.take(5)} ${formatSignedPercent(it.estimatedImpact)}" } ?: ""
            inboxStyle.addLine("涨$up / 跌$down$topText")
        }
        if (dashboard != null) inboxStyle.setSummaryText(dashboard.timestamp)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
            .setContentTitle("基金估值")
            .setContentText(compactParts.joinToString("  ·  ").ifEmpty { "加载中..." })
            .setStyle(inboxStyle)
            .build()
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
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_CHIP)
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
        val showNasdaq100: Boolean,
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
            showNasdaq100 = sp.getBoolean("overlay_nasdaq_100", true),
            showGold = sp.getBoolean("overlay_gold", true),
            showFunds = sp.getBoolean("overlay_funds", true)
        )
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "基金估值", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "悬浮窗与常驻通知" }
        )
        // Chip is promoted via setRequestPromotedOngoing, not channel importance.
        // Low importance keeps the shade notification silent/minimal.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHIP_ID, "流体云", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "状态栏流体云胶囊"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
        )
        // Remove legacy high-importance channel
        nm.deleteNotificationChannel(CHANNEL_LIVE_ID)
    }
}
