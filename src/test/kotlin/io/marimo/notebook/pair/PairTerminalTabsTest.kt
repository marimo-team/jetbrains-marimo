/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import io.marimo.notebook.pair.PairTerminalTabs.Action
import io.marimo.notebook.pair.PairTerminalTabs.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class PairTerminalTabsTest {
    @Test fun launchesFreshWhenNoTabMatchesNotebook() {
        val tabs = listOf(Tab("/a/nb.py", alive = true))
        assertEquals(Action.Launch(closeIndex = null), PairTerminalTabs.resolve(tabs, "/b/nb.py"))
    }

    @Test fun focusesLiveSessionForSameNotebook() {
        val tabs = listOf(Tab("/a/nb.py", alive = true), Tab("/b/nb.py", alive = true))
        assertEquals(Action.Focus(index = 1), PairTerminalTabs.resolve(tabs, "/b/nb.py"))
    }

    @Test fun relaunchesAfterClosingExitedSession() {
        val tabs = listOf(Tab("/a/nb.py", alive = false))
        assertEquals(Action.Launch(closeIndex = 0), PairTerminalTabs.resolve(tabs, "/a/nb.py"))
    }

    @Test fun sameFileNameInDifferentDirectoriesDoesNotCollide() {
        val tabs = listOf(Tab("/a/stocks.py", alive = true))
        assertEquals(Action.Launch(closeIndex = null), PairTerminalTabs.resolve(tabs, "/b/stocks.py"))
    }

    @Test fun ignoresUntaggedTabs() {
        val tabs = listOf(Tab(null, alive = true))
        assertEquals(Action.Launch(closeIndex = null), PairTerminalTabs.resolve(tabs, "/a/nb.py"))
    }
}
