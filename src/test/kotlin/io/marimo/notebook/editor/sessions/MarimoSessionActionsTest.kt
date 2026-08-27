/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.sessions

import io.marimo.notebook.session.MarimoLaunchContext
import io.marimo.notebook.session.MarimoSessionState
import io.marimo.notebook.session.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restart and Stop enablement is table-driven over every session state, including a missing
 * snapshot. Tool-window layout, JCEF notebook views, and click handlers stay out of this suite:
 * they need a realized browser and a live tool window, which the light fixture does not provide.
 */
class MarimoSessionActionsTest {

    private fun snapshot(state: MarimoSessionState, tokenAuthEnabled: Boolean = false) =
        SessionSnapshot(
            fileUrl = "temp:///src/nb.py",
            fileName = "nb.py",
            state = state,
            expiresAtMillis = null,
            launch =
                MarimoLaunchContext(
                    port = 2718,
                    workDir = "/proj",
                    launcherId = "sdk",
                    launcherInfo = null,
                    sandbox = false,
                    tokenAuthEnabled = tokenAuthEnabled,
                ),
            sandbox = false,
        )

    @Test
    fun restartAndStopEnablementCoversEverySessionState() {
        val expected =
            mapOf(
                MarimoSessionState.STARTING to true,
                MarimoSessionState.RUNNING to true,
                MarimoSessionState.STOPPED to false,
                MarimoSessionState.FAILED to false,
            )
        assertEquals(
            "every session state needs an explicit Restart/Stop decision",
            MarimoSessionState.entries.toSet(),
            expected.keys,
        )
        expected.forEach { (state, enabled) ->
            assertEquals(state.name, enabled, canControlSession(snapshot(state)))
        }
    }

    @Test
    fun aMissingSessionDisablesRestartAndStop() {
        assertFalse("no session, nothing to control", canControlSession(null))
    }

    @Test
    fun copyUrlFollowsLaunchAndTokenPolicy() {
        assertFalse(canCopySessionUrl(null))
        assertFalse(canCopySessionUrl(snapshot(MarimoSessionState.STARTING).copy(launch = null)))
        assertFalse(
            canCopySessionUrl(snapshot(MarimoSessionState.RUNNING, tokenAuthEnabled = true))
        )
        assertTrue(
            canCopySessionUrl(snapshot(MarimoSessionState.RUNNING, tokenAuthEnabled = false))
        )
    }
}
