package com.sillytavern.eink.eink

import org.junit.Assert.assertEquals
import org.junit.Test

class EinkThemeModeTest {
    @Test
    fun `missing settings default to balanced mode`() {
        assertEquals(EinkThemeMode.BALANCED, EinkThemeMode.resolve(null, null))
    }

    @Test
    fun `legacy enabled setting migrates without changing behavior`() {
        assertEquals(EinkThemeMode.BALANCED, EinkThemeMode.resolve(null, true))
        assertEquals(EinkThemeMode.OFF, EinkThemeMode.resolve(null, false))
    }

    @Test
    fun `explicit high contrast mode wins over the legacy flag`() {
        assertEquals(EinkThemeMode.HIGH_CONTRAST, EinkThemeMode.resolve("HIGH_CONTRAST", false))
    }

    @Test
    fun `unknown stored modes recover to balanced mode`() {
        assertEquals(EinkThemeMode.BALANCED, EinkThemeMode.resolve("future-mode", false))
    }
}
