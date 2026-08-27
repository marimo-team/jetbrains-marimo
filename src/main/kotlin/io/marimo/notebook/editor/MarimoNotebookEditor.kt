/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.marimo.notebook.editor.source.flushMarimoSourceToDisk
import io.marimo.notebook.editor.view.NotebookEditorView
import io.marimo.notebook.editor.view.NotebookViewRegistry
import io.marimo.notebook.editor.view.SecondaryNotebookView
import io.marimo.notebook.session.LeaseOwner
import io.marimo.notebook.session.NotebookSessionLease
import io.marimo.notebook.session.NotebookSessionManager
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

/**
 * A split or a reopen must not tear down the marimo session. This [FileEditor] holds an
 * `EDITOR_TAB` lease and borrows the registry view. The editor package is the JCEF UI: tabs, the
 * Sessions tool window, and session actions.
 */
class MarimoNotebookEditor(project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val sessionManager = project.service<NotebookSessionManager>()
    private val viewRegistry = project.service<NotebookViewRegistry>()
    private val lease: NotebookSessionLease = sessionManager.acquire(file, LeaseOwner.EDITOR_TAB)
    private val view: NotebookEditorView = viewRegistry.viewFor(lease)
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private val vfsConnection = project.messageBus.connect()
    private var disposed = false
    private var valid = true

    init {
        vfsConnection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (
                        events.any { it is VFileDeleteEvent && (it.file == file || !file.isValid) }
                    ) {
                        notifyValidityChanged()
                    }
                }
            },
        )
    }

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

    override fun isValid(): Boolean = !disposed && file.isValid

    private fun notifyValidityChanged() {
        if (valid && !isValid) {
            valid = false
            propertyChangeSupport.firePropertyChange(FileEditor.getPropValid(), true, false)
        }
    }

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
        if (disposed) return
        disposed = true
        vfsConnection.disconnect()
        notifyValidityChanged()
        viewRegistry.releaseView(lease, view)
        if (view is SecondaryNotebookView) Disposer.dispose(view)
        lease.close()
    }
}
