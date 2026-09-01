/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import io.marimo.notebook.session.MarimoSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceRestartActionTest {
    @Test
    fun clickRestartsTheNotebookAndHidesTheAction() {
        var restarts = 0
        val action = DataSourceRestartAction { restarts++ }
        action.isVisible = true

        action.doClick()

        assertEquals(1, restarts)
        assertFalse(action.isVisible)
    }

    @Test
    fun actionIsOfferedOnlyForALiveNotebookSession() {
        assertTrue(shouldOfferDataSourceRestart(MarimoSessionState.STARTING))
        assertTrue(shouldOfferDataSourceRestart(MarimoSessionState.RUNNING))
        assertFalse(shouldOfferDataSourceRestart(MarimoSessionState.STOPPED))
        assertFalse(shouldOfferDataSourceRestart(MarimoSessionState.FAILED))
        assertFalse(shouldOfferDataSourceRestart(null))
    }
}
