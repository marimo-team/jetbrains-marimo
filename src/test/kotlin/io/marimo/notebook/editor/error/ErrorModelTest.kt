/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.error

import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.StopCause
import io.marimo.notebook.launch.UvUnavailableException
import io.marimo.notebook.session.environment.MarimoPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorModelTest {

    private fun of(failure: Failure, presence: MarimoPresence, uvAvailable: Boolean = true) =
        ErrorModel.of(failure, presence, uvAvailable)

    @Test
    fun missingMarimoOffersInstall() {
        val model = of(Failure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue(
            "install offered when marimo is missing",
            model.actions.contains(ErrorAction.INSTALL),
        )
    }

    @Test
    fun installedMarimoDoesNotOfferInstall() {
        val model = of(Failure.ServerNotStarted(null), MarimoPresence.Installed("0.1.0"))
        assertFalse(
            "no install when marimo is present",
            model.actions.contains(ErrorAction.INSTALL),
        )
    }

    @Test
    fun noInterpreterTakesPrecedenceOverInstall() {
        val model =
            of(
                Failure.ServerNotStarted(NoInterpreterException("none")),
                MarimoPresence.Unknown,
            )
        assertFalse(
            "can't install without an interpreter",
            model.actions.contains(ErrorAction.INSTALL),
        )
    }

    @Test
    fun noInterpreterAndMissingOfferSandbox() {
        val noInterpreter =
            of(
                Failure.ServerNotStarted(NoInterpreterException("none")),
                MarimoPresence.Unknown,
            )
        val missing = of(Failure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue(noInterpreter.actions.contains(ErrorAction.START_IN_SANDBOX))
        assertTrue(missing.actions.contains(ErrorAction.START_IN_SANDBOX))
    }

    @Test
    fun sandboxStaysOfferedButDisabledWithoutUv() {
        val model =
            of(
                Failure.ServerNotStarted(NoInterpreterException("none")),
                MarimoPresence.Unknown,
                uvAvailable = false,
            )
        assertTrue(
            "offered so the user sees why it's unavailable",
            model.actions.contains(ErrorAction.START_IN_SANDBOX),
        )
        assertFalse("disabled when uv is missing", model.sandboxEnabled)
    }

    @Test
    fun sandboxLaunchFailureDoesNotOfferProjectInterpreterInstall() {
        val model =
            ErrorModel.of(
                Failure.ServerNotStarted(RuntimeException("uv launch failed")),
                MarimoPresence.Missing,
                uvAvailable = true,
                sandbox = true,
            )

        assertFalse(model.actions.contains(ErrorAction.INSTALL))
        assertFalse(model.actions.contains(ErrorAction.START_IN_SANDBOX))
    }

    @Test
    fun uvUnavailableExplainsSandboxNeedsUv() {
        val model =
            of(
                Failure.ServerNotStarted(UvUnavailableException("no uv")),
                MarimoPresence.Unknown,
            )
        assertTrue("message names uv", model.message.contains("uv"))
    }

    @Test
    fun loadFailureKeepsDetailAndOffersRetry() {
        val model = of(Failure.EditorLoadFailed("ERR_CONNECTION_REFUSED"), MarimoPresence.Unknown)
        assertEquals("ERR_CONNECTION_REFUSED", model.detail)
        assertTrue(model.actions.contains(ErrorAction.RETRY))
    }

    @Test
    fun blankDetailBecomesNull() {
        val model = of(Failure.ServerNotStarted(RuntimeException("   ")), MarimoPresence.Unknown)
        assertEquals(null, model.detail)
    }

    @Test
    fun serverErrorNeverSurfacesRawTrace() {
        val trace =
            "marimo exited (code 1) before serving http://127.0.0.1:2718\n" +
                "Traceback (most recent call last):\n  File \"x\", line 1\nModuleNotFoundError: No module named 'marimo'"
        listOf(MarimoPresence.Missing, MarimoPresence.Unknown, MarimoPresence.Installed("0.1.0"))
            .forEach { presence ->
                val model = of(Failure.ServerNotStarted(RuntimeException(trace)), presence)
                assertEquals("raw launch trace must not reach the panel", null, model.detail)
            }
    }

    @Test
    fun everyModeOffersOpenAsPython() {
        val failures =
            listOf(
                Failure.ServerNotStarted(null),
                Failure.ServerNotStarted(NoInterpreterException("none")),
                Failure.EditorLoadFailed(null),
            )
        failures.forEach {
            val model = of(it, MarimoPresence.Missing)
            assertTrue(
                "escape hatch always present",
                model.actions.contains(ErrorAction.OPEN_AS_PYTHON),
            )
        }
    }

    @Test
    fun deliberateStopOffersRestartAndClose() {
        val model =
            of(Failure.ServerStopped(StopCause.Deliberate), MarimoPresence.Installed("0.1.0"))
        assertTrue("restart is the primary offer", model.actions.contains(ErrorAction.RETRY))
        assertTrue(
            "shutting down often means 'I'm done'",
            model.actions.contains(ErrorAction.CLOSE),
        )
    }

    @Test
    fun unexpectedStopDoesNotOfferClose() {
        val model =
            of(
                Failure.ServerStopped(StopCause.Unexpected(137, "Killed")),
                MarimoPresence.Installed("0.1.0"),
            )
        assertTrue(model.actions.contains(ErrorAction.RETRY))
        assertFalse(
            "walking away is not the remedy for a crash",
            model.actions.contains(ErrorAction.CLOSE),
        )
    }

    @Test
    fun stopMessageDistinguishesDeliberateFromCrash() {
        val deliberate = of(Failure.ServerStopped(StopCause.Deliberate), MarimoPresence.Unknown)
        val crashed = of(Failure.ServerStopped(StopCause.Unexpected(1, "")), MarimoPresence.Unknown)
        assertEquals("marimo was shut down.", deliberate.message)
        assertFalse(
            "the two causes must not read identically",
            deliberate.message == crashed.message,
        )
    }

    @Test
    fun stopNeverSurfacesRawProcessOutput() {
        val tail = "Traceback (most recent call last):\n  File \"x\", line 1\nMemoryError"
        val model = of(Failure.ServerStopped(StopCause.Unexpected(1, tail)), MarimoPresence.Unknown)
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
            val model = of(Failure.ServerStopped(cause), MarimoPresence.Unknown)
            assertTrue(
                "escape hatch always present",
                model.actions.contains(ErrorAction.OPEN_AS_PYTHON),
            )
        }
    }
}
