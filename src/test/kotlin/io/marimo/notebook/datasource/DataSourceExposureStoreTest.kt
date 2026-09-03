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

    private fun entry(id: String, exposed: Boolean, familyDefault: Boolean) =
        DataSourceExposureStore.ExposureEntry().apply {
            dataSourceId = id
            this.exposed = exposed
            this.familyDefault = familyDefault
        }

    @Test
    fun recordExposuresMarksTheDecisionAndReportsChanges() {
        val store = DataSourceExposureStore()
        assertFalse(store.decisionRecorded(ordersNotebook))
        assertTrue(
            store.recordExposures(
                ordersNotebook,
                listOf(entry("pg-1", exposed = true, familyDefault = true)),
            )
        )
        assertTrue(store.decisionRecorded(ordersNotebook))
        assertEquals(setOf("pg-1"), store.exposedIds(ordersNotebook))
        assertEquals(setOf("pg-1"), store.defaultIds(ordersNotebook))
        assertFalse(
            "an identical decision must not report a change",
            store.recordExposures(
                ordersNotebook,
                listOf(entry("pg-1", exposed = true, familyDefault = true)),
            ),
        )
    }

    @Test
    fun exposureIsIsolatedByNotebookPath() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(entry("pg-1", exposed = true, familyDefault = true)),
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
            listOf(entry("pg-1", exposed = true, familyDefault = true)),
        )
        store.recordExposures(
            reportNotebook,
            listOf(entry("mysql-1", exposed = true, familyDefault = true)),
        )

        assertEquals(setOf(ordersNotebook), store.notebookPathsExposing("pg-1"))
        assertTrue(store.notebookPathsExposing("other").isEmpty())
        assertEquals(
            setOf(ordersNotebook, reportNotebook),
            store.notebookPathsWithExposures(),
        )
    }

    @Test
    fun sourceChangesResolveOnlyNotebooksThatUseTheSourceAsDefault() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(
                entry("pg-default", exposed = true, familyDefault = true),
                entry("pg-replica", exposed = true, familyDefault = false),
            ),
        )

        assertEquals(
            setOf(ordersNotebook),
            store.notebookPathsUsingDefault("pg-default"),
        )
        assertTrue(store.notebookPathsUsingDefault("pg-replica").isEmpty())
        assertEquals(setOf(ordersNotebook), store.notebookPathsWithDefaults())
    }

    @Test
    fun neverForThisProjectClearsExposures() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(entry("pg-1", exposed = true, familyDefault = true)),
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
                entry("pg-1", exposed = true, familyDefault = true),
                entry("pg-2", exposed = false, familyDefault = false),
            ),
        )
        val element = XmlSerializer.serialize(store.state)
        val copy = XmlSerializer.deserialize(element, DataSourceExposureStore.State::class.java)
        val reloaded = DataSourceExposureStore().apply { loadState(copy) }
        assertEquals(setOf("pg-1"), reloaded.exposedIds(ordersNotebook))
        assertTrue(reloaded.decisionRecorded(ordersNotebook))
    }

    @Test
    fun persistedStateIsAnIndependentSnapshot() {
        val store = DataSourceExposureStore()
        store.recordExposures(
            ordersNotebook,
            listOf(entry("pg-1", exposed = true, familyDefault = true)),
        )

        val snapshot = store.state
        snapshot.notebooks.clear()

        assertEquals(setOf("pg-1"), store.exposedIds(ordersNotebook))
    }

    @Test
    fun loadedStateIsCopiedBeforePublication() {
        val state =
            DataSourceExposureStore.State().apply {
                notebooks =
                    mutableListOf(
                        DataSourceExposureStore.NotebookExposure().apply {
                            notebookPath = ordersNotebook
                            decisionRecorded = true
                            entries =
                                mutableListOf(entry("pg-1", exposed = true, familyDefault = true))
                        }
                    )
            }
        val store = DataSourceExposureStore()

        store.loadState(state)
        state.notebooks.clear()

        assertEquals(setOf("pg-1"), store.exposedIds(ordersNotebook))
    }
}
