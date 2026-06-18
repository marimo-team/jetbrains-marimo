/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoPresence
import io.marimo.notebook.launch.NoInterpreterException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoErrorModelTest {

    @Test fun missingMarimoOffersInstall() {
        val model = MarimoErrorModel.of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Missing)
        assertTrue("install offered when marimo is missing", model.actions.contains(MarimoErrorAction.INSTALL))
    }

    @Test fun installedMarimoDoesNotOfferInstall() {
        val model =
            MarimoErrorModel.of(MarimoFailure.ServerNotStarted(null), MarimoPresence.Installed("0.1.0"))
        assertFalse("no install when marimo is present", model.actions.contains(MarimoErrorAction.INSTALL))
    }

    @Test fun noInterpreterTakesPrecedenceOverInstall() {
        val model = MarimoErrorModel.of(
            MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
            MarimoPresence.Unknown,
        )
        assertFalse("can't install without an interpreter", model.actions.contains(MarimoErrorAction.INSTALL))
    }

    @Test fun loadFailureKeepsDetailAndOffersRetry() {
        val model = MarimoErrorModel.of(MarimoFailure.EditorLoadFailed("ERR_CONNECTION_REFUSED"), MarimoPresence.Unknown)
        assertEquals("ERR_CONNECTION_REFUSED", model.detail)
        assertTrue(model.actions.contains(MarimoErrorAction.RETRY))
    }

    @Test fun blankDetailBecomesNull() {
        val model = MarimoErrorModel.of(MarimoFailure.ServerNotStarted(RuntimeException("   ")), MarimoPresence.Unknown)
        assertEquals(null, model.detail)
    }

    @Test fun everyModeOffersOpenAsPython() {
        val failures = listOf(
            MarimoFailure.ServerNotStarted(null),
            MarimoFailure.ServerNotStarted(NoInterpreterException("none")),
            MarimoFailure.EditorLoadFailed(null),
        )
        failures.forEach {
            val model = MarimoErrorModel.of(it, MarimoPresence.Missing)
            assertTrue("escape hatch always present", model.actions.contains(MarimoErrorAction.OPEN_AS_PYTHON))
        }
    }
}
