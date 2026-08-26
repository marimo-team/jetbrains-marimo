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

    data class Identity(val notebookPath: String, val harnessId: String)

    val IDENTITY_KEY: Key<Identity> = Key.create("io.marimo.pair.identity")

    data class Tab(val identity: Identity?, val alive: Boolean)

    sealed interface Action {
        /** Reuse the live session at [index]. */
        data class Focus(val index: Int) : Action

        /** Open a fresh session, first closing the stale tab at [closeIndex] when non-null. */
        data class Launch(val closeIndex: Int?) : Action
    }

    fun resolve(tabs: List<Tab>, notebookPath: String, harnessId: String): Action {
        val identity = Identity(notebookPath, harnessId)
        val match = tabs.indexOfFirst { it.identity == identity }
        return when {
            match < 0 -> Action.Launch(closeIndex = null)
            tabs[match].alive -> Action.Focus(match)
            else -> Action.Launch(closeIndex = match)
        }
    }
}
