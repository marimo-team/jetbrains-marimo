package com.github.kirangadhave.marimopycharm.editor

import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoThemeTest {
    @Test fun darkInjectionSetsDarkSignals() {
        val js = MarimoTheme.injectionJs(dark = true)
        assertTrue(js.contains("dataset.theme = 'dark'"))
        assertTrue(js.contains("colorScheme = 'dark'"))
        assertTrue(js.contains("toggle('dark', true)"))
    }

    @Test fun lightInjectionSetsLightSignals() {
        val js = MarimoTheme.injectionJs(dark = false)
        assertTrue(js.contains("dataset.theme = 'light'"))
        assertTrue(js.contains("colorScheme = 'light'"))
        assertTrue(js.contains("toggle('dark', false)"))
    }
}
