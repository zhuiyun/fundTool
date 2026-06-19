package com.yunplayer.stockdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val dashboard: Dashboard? = null,
    val selectedFundId: Int? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detail: FundDetail? = null,
    val expanded: Boolean = false,
    val refreshing: Boolean = false,
    val goldLoading: Boolean = false,
    val goldError: String? = null,
    val goldQuote: GoldQuote? = null
)

class StockDashboardViewModel(
    private val dataSource: StockDataSource,
    private val goldDataSource: GoldDataSource? = null,
    autoRefreshMillis: Long? = 60_000L,
    private val goldPollingMillis: Long = 1_000L,
    private val currentHour: () -> Int = { LocalTime.now().hour }
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = mutableUiState.asStateFlow()

    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var goldPollingJob: Job? = null
    private var refreshRequestId: Long = 0L

    init {
        refresh()
        if (autoRefreshMillis != null) {
            viewModelScope.launch {
                while (true) {
                    delay(autoRefreshMillis)
                    if (isUsTradingHours()) refresh()
                }
            }
        }
    }

    // 美股盘前(16:00 CST)至收盘(次日05:00 CST，兼容冬令时)
    private fun isUsTradingHours(): Boolean {
        val hour = currentHour()
        return hour >= 16 || hour < 5
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadDashboard()
        }
    }

    fun refreshAll() {
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableUiState.update { it.copy(refreshing = true) }
            try {
                coroutineScope {
                    launch { loadDashboard() }
                    launch { loadGold() }
                }
            } finally {
                if (refreshRequestId == requestId) {
                    mutableUiState.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    fun startGoldPolling() {
        if (goldDataSource == null || goldPollingJob?.isActive == true) return
        goldPollingJob = viewModelScope.launch {
            while (true) {
                loadGold()
                delay(goldPollingMillis)
            }
        }
    }

    fun stopGoldPolling() {
        goldPollingJob?.cancel()
        goldPollingJob = null
    }

    private suspend fun loadDashboard() {
        mutableUiState.update { it.copy(loading = it.dashboard == null, error = null) }
        runCatching { dataSource.fetchDashboard() }
            .onSuccess { dashboard ->
                mutableUiState.update {
                    it.copy(loading = false, error = null, dashboard = dashboard)
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                mutableUiState.update {
                    it.copy(loading = false, error = error.userMessage())
                }
            }
    }

    private suspend fun loadGold() {
        val source = goldDataSource ?: return
        mutableUiState.update {
            it.copy(goldLoading = it.goldQuote == null, goldError = null)
        }
        runCatching { source.fetchLatest() }
            .onSuccess { quote ->
                mutableUiState.update {
                    it.copy(goldLoading = false, goldError = null, goldQuote = quote)
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                mutableUiState.update {
                    it.copy(goldLoading = false, goldError = error.userMessage())
                }
            }
    }

    fun selectFund(id: Int) {
        mutableUiState.update { it.copy(selectedFundId = id) }
        loadDetail(id)
    }

    fun retryDetail() {
        mutableUiState.value.selectedFundId?.let(::loadDetail)
    }

    private fun loadDetail(id: Int) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(detailLoading = true, detailError = null, detail = null, expanded = false)
            }
            runCatching { dataSource.fetchDetail(id) }
                .onSuccess { detail ->
                    mutableUiState.update {
                        // 丢弃过期响应：用户可能已切换或关闭了详情
                        if (it.selectedFundId != id) return@update it
                        it.copy(detailLoading = false, detailError = null, detail = detail)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableUiState.update {
                        if (it.selectedFundId != id) return@update it
                        it.copy(detailLoading = false, detailError = error.userMessage())
                    }
                }
        }
    }

    fun closeDetail() {
        mutableUiState.update {
            it.copy(
                selectedFundId = null,
                detailLoading = false,
                detailError = null,
                detail = null,
                expanded = false
            )
        }
    }

    fun toggleExpanded() {
        mutableUiState.update { it.copy(expanded = !it.expanded) }
    }
}

private fun Throwable.userMessage(): String {
    val raw = message?.takeIf { it.isNotBlank() } ?: return "加载失败，请重试"
    return when {
        raw.contains("JsonReader") || raw.contains("malformed") || raw.contains("JSON", ignoreCase = true) ->
            "数据解析失败，请重试"
        raw.contains("timeout", ignoreCase = true) || raw.contains("SocketTimeout") ->
            "网络超时，请重试"
        raw.contains("UnknownHost") || raw.contains("Unable to resolve") || raw.contains("No address") ->
            "网络连接失败，请检查网络"
        raw.contains("鉴权") || raw.contains("401") || raw.contains("403") ->
            "鉴权失败，请稍后重试"
        raw.contains("服务器繁忙") || raw.contains("50") ->
            "服务器繁忙，请稍后重试"
        else -> "暂时无法加载，请重试"
    }
}
