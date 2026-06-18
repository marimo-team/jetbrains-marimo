/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.detect.MarimoDetector
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.server.MarimoServerService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware

/**
 * Opens a marimo notebook in marimo's isolated uv environment (PEP 723 deps). Disabled with an
 * explanatory tooltip when uv — which marimo's sandbox requires — isn't on the machine. The uv lookup
 * runs on a background thread ([ActionUpdateThread.BGT]) so it never blocks the EDT.
 */
class RunInSandboxAction : AnAction(), DumbAware {
    init {
        templatePresentation.icon = MarimoIcons.FILE
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (e.project == null || file == null || !MarimoDetector.looksLikeMarimo(file)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = true
        val hasUv = UvLauncher.findUv() != null
        e.presentation.isEnabled = hasUv
        e.presentation.description =
            if (hasUv) "Run this marimo notebook in an isolated uv environment (PEP 723 deps)"
            else MarimoErrorPanel.SANDBOX_NEEDS_UV
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        project.service<MarimoServerService>().enableSandbox(file)
        val editors = FileEditorManager.getInstance(project)
        editors.getEditors(file).filterIsInstance<MarimoNotebookEditor>().forEach { it.reload() }
        editors.openFile(file, true)
    }
}
