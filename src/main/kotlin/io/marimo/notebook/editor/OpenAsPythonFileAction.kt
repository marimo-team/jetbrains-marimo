/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.detect.MarimoDetector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ide.actions.OpenInRightSplitAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.python.PythonFileType

/**
 * Opens a marimo notebook's raw Python source in a split beside the notebook. A file maps to a single
 * tab per editor window, so the source can't share the notebook's tab; the split gives it its own pane,
 * independent of the notebook's inline Source tab.
 */
class OpenAsPythonFileAction : AnAction(), DumbAware {
    init {
        templatePresentation.icon = PythonFileType.INSTANCE.icon
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            e.project != null && file != null && MarimoDetector.looksLikeMarimo(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        OpenInRightSplitAction.openInRightSplit(project, file, null, true)
        FileEditorManager.getInstance(project).setSelectedEditor(file, MARIMO_SOURCE_EDITOR_TYPE)
    }
}
