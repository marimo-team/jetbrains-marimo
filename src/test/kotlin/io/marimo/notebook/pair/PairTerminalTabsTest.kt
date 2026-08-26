/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import io.marimo.notebook.pair.PairTerminalTabs.Action
import io.marimo.notebook.pair.PairTerminalTabs.Identity
import io.marimo.notebook.pair.PairTerminalTabs.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class PairTerminalTabsTest {
    @Test
    fun launchesFreshWhenNoTabMatchesNotebook() {
        val tabs = listOf(Tab(Identity("/a/nb.py", "claude"), alive = true))
        assertEquals(
            Action.Launch(closeIndex = null),
            PairTerminalTabs.resolve(tabs, "/b/nb.py", "claude"),
        )
    }

    @Test
    fun focusesLiveSessionForSameNotebookAndHarness() {
        val tabs =
            listOf(
                Tab(Identity("/a/nb.py", "claude"), alive = true),
                Tab(Identity("/b/nb.py", "claude"), alive = true),
            )
        assertEquals(Action.Focus(index = 1), PairTerminalTabs.resolve(tabs, "/b/nb.py", "claude"))
    }

    @Test
    fun liveClaudeTabDoesNotCaptureACodexRequest() {
        val tabs = listOf(Tab(Identity("/a/nb.py", "claude"), alive = true))
        assertEquals(
            Action.Launch(closeIndex = null),
            PairTerminalTabs.resolve(tabs, "/a/nb.py", "codex"),
        )
    }

    @Test
    fun relaunchesAfterClosingExitedSessionForTheSameHarness() {
        val tabs =
            listOf(
                Tab(Identity("/a/nb.py", "claude"), alive = true),
                Tab(Identity("/a/nb.py", "codex"), alive = false),
            )
        assertEquals(
            Action.Launch(closeIndex = 1),
            PairTerminalTabs.resolve(tabs, "/a/nb.py", "codex"),
        )
    }

    @Test
    fun sameFileNameInDifferentDirectoriesDoesNotCollide() {
        val tabs = listOf(Tab(Identity("/a/stocks.py", "claude"), alive = true))
        assertEquals(
            Action.Launch(closeIndex = null),
            PairTerminalTabs.resolve(tabs, "/b/stocks.py", "claude"),
        )
    }

    @Test
    fun ignoresUntaggedTabs() {
        val tabs = listOf(Tab(identity = null, alive = true))
        assertEquals(
            Action.Launch(closeIndex = null),
            PairTerminalTabs.resolve(tabs, "/a/nb.py", "claude"),
        )
    }
}
