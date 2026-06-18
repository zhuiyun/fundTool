package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeViewModelTest {
    @Test
    fun initialModeLoadsFromStore() {
        val store = FakeThemePreferenceStore(ThemeMode.Light)

        val viewModel = ThemeViewModel(store)

        assertEquals(ThemeMode.Light, viewModel.mode.value)
    }

    @Test
    fun selectionUpdatesStateAndPersists() {
        val store = FakeThemePreferenceStore(ThemeMode.FollowSystem)
        val viewModel = ThemeViewModel(store)

        viewModel.select(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, viewModel.mode.value)
        assertEquals(ThemeMode.Dark, store.savedMode)
    }
}

private class FakeThemePreferenceStore(
    private val initialMode: ThemeMode
) : ThemePreferenceStore {
    var savedMode: ThemeMode? = null

    override fun load(): ThemeMode = initialMode

    override fun save(mode: ThemeMode) {
        savedMode = mode
    }
}
