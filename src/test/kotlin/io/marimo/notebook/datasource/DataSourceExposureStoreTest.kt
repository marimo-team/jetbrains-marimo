/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceExposureStoreTest {
    private val ordersNotebook = "notebooks/orders.py"
    private val reportNotebook = "notebooks/report.py"

    private fun entry(id: String, exposed: Boolean, primary: Boolean) =
        DataSourceExposureStore.ExposureEntry().apply {
            dataSourceId = id
            this.exposed = exposed
            familyPrimary = primary
        }

    @Test
    fun recordExposuresMarksTheDecisionAndReportsChanges() {
        val store = DataSourceExposureStore()
        assertFalse(store.decisionRecorded(ordersNotebook))
        assertTrue(
            store.recordExposures(
                ordersNotebook,
                listOf(entry("pg-1", exposed = true, primary = true)),
            )
        )
        assertTrue(store.decisionRecorded(ordersNotebook))
        assertEquals(setOf("pg-1"), store.exposedIds(ordersNotebook))
        assertEquals(setOf("pg-1"), store.primaryIds(ordersNotebook))
        assertFalse(
            "an identical decision must not report a change",
            store.recordExposures(
                ordersNotebook,
                listOf(entry("pg-1", exposed = true, primary = true)),
            ),
        )
    }

    @Test
    fun exposureIsIsolatedByNotebookPath() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(entry("pg-1", exposed = true, primary = true)),
        )

        assertEquals(setOf("pg-1"), store.exposedIds(ordersNotebook))
        assertTrue(store.exposedIds(reportNotebook).isEmpty())
        assertFalse(store.decisionRecorded(reportNotebook))
    }

    @Test
    fun sourceChangesResolveOnlyNotebooksThatExposeTheSource() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(entry("pg-1", exposed = true, primary = true)),
        )
        store.recordExposures(
            reportNotebook,
            listOf(entry("mysql-1", exposed = true, primary = true)),
        )

        assertEquals(setOf(ordersNotebook), store.notebookPathsExposing("pg-1"))
        assertTrue(store.notebookPathsExposing("other").isEmpty())
        assertEquals(
            setOf(ordersNotebook, reportNotebook),
            store.notebookPathsWithExposures(),
        )
    }

    @Test
    fun neverForThisProjectClearsExposures() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(entry("pg-1", exposed = true, primary = true)),
        )
        store.recordNever()
        assertFalse(store.decisionRecorded(ordersNotebook))
        assertTrue(store.neverForThisProject())
        assertTrue(store.exposedIds(ordersNotebook).isEmpty())
    }

    @Test
    fun stateRoundTripsThroughXmlSerialization() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(
                entry("pg-1", exposed = true, primary = true),
                entry("pg-2", exposed = false, primary = false),
            ),
        )
        val element = XmlSerializer.serialize(store.state)
        val copy = XmlSerializer.deserialize(element, DataSourceExposureStore.State::class.java)
        val reloaded = DataSourceExposureStore().apply { loadState(copy) }
        assertEquals(setOf("pg-1"), reloaded.exposedIds(ordersNotebook))
        assertTrue(reloaded.decisionRecorded(ordersNotebook))
    }
}
