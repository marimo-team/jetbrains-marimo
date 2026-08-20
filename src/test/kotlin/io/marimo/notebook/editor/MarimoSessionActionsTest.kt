/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.server.MarimoLaunchContext
import io.marimo.notebook.server.MarimoSessionSnapshot
import io.marimo.notebook.server.MarimoSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoSessionActionsTest {

    private fun snapshot(state: MarimoSessionState) = MarimoSessionSnapshot(
        fileUrl = "temp:///src/nb.py",
        fileName = "nb.py",
        state = state,
        attachedTabs = 0,
        expiresAtMillis = null,
        launch = MarimoLaunchContext(
            port = 2718,
            workDir = "/proj",
            launcherId = "sdk",
            sandbox = false,
            tokenAuthEnabled = false,
        ),
        sandbox = false,
    )

    @Test fun liveStatesEnableRestartAndStop() {
        assertTrue(canControlSession(snapshot(MarimoSessionState.STARTING)))
        assertTrue(canControlSession(snapshot(MarimoSessionState.RUNNING)))
        assertTrue(canControlSession(snapshot(MarimoSessionState.STOPPING)))
    }

    @Test fun deadStatesDisableRestartAndStop() {
        assertFalse(canControlSession(snapshot(MarimoSessionState.STOPPED)))
        assertFalse(canControlSession(snapshot(MarimoSessionState.FAILED)))
    }

    @Test fun aMissingSessionDisablesRestartAndStop() {
        assertFalse("no session, nothing to control", canControlSession(null))
    }
}
