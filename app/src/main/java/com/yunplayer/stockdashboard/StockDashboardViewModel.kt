package com.yunplayer.stockdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val gainers: List<HotStock> = emptyList(),
    val losers: List<HotStock> = emptyList(),
    val actives: List<HotStock> = emptyList(),
    val activeTab: StockTab = StockTab.Gainers,
    val refreshing: Boolean = false,
    val goldLoading: Boolean = false,
    val goldError: String? = null,
    val goldQuote: GoldQuote? = null,
    val mainTab: MainTab = MainTab.HotStocks,
    val qdiiLoading: Boolean = false,
    val qdiiEstimates: List<QdiiEstimate> = emptyList(),
    val qdiiError: String? = null,
    // The market state observed during the last QDII refresh (e.g. "PRE", "REGULAR")
    val qdiiMarketState: String? = null,
    val nasdaqQuote: NasdaqQuote? = null,
    val nasdaqLoading: Boolean = false,
)

class StockDashboardViewModel(
    private val dataSource: HotStocksSource,
    private val goldDataSource: GoldDataSource? = null,
    private val qdiiDataSource: QdiiFundRepository? = null,
    private val indexDataSource: IndexRepository? = null,
    autoRefreshMillis: Long? = 60_000L,
    private val goldPollingMillis: Long = 1_000L,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = mutableUiState.asStateFlow()

    private var refreshJob: Job? = null
    private var goldPollingJob: Job? = null
    private var qdiiJob: Job? = null
    private var qdiiLoaded = false

    init {
        refresh()
        if (autoRefreshMillis != null) {
            viewModelScope.launch {
                while (true) {
                    delay(autoRefreshMillis)
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            coroutineScope {
                launch { loadAllTabs() }
                launch { loadNasdaq() }
                if (qdiiLoaded) launch { loadQdii() }
            }
        }
    }

    fun refreshAll() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableUiState.update { it.copy(refreshing = true) }
            try {
                coroutineScope {
                    launch { loadAllTabs() }
                    launch { loadGold() }
                    launch { loadNasdaq() }
                    if (qdiiLoaded) launch { loadQdii() }
                }
            } finally {
                mutableUiState.update { it.copy(refreshing = false) }
            }
        }
    }

    fun selectTab(tab: StockTab) {
        mutableUiState.update { it.copy(activeTab = tab) }
    }

    fun selectMainTab(tab: MainTab) {
        mutableUiState.update { it.copy(mainTab = tab) }
        if (tab == MainTab.Qdii && !qdiiLoaded) {
            loadQdii()
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

    private suspend fun loadAllTabs() {
        val hasData = mutableUiState.value.let {
            it.gainers.isNotEmpty() || it.losers.isNotEmpty() || it.actives.isNotEmpty()
        }
        mutableUiState.update { it.copy(loading = !hasData, error = null) }
        runCatching {
            coroutineScope {
                val g = async { dataSource.fetch(StockTab.Gainers) }
                val l = async { dataSource.fetch(StockTab.Losers) }
                val a = async { dataSource.fetch(StockTab.Actives) }
                Triple(g.await(), l.await(), a.await())
            }
        }.onSuccess { (gainers, losers, actives) ->
            mutableUiState.update {
                it.copy(loading = false, error = null, gainers = gainers, losers = losers, actives = actives)
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            mutableUiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }

    private suspend fun loadGold() {
        val source = goldDataSource ?: return
        mutableUiState.update { it.copy(goldLoading = it.goldQuote == null, goldError = null) }
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

    private suspend fun loadNasdaq() {
        val source = indexDataSource ?: return
        mutableUiState.update { it.copy(nasdaqLoading = it.nasdaqQuote == null) }
        runCatching { source.fetchNasdaq() }
            .onSuccess { quote ->
                mutableUiState.update { it.copy(nasdaqLoading = false, nasdaqQuote = quote) }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                mutableUiState.update { it.copy(nasdaqLoading = false) }
            }
    }

    fun loadQdii() {
        val repo = qdiiDataSource ?: return
        qdiiJob?.cancel()
        qdiiJob = viewModelScope.launch {
            mutableUiState.update { it.copy(qdiiLoading = true, qdiiError = null) }
            runCatching {
                coroutineScope {
                    QdiiFundRepository.FUNDS
                        .map { fund -> async { repo.fetchEstimate(fund) } }
                        .awaitAll()
                        .sortedByDescending { it.estimatedChangePercent ?: -Double.MAX_VALUE }
                }
            }.onSuccess { estimates ->
                qdiiLoaded = true
                mutableUiState.update {
                    it.copy(qdiiLoading = false, qdiiEstimates = estimates, qdiiMarketState = null)
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                mutableUiState.update { it.copy(qdiiLoading = false, qdiiError = e.userMessage()) }
            }
        }
    }
}

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "加载失败，请重试"
