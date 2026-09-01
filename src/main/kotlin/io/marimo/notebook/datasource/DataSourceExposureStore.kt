/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Stores exposure decisions for one project. This service holds identifiers and booleans only. It
 * never holds credentials or connection details. Workspace storage keeps the state out of shared
 * project files.
 */
@Service(Service.Level.PROJECT)
@State(name = "MarimoDataSourceExposure", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DataSourceExposureStore : PersistentStateComponent<DataSourceExposureStore.State> {

    class ExposureEntry {
        var dataSourceId: String = ""
        var exposed: Boolean = false
        var familyPrimary: Boolean = false
    }

    class State {
        /** True after the user answers the prompt or applies the dialog. */
        var decisionRecorded: Boolean = false
        var neverForThisProject: Boolean = false
        var entries: MutableList<ExposureEntry> = mutableListOf()
    }

    private var current = State()

    override fun getState(): State = current

    override fun loadState(state: State) {
        current = state
    }

    fun decisionRecorded(): Boolean = current.decisionRecorded

    fun neverForThisProject(): Boolean = current.neverForThisProject

    fun exposedIds(): Set<String> =
        current.entries.filter { it.exposed }.mapTo(mutableSetOf()) { it.dataSourceId }

    fun primaryIds(): Set<String> =
        current.entries
            .filter { it.exposed && it.familyPrimary }
            .mapTo(mutableSetOf()) { it.dataSourceId }

    fun recordNever() {
        current.decisionRecorded = true
        current.neverForThisProject = true
        current.entries.clear()
    }

    /** Replaces the decision. Returns true when the effective exposure set changes. */
    fun recordExposures(entries: List<ExposureEntry>): Boolean {
        val before = fingerprint(current.entries)
        current.decisionRecorded = true
        current.neverForThisProject = false
        current.entries = entries.toMutableList()
        return before != fingerprint(current.entries)
    }

    private fun fingerprint(entries: List<ExposureEntry>): Set<Triple<String, Boolean, Boolean>> =
        entries.mapTo(mutableSetOf()) { Triple(it.dataSourceId, it.exposed, it.familyPrimary) }

    companion object {
        fun getInstance(project: Project): DataSourceExposureStore =
            project.getService(DataSourceExposureStore::class.java)
    }
}
