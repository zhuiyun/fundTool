package com.yunplayer.stockdashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockDashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoadPublishesDashboard() = runTest(dispatcher) {
        val viewModel = StockDashboardViewModel(FakeStockDataSource(), autoRefreshMillis = null)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("上证指数", state.dashboard?.indexes?.first()?.name)
        assertNull(state.error)
    }

    @Test
    fun selectingFundLoadsDetailAndBackClearsIt() = runTest(dispatcher) {
        val viewModel = StockDashboardViewModel(FakeStockDataSource(), autoRefreshMillis = null)
        advanceUntilIdle()

        viewModel.selectFund(7)
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.detail?.id)
        assertFalse(viewModel.uiState.value.detailLoading)

        viewModel.closeDetail()

        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun failedRefreshShowsRetryableError() = runTest(dispatcher) {
        val viewModel = StockDashboardViewModel(
            FakeStockDataSource(dashboardError = IllegalStateException("网络错误")),
            autoRefreshMillis = null
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals("网络错误", viewModel.uiState.value.error)
    }

    @Test
    fun expandTogglesHoldingVisibilityMode() = runTest(dispatcher) {
        val viewModel = StockDashboardViewModel(FakeStockDataSource(), autoRefreshMillis = null)

        assertFalse(viewModel.uiState.value.expanded)
        viewModel.toggleExpanded()
        assertTrue(viewModel.uiState.value.expanded)
    }

    @Test
    fun failedDetailCanRetryWithoutReturningToTheList() = runTest(dispatcher) {
        val dataSource = FlakyDetailDataSource()
        val viewModel = StockDashboardViewModel(dataSource, autoRefreshMillis = null)
        advanceUntilIdle()

        viewModel.selectFund(7)
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.selectedFundId)
        assertEquals("详情暂不可用", viewModel.uiState.value.detailError)

        viewModel.retryDetail()
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.detail?.id)
        assertNull(viewModel.uiState.value.detailError)
    }

    @Test
    fun goldPollingStartsImmediatelyAndRepeatsOncePerSecond() = runTest(dispatcher) {
        val goldSource = CountingGoldDataSource()
        val viewModel = StockDashboardViewModel(
            dataSource = FakeStockDataSource(),
            goldDataSource = goldSource,
            autoRefreshMillis = null,
            goldPollingMillis = 1_000L
        )

        viewModel.startGoldPolling()
        runCurrent()
        assertEquals(1, goldSource.calls)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, goldSource.calls)
        viewModel.stopGoldPolling()
    }

    @Test
    fun startingGoldPollingTwiceDoesNotCreateDuplicateLoops() = runTest(dispatcher) {
        val goldSource = CountingGoldDataSource()
        val viewModel = StockDashboardViewModel(
            dataSource = FakeStockDataSource(),
            goldDataSource = goldSource,
            autoRefreshMillis = null,
            goldPollingMillis = 1_000L
        )

        viewModel.startGoldPolling()
        viewModel.startGoldPolling()
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(2, goldSource.calls)
        viewModel.stopGoldPolling()
    }

    @Test
    fun stoppingGoldPollingPreventsFurtherRequests() = runTest(dispatcher) {
        val goldSource = CountingGoldDataSource()
        val viewModel = StockDashboardViewModel(
            dataSource = FakeStockDataSource(),
            goldDataSource = goldSource,
            autoRefreshMillis = null,
            goldPollingMillis = 1_000L
        )

        viewModel.startGoldPolling()
        runCurrent()
        viewModel.stopGoldPolling()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, goldSource.calls)
    }

    @Test
    fun refreshAllStaysRefreshingUntilDashboardAndGoldFinish() = runTest(dispatcher) {
        val viewModel = StockDashboardViewModel(
            dataSource = DelayedStockDataSource(100L),
            goldDataSource = CountingGoldDataSource(delayMillis = 200L),
            autoRefreshMillis = null
        )
        runCurrent()

        viewModel.refreshAll()
        runCurrent()
        assertTrue(viewModel.uiState.value.refreshing)

        advanceTimeBy(199L)
        runCurrent()
        assertTrue(viewModel.uiState.value.refreshing)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(788.12, viewModel.uiState.value.goldQuote?.price ?: 0.0, 0.0)
    }

    @Test
    fun failedGoldRefreshKeepsLastQuoteAndRecoversLater() = runTest(dispatcher) {
        val goldSource = SequencedGoldDataSource()
        val viewModel = StockDashboardViewModel(
            dataSource = FakeStockDataSource(),
            goldDataSource = goldSource,
            autoRefreshMillis = null
        )
        advanceUntilIdle()

        viewModel.refreshAll()
        advanceUntilIdle()
        val firstQuote = viewModel.uiState.value.goldQuote
        assertEquals(788.12, firstQuote?.price ?: 0.0, 0.0)

        viewModel.refreshAll()
        advanceUntilIdle()
        assertEquals(firstQuote, viewModel.uiState.value.goldQuote)
        assertEquals("金价网络错误", viewModel.uiState.value.goldError)

        viewModel.refreshAll()
        advanceUntilIdle()
        assertEquals(789.0, viewModel.uiState.value.goldQuote?.price ?: 0.0, 0.0)
        assertNull(viewModel.uiState.value.goldError)
    }
}

private class FakeStockDataSource(
    private val dashboardError: Throwable? = null
) : StockDataSource {
    override suspend fun fetchDashboard(): Dashboard {
        dashboardError?.let { throw it }
        return Dashboard(
            indexes = listOf(IndexImpact("上证指数", "0.32%")),
            funds = listOf(FundImpact(7, "示例基金", 1.2, "盘中")),
            timestamp = "2026-06-18 10:00",
            description = "实时估算",
            hiddenOvernight = false
        )
    }

    override suspend fun fetchDetail(id: Int): FundDetail {
        return FundDetail(
            id = id,
            name = "示例基金",
            estimatedImpact = 1.2,
            time = "盘中",
            holdings = listOf(Holding("示例股票", 5.0, 2.0))
        )
    }
}

private class FlakyDetailDataSource : StockDataSource {
    private var attempts = 0

    override suspend fun fetchDashboard(): Dashboard {
        return Dashboard(emptyList(), emptyList(), "", "", false)
    }

    override suspend fun fetchDetail(id: Int): FundDetail {
        attempts += 1
        if (attempts == 1) error("详情暂不可用")
        return FundDetail(id, "示例基金", 1.2, "盘中", emptyList())
    }
}

private class CountingGoldDataSource(
    private val delayMillis: Long = 0L
) : GoldDataSource {
    var calls: Int = 0
        private set

    override suspend fun fetchLatest(): GoldQuote {
        calls += 1
        if (delayMillis > 0) delay(delayMillis)
        return GoldQuote(788.12, 780.0, 8.12, 1.04, 1_718_700_000_000L)
    }
}

private class SequencedGoldDataSource : GoldDataSource {
    private var calls = 0

    override suspend fun fetchLatest(): GoldQuote {
        calls += 1
        return when (calls) {
            1 -> GoldQuote(788.12, 780.0, 8.12, 1.04, 1L)
            2 -> error("金价网络错误")
            else -> GoldQuote(789.0, 780.0, 9.0, 1.15, 2L)
        }
    }
}

private class DelayedStockDataSource(
    private val delayMillis: Long
) : StockDataSource {
    override suspend fun fetchDashboard(): Dashboard {
        delay(delayMillis)
        return Dashboard(emptyList(), emptyList(), "now", "", false)
    }

    override suspend fun fetchDetail(id: Int): FundDetail {
        return FundDetail(id, "", 0.0, "", emptyList())
    }
}
