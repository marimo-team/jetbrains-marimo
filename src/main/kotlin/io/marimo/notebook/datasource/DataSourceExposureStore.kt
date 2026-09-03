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
        var familyDefault: Boolean = false
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

    private val lock = Any()
    private var current = State()

    override fun getState(): State = synchronized(lock) { current.copy() }

    override fun loadState(state: State) {
        synchronized(lock) { current = state.copy() }
    }

    fun decisionRecorded(notebookPath: String): Boolean =
        synchronized(lock) { notebook(notebookPath)?.decisionRecorded == true }

    fun neverForThisProject(): Boolean = synchronized(lock) { current.neverForThisProject }

    fun exposedIds(notebookPath: String): Set<String> =
        synchronized(lock) {
            notebook(notebookPath)
                ?.entries
                .orEmpty()
                .filter { it.exposed }
                .mapTo(mutableSetOf()) {
                    it.dataSourceId
                }
        }

    fun defaultIds(notebookPath: String): Set<String> =
        synchronized(lock) {
            notebook(notebookPath)
                ?.entries
                .orEmpty()
                .filter { it.exposed && it.familyDefault }
                .mapTo(mutableSetOf()) { it.dataSourceId }
        }

    fun notebookPathsExposing(dataSourceId: String): Set<String> =
        synchronized(lock) {
            current.notebooks
                .filter { notebook ->
                    notebook.entries.any { it.exposed && it.dataSourceId == dataSourceId }
                }
                .mapTo(mutableSetOf()) { it.notebookPath }
        }

    fun notebookPathsUsingDefault(dataSourceId: String): Set<String> =
        synchronized(lock) {
            current.notebooks
                .filter { notebook ->
                    notebook.entries.any {
                        it.exposed && it.familyDefault && it.dataSourceId == dataSourceId
                    }
                }
                .mapTo(mutableSetOf()) { it.notebookPath }
        }

    fun notebookPathsWithExposures(): Set<String> =
        synchronized(lock) {
            current.notebooks
                .filter { notebook -> notebook.entries.any { it.exposed } }
                .mapTo(mutableSetOf()) { it.notebookPath }
        }

    fun notebookPathsWithDefaults(): Set<String> =
        synchronized(lock) {
            current.notebooks
                .filter { notebook -> notebook.entries.any { it.exposed && it.familyDefault } }
                .mapTo(mutableSetOf()) { it.notebookPath }
        }

    fun recordNever() {
        synchronized(lock) {
            current.neverForThisProject = true
            current.notebooks.clear()
        }
    }

    /** Replaces the decision. Returns true when the effective exposure set changes. */
    fun recordExposures(notebookPath: String, entries: List<ExposureEntry>): Boolean =
        synchronized(lock) {
            val notebook = notebook(notebookPath) ?: addNotebook(notebookPath)
            val before = fingerprint(notebook.entries)
            notebook.decisionRecorded = true
            current.neverForThisProject = false
            notebook.entries = entries.mapTo(mutableListOf()) { it.copy() }
            before != fingerprint(notebook.entries)
        }

    private fun notebook(notebookPath: String): NotebookExposure? =
        current.notebooks.firstOrNull { it.notebookPath == notebookPath }

    private fun addNotebook(notebookPath: String): NotebookExposure =
        NotebookExposure().also {
            it.notebookPath = notebookPath
            current.notebooks.add(it)
        }

    private fun fingerprint(entries: List<ExposureEntry>): Set<Triple<String, Boolean, Boolean>> =
        entries.mapTo(mutableSetOf()) { Triple(it.dataSourceId, it.exposed, it.familyDefault) }

    private fun State.copy(): State =
        State().also { copy ->
            copy.neverForThisProject = neverForThisProject
            copy.notebooks = notebooks.mapTo(mutableListOf()) { it.copy() }
        }

    private fun NotebookExposure.copy(): NotebookExposure =
        NotebookExposure().also { copy ->
            copy.notebookPath = notebookPath
            copy.decisionRecorded = decisionRecorded
            copy.entries = entries.mapTo(mutableListOf()) { it.copy() }
        }

    private fun ExposureEntry.copy(): ExposureEntry =
        ExposureEntry().also { copy ->
            copy.dataSourceId = dataSourceId
            copy.exposed = exposed
            copy.familyDefault = familyDefault
        }

    companion object {
        fun getInstance(project: Project): DataSourceExposureStore =
            project.getService(DataSourceExposureStore::class.java)
    }
}
