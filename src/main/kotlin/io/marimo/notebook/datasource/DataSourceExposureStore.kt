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

    class NotebookExposure {
        var notebookPath: String = ""
        var decisionRecorded: Boolean = false
        var entries: MutableList<ExposureEntry> = mutableListOf()
    }

    class State {
        var neverForThisProject: Boolean = false
        var notebooks: MutableList<NotebookExposure> = mutableListOf()
    }

    private var current = State()

    override fun getState(): State = current

    override fun loadState(state: State) {
        current = state
    }

    fun decisionRecorded(notebookPath: String): Boolean =
        notebook(notebookPath)?.decisionRecorded == true

    fun neverForThisProject(): Boolean = current.neverForThisProject

    fun exposedIds(notebookPath: String): Set<String> =
        notebook(notebookPath)
            ?.entries
            .orEmpty()
            .filter { it.exposed }
            .mapTo(mutableSetOf()) {
                it.dataSourceId
            }

    fun primaryIds(notebookPath: String): Set<String> =
        notebook(notebookPath)
            ?.entries
            .orEmpty()
            .filter { it.exposed && it.familyPrimary }
            .mapTo(mutableSetOf()) { it.dataSourceId }

    fun recordNever() {
        current.neverForThisProject = true
        current.notebooks.clear()
    }

    /** Replaces the decision. Returns true when the effective exposure set changes. */
    fun recordExposures(notebookPath: String, entries: List<ExposureEntry>): Boolean {
        val notebook = notebook(notebookPath) ?: addNotebook(notebookPath)
        val before = fingerprint(notebook.entries)
        notebook.decisionRecorded = true
        current.neverForThisProject = false
        notebook.entries = entries.toMutableList()
        return before != fingerprint(notebook.entries)
    }

    private fun notebook(notebookPath: String): NotebookExposure? =
        current.notebooks.firstOrNull { it.notebookPath == notebookPath }

    private fun addNotebook(notebookPath: String): NotebookExposure =
        NotebookExposure().also {
            it.notebookPath = notebookPath
            current.notebooks.add(it)
        }

    private fun fingerprint(entries: List<ExposureEntry>): Set<Triple<String, Boolean, Boolean>> =
        entries.mapTo(mutableSetOf()) { Triple(it.dataSourceId, it.exposed, it.familyPrimary) }

    companion object {
        fun getInstance(project: Project): DataSourceExposureStore =
            project.getService(DataSourceExposureStore::class.java)
    }
}
