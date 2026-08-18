/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.server.MarimoServerService
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

/**
 * Thin [FileEditor] over a per-file [MarimoNotebookView]. The view — browser, panel, and marimo
 * server — is owned by [MarimoServerService], keyed by file, and outlives this instance. Dragging a
 * notebook to another split disposes this editor and creates a fresh one for the same file; both
 * borrow the same view, so the marimo session is never torn down or reconnected.
 */
class MarimoNotebookEditor(project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val server = project.service<MarimoServerService>()
    private val view = server.viewFor(file)
    private val propertyChangeSupport = PropertyChangeSupport(this)

    init {
        server.attach(file)
    }

    /** Re-launch this notebook, picking up any launch-mode change (e.g. a newly requested sandbox). */
    fun reload() = view.reload()

    /**
     * Hand any pending Source-tab edits to the marimo server, which only sees them once they reach
     * disk. Unlike the Source tab's disk refresh, this stays on the EDT: writing the document
     * requires it, and the document is already in memory, so there is no disk scan to move off.
     */
    override fun selectNotify() {
        flushMarimoSourceToDisk(file)
    }

    override fun getComponent(): JComponent = view.panel
    override fun getPreferredFocusedComponent(): JComponent? = view.preferredFocusedComponent
    override fun getName(): String = "marimo"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) =
        propertyChangeSupport.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) =
        propertyChangeSupport.removePropertyChangeListener(listener)

    /**
     * Detaches this tab from the session. The view, browser, and server survive in the project's
     * session manager: a tab move or a quick reopen reattaches to the same live notebook, and only
     * the manager's background TTL (or an explicit Stop) tears it down.
     */
    override fun dispose() {
        server.detach(file)
    }
}
