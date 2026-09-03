/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

/** Mutable, credential-free selection state shared by the Data Sources tab controls. */
internal class DataSourceExposureSelection(
    candidates: List<CandidateDataSource>,
    exposedIds: Set<String>,
    defaultIds: Set<String>,
) {
    internal data class Item(
        val candidate: CandidateDataSource,
        var exposed: Boolean,
        var familyDefault: Boolean,
    )

    val items: List<Item> = candidates.map { candidate ->
        Item(
            candidate = candidate,
            exposed = candidate.supported && candidate.id in exposedIds,
            familyDefault =
                candidate.supported && candidate.id in exposedIds && candidate.id in defaultIds,
        )
    }

    init {
        normalizeAllFamilies()
    }

    fun setExposed(id: String, exposed: Boolean) {
        val item = items.firstOrNull { it.candidate.id == id } ?: return
        if (!item.candidate.supported) return
        item.exposed = exposed
        if (!exposed) item.familyDefault = false
        normalizeFamily(item.candidate.family)
    }

    fun setDefault(id: String) {
        val item = items.firstOrNull { it.candidate.id == id } ?: return
        val family = item.candidate.family ?: return
        if (!item.candidate.supported || !item.exposed) return
        items.filter { it.candidate.family == family }.forEach { it.familyDefault = it === item }
    }

    fun shareAll() {
        items.filter { it.candidate.supported }.forEach { it.exposed = true }
        normalizeAllFamilies()
    }

    fun entries(): List<DataSourceExposureStore.ExposureEntry> =
        items
            .filter { it.exposed }
            .map { item ->
                DataSourceExposureStore.ExposureEntry().apply {
                    dataSourceId = item.candidate.id
                    exposed = true
                    familyDefault = item.familyDefault
                }
            }

    private fun normalizeAllFamilies() {
        items.mapNotNull { it.candidate.family }.toSet().forEach(::normalizeFamily)
    }

    private fun normalizeFamily(family: DbFamily?) {
        if (family == null) return
        val members = items.filter { it.candidate.family == family }
        members.filter { !it.exposed }.forEach { it.familyDefault = false }
        val exposed = members.filter { it.exposed }
        if (exposed.isEmpty()) return
        val effective =
            Candidates.effectiveDefaults(
                exposed.map { it.candidate },
                exposed.filter { it.familyDefault }.mapTo(mutableSetOf()) { it.candidate.id },
            )
        members.forEach { item ->
            item.familyDefault = item.exposed && item.candidate.id in effective
        }
    }
}
