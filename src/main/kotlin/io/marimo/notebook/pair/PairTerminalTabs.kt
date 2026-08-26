/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.openapi.util.Key

/**
 * Decides whether a "Pair with marimo" launch reuses an existing terminal tab or opens a new one.
 *
 * Tabs are matched by notebook path and harness id, not by the visible tab title: two notebooks
 * that share a file name, or two harnesses on the same notebook, must not resolve to each other's
 * session. A matched tab is only reused when its shell is still alive — a tab left behind by an
 * exited session is closed and relaunched.
 */
internal object PairTerminalTabs {

    /** File path of the notebook a pair terminal tab was opened for. */
    val NOTEBOOK_KEY: Key<String> = Key.create("io.marimo.pair.notebook")

    /** Harness id (claude, codex, opencode) the tab was opened for. */
    val HARNESS_KEY: Key<String> = Key.create("io.marimo.pair.harness")

    data class Tab(val notebookPath: String?, val harnessId: String?, val alive: Boolean)

    /** Identity stored on the terminal content for a notebook-plus-harness session. */
    fun contentKey(notebookPath: String, harnessId: String): Pair<String, String> =
        notebookPath to harnessId

    sealed interface Action {
        /** Reuse the live session at [index]. */
        data class Focus(val index: Int) : Action

        /** Open a fresh session, first closing the stale tab at [closeIndex] when non-null. */
        data class Launch(val closeIndex: Int?) : Action
    }

    fun resolve(tabs: List<Tab>, notebookPath: String, harnessId: String): Action {
        val match = tabs.indexOfFirst {
            it.notebookPath == notebookPath && it.harnessId == harnessId
        }
        return when {
            match < 0 -> Action.Launch(closeIndex = null)
            tabs[match].alive -> Action.Focus(match)
            else -> Action.Launch(closeIndex = match)
        }
    }
}
