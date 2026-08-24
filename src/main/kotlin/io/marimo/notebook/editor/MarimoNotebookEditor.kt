/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.editor.view.NotebookViewRegistry
import io.marimo.notebook.session.LeaseOwner
import io.marimo.notebook.session.NotebookSessionLease
import io.marimo.notebook.session.NotebookSessionManager
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

/**
 * Thin [FileEditor] over a per-session [MarimoNotebookView]. The editor registry owns the browser
 * and panel, while [NotebookSessionManager] owns the server and leases. Dragging a notebook to
 * another split creates a fresh editor for the same retained view and session.
 */
class MarimoNotebookEditor(project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val sessionManager = project.service<NotebookSessionManager>()
    private val viewRegistry = project.service<NotebookViewRegistry>()
    private val lease: NotebookSessionLease = sessionManager.acquire(file, LeaseOwner.EDITOR_TAB)
    private val view: MarimoNotebookView = viewRegistry.primaryViewFor(lease)
    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * Re-launch this notebook, picking up any launch-mode change (e.g. a newly requested sandbox).
     */
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
     * Releases this tab's lease. The registry keeps the view while its session remains alive, so a
     * tab move or quick reopen returns to the same live notebook.
     */
    override fun dispose() {
        lease.close()
    }
}
