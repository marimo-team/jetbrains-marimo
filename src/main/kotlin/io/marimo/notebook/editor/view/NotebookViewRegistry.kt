/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.marimo.notebook.session.NotebookSessionEvent
import io.marimo.notebook.session.NotebookSessionLease
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.SessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Owns the primary JCEF view for each live notebook session in this project. */
@Service(Service.Level.PROJECT)
class NotebookViewRegistry(private val project: Project) : Disposable {

    private val sessionManager = project.getService(NotebookSessionManager::class.java)
    private val views = ConcurrentHashMap<SessionId, NotebookView>()
    private val primaryMounts = ConcurrentHashMap<SessionId, AtomicInteger>()

    init {
        sessionManager.addSessionEventListener(this, ::onSessionEvent)
    }

    /**
     * Returns the view an editor should render. The first open editor for a session gets the shared
     * primary [NotebookView]; any simultaneous editor gets its own [SecondaryNotebookView].
     * Read-only behavior for the second client is enforced by marimo, not here.
     */
    internal fun viewFor(lease: NotebookSessionLease): NotebookEditorView {
        val primary = primaryViewFor(lease)
        val mounts = primaryMounts.computeIfAbsent(lease.sessionId) { AtomicInteger(0) }
        while (true) {
            when (val current = mounts.get()) {
                0 -> if (mounts.compareAndSet(0, 1)) return primary
                else -> return SecondaryNotebookView(project, lease)
            }
        }
    }

    /**
     * Releases a primary mount when its editor closes. Secondary views are disposed by the editor.
     */
    internal fun releaseView(lease: NotebookSessionLease, view: NotebookEditorView) {
        if (view is NotebookView) {
            primaryMounts[lease.sessionId]?.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
    }

    /** Returns the primary view for [lease]'s session. The registry creates it if it is absent. */
    fun primaryViewFor(lease: NotebookSessionLease): NotebookView =
        views.computeIfAbsent(lease.sessionId) {
            NotebookView(project, lease.notebook).also { view ->
                Disposer.register(this, view)
            }
        }

    private fun onSessionEvent(event: NotebookSessionEvent) {
        when (event) {
            is NotebookSessionEvent.Ended -> {
                primaryMounts.remove(event.sessionId)
                views.remove(event.sessionId)?.let { view -> onEdt { Disposer.dispose(view) } }
            }
            is NotebookSessionEvent.Restarted ->
                views[event.sessionId]?.let { view ->
                    onEdt {
                        if (views[event.sessionId] === view) view.reconnectAfterSessionRestart()
                    }
                }
        }
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread || application.isUnitTestMode) {
            action()
        } else {
            application.invokeLater {
                if (!project.isDisposed) action()
            }
        }
    }

    override fun dispose() {
        views.clear()
        primaryMounts.clear()
    }
}
