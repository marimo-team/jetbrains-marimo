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
                defaultIds = setOf("deleted-id"),
            )

        assertEquals(listOf("new-id"), selection.items.map { it.candidate.id })
        assertFalse(selection.items.single().exposed)
        assertFalse(selection.items.single().familyDefault)
        assertTrue(selection.entries().isEmpty())
    }

    @Test
    fun exposingTheFirstSourceMakesItDefaultForItsFamily() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders")),
                exposedIds = emptySet(),
                defaultIds = emptySet(),
            )

        selection.setExposed("orders", true)

        assertTrue(selection.items.single().exposed)
        assertTrue(selection.items.single().familyDefault)
    }

    @Test
    fun selectingAnotherDefaultClearsThePreviousDefault() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders"), postgres("reporting")),
                exposedIds = setOf("orders", "reporting"),
                defaultIds = setOf("orders"),
            )

        selection.setDefault("reporting")

        assertFalse(selection.items.single { it.candidate.id == "orders" }.familyDefault)
        assertTrue(selection.items.single { it.candidate.id == "reporting" }.familyDefault)
    }

    @Test
    fun disablingTheDefaultPromotesAnotherExposedSource() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders"), postgres("reporting")),
                exposedIds = setOf("orders", "reporting"),
                defaultIds = setOf("orders"),
            )

        selection.setExposed("orders", false)

        assertTrue(selection.items.single { it.candidate.id == "reporting" }.familyDefault)
    }

    @Test
    fun shareAllSharesEverySupportedSource() {
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(postgres("orders"), postgres("reporting")),
                exposedIds = emptySet(),
                defaultIds = emptySet(),
            )

        selection.shareAll()

        assertTrue(selection.items.all { it.exposed })
        assertEquals(1, selection.items.count { it.familyDefault })
        assertEquals(
            setOf("orders", "reporting"),
            selection.entries().mapTo(mutableSetOf()) { it.dataSourceId },
        )
    }

    @Test
    fun fallbackDefaultUsesTheSameDeterministicOrderingAsLaunch() {
        val beta = postgres("beta", "Beta")
        val alpha = postgres("alpha", "Alpha")
        val selection =
            DataSourceExposureSelection(
                candidates = listOf(beta, alpha),
                exposedIds = setOf("beta", "alpha"),
                defaultIds = emptySet(),
            )

        assertFalse(selection.items.single { it.candidate.id == "beta" }.familyDefault)
        assertTrue(selection.items.single { it.candidate.id == "alpha" }.familyDefault)
        assertEquals(
            Candidates.effectiveDefaults(listOf(beta, alpha), emptySet()),
            selection.items.filter { it.familyDefault }.mapTo(mutableSetOf()) { it.candidate.id },
        )
    }
}
