/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.session.environment.MarimoPresence
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.StopCause
import io.marimo.notebook.launch.UvUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoErrorModelTest {

    private fun of(failure: MarimoFailure, presence: MarimoPresence, uvAvailable: Boolean = true) =
        MarimoErrorModel.of(failure, presence, uvAvailable)

    @Test
    fun missingMarimoOffersInstall() {
        val model = of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue(
            "install offered when marimo is missing",
            model.actions.contains(MarimoErrorAction.INSTALL),
        )
    }

    @Test
    fun installedMarimoDoesNotOfferInstall() {
        val model = of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Installed("0.1.0"))
        assertFalse(
            "no install when marimo is present",
            model.actions.contains(MarimoErrorAction.INSTALL),
        )
    }

    @Test
    fun noInterpreterTakesPrecedenceOverInstall() {
        val model =
            of(
                MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
                MarimoPresence.Unknown,
            )
        assertFalse(
            "can't install without an interpreter",
            model.actions.contains(MarimoErrorAction.INSTALL),
        )
    }

    @Test
    fun noInterpreterAndMissingOfferSandbox() {
        val noInterpreter =
            of(
                MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
                MarimoPresence.Unknown,
            )
        val missing = of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue(noInterpreter.actions.contains(MarimoErrorAction.START_IN_SANDBOX))
        assertTrue(missing.actions.contains(MarimoErrorAction.START_IN_SANDBOX))
    }

    @Test
    fun sandboxStaysOfferedButDisabledWithoutUv() {
        val model =
            of(
                MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
                MarimoPresence.Unknown,
                uvAvailable = false,
            )
        assertTrue(
            "offered so the user sees why it's unavailable",
            model.actions.contains(MarimoErrorAction.START_IN_SANDBOX),
        )
        assertFalse("disabled when uv is missing", model.sandboxEnabled)
    }

    @Test
    fun uvUnavailableExplainsSandboxNeedsUv() {
        val model =
            of(
                MarimoFailure.ServerNotStarted(UvUnavailableException("no uv")),
                MarimoPresence.Unknown,
            )
        assertTrue("message names uv", model.message.contains("uv"))
    }

    @Test
    fun loadFailureKeepsDetailAndOffersRetry() {
        val model =
            of(MarimoFailure.EditorLoadFailed("ERR_CONNECTION_REFUSED"), MarimoPresence.Unknown)
        assertEquals("ERR_CONNECTION_REFUSED", model.detail)
        assertTrue(model.actions.contains(MarimoErrorAction.RETRY))
    }

    @Test
    fun blankDetailBecomesNull() {
        val model =
            of(MarimoFailure.ServerNotStarted(RuntimeException("   ")), MarimoPresence.Unknown)
        assertEquals(null, model.detail)
    }

    @Test
    fun serverErrorNeverSurfacesRawTrace() {
        val trace =
            "marimo exited (code 1) before serving http://127.0.0.1:2718\n" +
                "Traceback (most recent call last):\n  File \"x\", line 1\nModuleNotFoundError: No module named 'marimo'"
        listOf(MarimoPresence.Missing, MarimoPresence.Unknown, MarimoPresence.Installed("0.1.0"))
            .forEach { presence ->
                val model = of(MarimoFailure.ServerNotStarted(RuntimeException(trace)), presence)
                assertEquals("raw launch trace must not reach the panel", null, model.detail)
            }
    }

    @Test
    fun everyModeOffersOpenAsPython() {
        val failures =
            listOf(
                MarimoFailure.ServerNotStarted(null),
                MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
                MarimoFailure.EditorLoadFailed(null),
            )
        failures.forEach {
            val model = of(it, MarimoPresence.Missing)
            assertTrue(
                "escape hatch always present",
                model.actions.contains(MarimoErrorAction.OPEN_AS_PYTHON),
            )
        }
    }

    @Test
    fun deliberateStopOffersRestartAndClose() {
        val model =
            of(MarimoFailure.ServerStopped(StopCause.Deliberate), MarimoPresence.Installed("0.1.0"))
        assertTrue("restart is the primary offer", model.actions.contains(MarimoErrorAction.RETRY))
        assertTrue(
            "shutting down often means 'I'm done'",
            model.actions.contains(MarimoErrorAction.CLOSE),
        )
    }

    @Test
    fun unexpectedStopDoesNotOfferClose() {
        val model =
            of(
                MarimoFailure.ServerStopped(StopCause.Unexpected(137, "Killed")),
                MarimoPresence.Installed("0.1.0"),
            )
        assertTrue(model.actions.contains(MarimoErrorAction.RETRY))
        assertFalse(
            "walking away is not the remedy for a crash",
            model.actions.contains(MarimoErrorAction.CLOSE),
        )
    }

    @Test
    fun stopMessageDistinguishesDeliberateFromCrash() {
        val deliberate =
            of(MarimoFailure.ServerStopped(StopCause.Deliberate), MarimoPresence.Unknown)
        val crashed =
            of(MarimoFailure.ServerStopped(StopCause.Unexpected(1, "")), MarimoPresence.Unknown)
        assertFalse(
            "the two causes must not read identically",
            deliberate.message == crashed.message,
        )
    }

    @Test
    fun stopNeverSurfacesRawProcessOutput() {
        val tail = "Traceback (most recent call last):\n  File \"x\", line 1\nMemoryError"
        val model =
            of(MarimoFailure.ServerStopped(StopCause.Unexpected(1, tail)), MarimoPresence.Unknown)
        val shown = "${model.message} ${model.detail.orEmpty()}"
        assertFalse(
            "process output belongs in the IDE log, not the panel",
            shown.contains("MemoryError"),
        )
        assertFalse(shown.contains("Traceback"))
    }

    @Test
    fun bothStopCausesOfferOpenAsPython() {
        listOf(StopCause.Deliberate, StopCause.Unexpected(1, "")).forEach { cause ->
            val model = of(MarimoFailure.ServerStopped(cause), MarimoPresence.Unknown)
            assertTrue(
                "escape hatch always present",
                model.actions.contains(MarimoErrorAction.OPEN_AS_PYTHON),
            )
        }
    }
}
