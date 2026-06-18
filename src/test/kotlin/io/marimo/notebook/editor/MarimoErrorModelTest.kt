/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoPresence
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.UvUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoErrorModelTest {

    private fun of(failure: MarimoFailure, presence: MarimoPresence, uvAvailable: Boolean = true) =
        MarimoErrorModel.of(failure, presence, uvAvailable)

    @Test fun missingMarimoOffersInstall() {
        val model = of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue("install offered when marimo is missing", model.actions.contains(MarimoErrorAction.INSTALL))
    }

    @Test fun installedMarimoDoesNotOfferInstall() {
        val model = of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Installed("0.1.0"))
        assertFalse("no install when marimo is present", model.actions.contains(MarimoErrorAction.INSTALL))
    }

    @Test fun noInterpreterTakesPrecedenceOverInstall() {
        val model = of(MarimoFailure.ServerNotStarted(NoInterpreterException("none")), MarimoPresence.Unknown)
        assertFalse("can't install without an interpreter", model.actions.contains(MarimoErrorAction.INSTALL))
    }

    @Test fun noInterpreterAndMissingOfferSandbox() {
        val noInterpreter = of(MarimoFailure.ServerNotStarted(NoInterpreterException("none")), MarimoPresence.Unknown)
        val missing = of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue(noInterpreter.actions.contains(MarimoErrorAction.START_IN_SANDBOX))
        assertTrue(missing.actions.contains(MarimoErrorAction.START_IN_SANDBOX))
    }

    @Test fun sandboxStaysOfferedButDisabledWithoutUv() {
        val model = of(
            MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
            MarimoPresence.Unknown,
            uvAvailable = false,
        )
        assertTrue("offered so the user sees why it's unavailable", model.actions.contains(MarimoErrorAction.START_IN_SANDBOX))
        assertFalse("disabled when uv is missing", model.sandboxEnabled)
    }

    @Test fun uvUnavailableExplainsSandboxNeedsUv() {
        val model = of(MarimoFailure.ServerNotStarted(UvUnavailableException("no uv")), MarimoPresence.Unknown)
        assertTrue("message names uv", model.message.contains("uv"))
    }

    @Test fun loadFailureKeepsDetailAndOffersRetry() {
        val model = of(MarimoFailure.EditorLoadFailed("ERR_CONNECTION_REFUSED"), MarimoPresence.Unknown)
        assertEquals("ERR_CONNECTION_REFUSED", model.detail)
        assertTrue(model.actions.contains(MarimoErrorAction.RETRY))
    }

    @Test fun blankDetailBecomesNull() {
        val model = of(MarimoFailure.ServerNotStarted(RuntimeException("   ")), MarimoPresence.Unknown)
        assertEquals(null, model.detail)
    }

    @Test fun everyModeOffersOpenAsPython() {
        val failures = listOf(
            MarimoFailure.ServerNotStarted(null),
            MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
            MarimoFailure.EditorLoadFailed(null),
        )
        failures.forEach {
            val model = of(it, MarimoPresence.Missing)
            assertTrue("escape hatch always present", model.actions.contains(MarimoErrorAction.OPEN_AS_PYTHON))
        }
    }
}
