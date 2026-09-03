/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceExposurePanelLoadStateTest {
    @Test
    fun aNewLoadInvalidatesAnEarlierLoadForTheSameNotebook() {
        val state = DataSourceExposurePanelLoadState()
        val first = state.begin("orders.py")
        val second = state.begin("orders.py")

        assertFalse(state.isCurrent(first))
        assertTrue(state.isCurrent(second))
    }

    @Test
    fun aNewLoadInvalidatesAnEarlierLoadForAnotherNotebook() {
        val state = DataSourceExposurePanelLoadState()
        val first = state.begin("orders.py")
        val second = state.begin("inventory.py")

        assertFalse(state.isCurrent(first))
        assertTrue(state.isCurrent(second))
    }
}
