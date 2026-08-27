/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.sessions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.editor.EditorAvailability
import io.marimo.notebook.editor.MARIMO_NOTEBOOK_EDITOR_TYPE
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.SessionSnapshot

/** True when a session exists whose process is alive, so Restart and Stop have a target. */
internal fun canControlSession(status: SessionSnapshot?): Boolean = status?.state?.isLive == true

/** True when the Sessions card can copy a token-free loopback URL. */
internal fun canCopySessionUrl(status: SessionSnapshot?): Boolean {
    val launch = status?.launch ?: return false
    return !launch.tokenAuthEnabled
}

/** Opens (or focuses) the notebook and selects the marimo editor over the Source sub-tab. */
internal fun openMarimoNotebook(project: Project, file: VirtualFile) {
    val editors = FileEditorManager.getInstance(project)
    editors.openFile(file, true)
    editors.setSelectedEditor(file, MARIMO_NOTEBOOK_EDITOR_TYPE)
}

/**
 * Base for notebook session actions: visible only on marimo notebooks, enabled via [enabled], and
 * updated on BGT because the status probe only reads service state.
 */
abstract class MarimoSessionAction(private val enabled: (SessionSnapshot?) -> Boolean) :
    AnAction(), DumbAware {

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    final override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || file == null || !EditorAvailability.canEmbedNotebook(file)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = true
        e.presentation.isEnabled =
            enabled(project.serviceIfCreated<NotebookSessionManager>()?.peek(file))
    }

    protected fun target(e: AnActionEvent): Pair<Project, VirtualFile>? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return project to file
    }
}

class MarimoOpenNotebookAction : MarimoSessionAction(enabled = { true }) {
    override fun actionPerformed(e: AnActionEvent) {
        val (project, file) = target(e) ?: return
        openMarimoNotebook(project, file)
    }
}

class MarimoRestartNotebookAction : MarimoSessionAction(enabled = ::canControlSession) {
    override fun actionPerformed(e: AnActionEvent) {
        val (project, file) = target(e) ?: return
        project.service<NotebookSessionManager>().withExistingActionLease(file) { it.restart() }
    }
}

class MarimoStopNotebookAction : MarimoSessionAction(enabled = ::canControlSession) {
    override fun actionPerformed(e: AnActionEvent) {
        val (project, file) = target(e) ?: return
        project.service<NotebookSessionManager>().withExistingActionLease(file) { it.stop() }
    }
}

internal inline fun NotebookSessionManager.withExistingActionLease(
    file: VirtualFile,
    action: (io.marimo.notebook.session.NotebookSessionLease) -> Unit,
) {
    val lease = leaseIfPresent(file) ?: return
    try {
        action(lease)
    } finally {
        lease.close()
    }
}
