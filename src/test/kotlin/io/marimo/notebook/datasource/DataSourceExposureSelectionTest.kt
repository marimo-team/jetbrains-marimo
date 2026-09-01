/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceExposureSelectionTest {
    private fun postgres(id: String, name: String = id): CandidateDataSource =
        Candidates.from(
            IdeDataSourceFacts(
                id = id,
                displayName = name,
                url = "jdbc:postgresql://127.0.0.1:5432/orders",
                username = "orders_app",
                authProviderId = "user-pass",
            )
        )

    @Test
    fun recreatedSourceIsVisibleButDoesNotInheritDeletedSourceConsent() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("new-id", "Orders DB")),
                exposedIds = setOf("deleted-id"),
                primaryIds = setOf("deleted-id"),
            )

        assertEquals(listOf("new-id"), selection.items.map { it.candidate.id })
        assertFalse(selection.items.single().exposed)
        assertFalse(selection.items.single().primary)
        assertEquals(listOf("new-id"), selection.entries().map { it.dataSourceId })
    }

    @Test
    fun exposingTheFirstSourceMakesItPrimaryForItsFamily() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders")),
                exposedIds = emptySet(),
                primaryIds = emptySet(),
            )

        selection.setExposed("orders", true)

        assertTrue(selection.items.single().exposed)
        assertTrue(selection.items.single().primary)
    }

    @Test
    fun selectingAnotherPrimaryClearsThePreviousPrimary() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders"), postgres("reporting")),
                exposedIds = setOf("orders", "reporting"),
                primaryIds = setOf("orders"),
            )

        selection.setPrimary("reporting")

        assertFalse(selection.items.single { it.candidate.id == "orders" }.primary)
        assertTrue(selection.items.single { it.candidate.id == "reporting" }.primary)
    }

    @Test
    fun disablingThePrimaryPromotesAnotherExposedSource() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders"), postgres("reporting")),
                exposedIds = setOf("orders", "reporting"),
                primaryIds = setOf("orders"),
            )

        selection.setExposed("orders", false)

        assertTrue(selection.items.single { it.candidate.id == "reporting" }.primary)
    }

    @Test
    fun shareAllSharesEverySupportedSource() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders"), postgres("reporting")),
                exposedIds = emptySet(),
                primaryIds = emptySet(),
            )

        selection.shareAll()

        assertTrue(selection.items.all { it.exposed })
        assertEquals(1, selection.items.count { it.primary })
    }
}
