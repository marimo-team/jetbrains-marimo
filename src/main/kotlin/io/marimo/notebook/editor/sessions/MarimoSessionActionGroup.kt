/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.sessions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import io.marimo.notebook.editor.EditorAvailability

/** Shows the Marimo Session submenu only for marimo notebooks in supported IDEs. */
class MarimoSessionActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            file != null && EditorAvailability.canEmbedNotebook(file)
    }
}
