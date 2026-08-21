/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.jcef.JBCefApp
import io.marimo.notebook.detect.MarimoDetector

/** Shows the Marimo Session submenu only for marimo notebooks in supported IDEs. */
class MarimoSessionActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            JBCefApp.isSupported() && file != null && MarimoDetector.looksLikeMarimo(file)
    }
}
