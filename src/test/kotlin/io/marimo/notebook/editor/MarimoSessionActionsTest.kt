/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.session.MarimoLaunchContext
import io.marimo.notebook.session.MarimoSessionState
import io.marimo.notebook.session.SessionSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoSessionActionsTest {

    private fun snapshot(state: MarimoSessionState) =
        SessionSnapshot(
            fileUrl = "temp:///src/nb.py",
            fileName = "nb.py",
            state = state,
            attachedTabs = 0,
            expiresAtMillis = null,
            launch =
                MarimoLaunchContext(
                    port = 2718,
                    workDir = "/proj",
                    launcherId = "sdk",
                    sandbox = false,
                    tokenAuthEnabled = false,
                ),
            sandbox = false,
        )

    @Test
    fun liveStatesEnableRestartAndStop() {
        assertTrue(canControlSession(snapshot(MarimoSessionState.STARTING)))
        assertTrue(canControlSession(snapshot(MarimoSessionState.RUNNING)))
        assertTrue(canControlSession(snapshot(MarimoSessionState.STOPPING)))
    }

    @Test
    fun deadStatesDisableRestartAndStop() {
        assertFalse(canControlSession(snapshot(MarimoSessionState.STOPPED)))
        assertFalse(canControlSession(snapshot(MarimoSessionState.FAILED)))
    }

    @Test
    fun aMissingSessionDisablesRestartAndStop() {
        assertFalse("no session, nothing to control", canControlSession(null))
    }
}
