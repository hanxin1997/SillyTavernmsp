package com.sillytavern.eink.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFullscreenStateTest {
    @Test
    fun normalModeShowsBrowserBarOnly() {
        val state = ReadingFullscreenState()

        assertTrue(state.browserBarVisible)
        assertFalse(state.restoreControlVisible)
    }

    @Test
    fun fullscreenShowsBottomRestoreControl() {
        val state = ReadingFullscreenState(enabled = true)

        assertFalse(state.browserBarVisible)
        assertTrue(state.restoreControlVisible)
    }

    @Test
    fun keyboardTemporarilyHidesRestoreControlWithoutLeavingFullscreen() {
        val state = ReadingFullscreenState(enabled = true, imeVisible = true)

        assertFalse(state.browserBarVisible)
        assertFalse(state.restoreControlVisible)
        assertTrue(state.enabled)
    }
}
