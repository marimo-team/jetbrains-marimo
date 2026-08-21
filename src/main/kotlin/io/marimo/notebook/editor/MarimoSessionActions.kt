/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

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
import com.intellij.ui.jcef.JBCefApp
import io.marimo.notebook.detect.MarimoDetector
import io.marimo.notebook.server.MarimoServerService
import io.marimo.notebook.server.MarimoSessionSnapshot

/** True when a session exists whose process is alive, so Restart and Stop have a target. */
internal fun canControlSession(status: MarimoSessionSnapshot?): Boolean =
    status?.state?.isLive == true

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
abstract class MarimoSessionAction(private val enabled: (MarimoSessionSnapshot?) -> Boolean) :
    AnAction(), DumbAware {

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    final override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (
            project == null ||
                file == null ||
                !JBCefApp.isSupported() ||
                !MarimoDetector.looksLikeMarimo(file)
        ) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = true
        e.presentation.isEnabled =
            enabled(project.serviceIfCreated<MarimoServerService>()?.statusFor(file))
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
        project.service<MarimoServerService>().restart(file)
    }
}

class MarimoStopNotebookAction : MarimoSessionAction(enabled = ::canControlSession) {
    override fun actionPerformed(e: AnActionEvent) {
        val (project, file) = target(e) ?: return
        project.service<MarimoServerService>().stop(file)
    }
}
