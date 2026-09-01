/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceExposureStoreTest {
    private fun entry(id: String, exposed: Boolean, primary: Boolean) =
        DataSourceExposureStore.ExposureEntry().apply {
            dataSourceId = id
            this.exposed = exposed
            familyPrimary = primary
        }

    @Test
    fun recordExposuresMarksTheDecisionAndReportsChanges() {
        val store = DataSourceExposureStore()
        assertFalse(store.decisionRecorded())
        assertTrue(store.recordExposures(listOf(entry("pg-1", exposed = true, primary = true))))
        assertTrue(store.decisionRecorded())
        assertEquals(setOf("pg-1"), store.exposedIds())
        assertEquals(setOf("pg-1"), store.primaryIds())
        assertFalse(
            "an identical decision must not report a change",
            store.recordExposures(listOf(entry("pg-1", exposed = true, primary = true))),
        )
    }

    @Test
    fun neverForThisProjectClearsExposures() {
        val store = DataSourceExposureStore()
        store.recordExposures(listOf(entry("pg-1", exposed = true, primary = true)))
        store.recordNever()
        assertTrue(store.decisionRecorded())
        assertTrue(store.neverForThisProject())
        assertTrue(store.exposedIds().isEmpty())
    }

    @Test
    fun stateRoundTripsThroughXmlSerialization() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            listOf(
                entry("pg-1", exposed = true, primary = true),
                entry("pg-2", exposed = false, primary = false),
            )
        )
        val element = XmlSerializer.serialize(store.state)
        val copy = XmlSerializer.deserialize(element, DataSourceExposureStore.State::class.java)
        val reloaded = DataSourceExposureStore().apply { loadState(copy) }
        assertEquals(setOf("pg-1"), reloaded.exposedIds())
        assertTrue(reloaded.decisionRecorded())
    }
}
