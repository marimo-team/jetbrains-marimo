/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import io.marimo.notebook.detect.MarimoDetector
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile

/** Popup group rendered as a native submenu (and as a dropdown button on toolbars). */
class MarimoPairActionGroup : ActionGroup(), DumbAware {

    init {
        isPopup = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = marimoFile(e) != null
        e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
        MarimoPairMenu.actions(
            terminalActions = MarimoHarness.entries.map<MarimoHarness, AnAction> { LaunchHarnessAction(it) },
            copyAction = CopyPromptAction(),
        )
}

internal fun marimoFile(e: AnActionEvent): VirtualFile? =
    e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf(MarimoDetector::looksLikeMarimo)

/** Keeps the popup's sections readable without producing leading, trailing, or duplicate separators. */
internal object MarimoPairMenu {

    fun actions(
        terminalActions: List<AnAction>,
        copyAction: AnAction,
    ): Array<AnAction> {
        val sections = buildList {
            terminalActions.takeIf { it.isNotEmpty() }?.let { add(it) }
            add(listOf(copyAction))
        }
        return sections.flatMapIndexed { index, actions ->
            if (index == 0) actions else listOf(Separator.getInstance()) + actions
        }.toTypedArray()
    }
}

private class LaunchHarnessAction(private val harness: MarimoHarness) :
    AnAction(harness.terminalActionLabel), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = marimoFile(e) ?: return
        MarimoPairLauncher.launch(project, file, harness)
    }
}

private class CopyPromptAction : AnAction("Copy pairing prompt"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = marimoFile(e) ?: return
        MarimoPairLauncher.copyPrompt(project, file)
    }
}
