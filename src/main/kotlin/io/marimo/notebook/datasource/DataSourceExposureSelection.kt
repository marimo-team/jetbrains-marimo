/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

/** Mutable, credential-free selection state shared by the Data Sources tab controls. */
internal class DataSourceExposureSelection(
    candidates: List<CandidateDataSource>,
    exposedIds: Set<String>,
    primaryIds: Set<String>,
) {
    internal data class Item(
        val candidate: CandidateDataSource,
        var exposed: Boolean,
        var primary: Boolean,
    )

    val items: List<Item> = candidates.map { candidate ->
        Item(
            candidate = candidate,
            exposed = candidate.supported && candidate.id in exposedIds,
            primary =
                candidate.supported && candidate.id in exposedIds && candidate.id in primaryIds,
        )
    }

    init {
        normalizeAllFamilies()
    }

    fun setExposed(id: String, exposed: Boolean) {
        val item = items.firstOrNull { it.candidate.id == id } ?: return
        if (!item.candidate.supported) return
        item.exposed = exposed
        if (!exposed) item.primary = false
        normalizeFamily(item.candidate.family)
    }

    fun setPrimary(id: String) {
        val item = items.firstOrNull { it.candidate.id == id } ?: return
        val family = item.candidate.family ?: return
        if (!item.candidate.supported || !item.exposed) return
        items.filter { it.candidate.family == family }.forEach { it.primary = it === item }
    }

    fun shareAll() {
        items.filter { it.candidate.supported }.forEach { it.exposed = true }
        normalizeAllFamilies()
    }

    fun entries(): List<DataSourceExposureStore.ExposureEntry> = items.map { item ->
        DataSourceExposureStore.ExposureEntry().apply {
            dataSourceId = item.candidate.id
            exposed = item.exposed
            familyPrimary = item.exposed && item.primary
        }
    }

    private fun normalizeAllFamilies() {
        items.mapNotNull { it.candidate.family }.toSet().forEach(::normalizeFamily)
    }

    private fun normalizeFamily(family: DbFamily?) {
        if (family == null) return
        val members = items.filter { it.candidate.family == family }
        members.filter { !it.exposed }.forEach { it.primary = false }
        val exposed = members.filter { it.exposed }
        val selected = exposed.filter { it.primary }
        when {
            exposed.isEmpty() -> Unit
            selected.isEmpty() -> exposed.first().primary = true
            selected.size > 1 -> selected.drop(1).forEach { it.primary = false }
        }
    }
}
