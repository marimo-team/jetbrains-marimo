package com.github.kirangadhave.marimopycharm.pair

import com.github.kirangadhave.marimopycharm.detect.MarimoDetector
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
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
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
        MarimoHarness.entries.map<MarimoHarness, AnAction> { LaunchHarnessAction(it) }
            .toTypedArray() + CopyPromptAction()
}

private fun marimoFile(e: AnActionEvent): VirtualFile? =
    e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf(MarimoDetector::looksLikeMarimo)

private class LaunchHarnessAction(private val harness: MarimoHarness) :
    AnAction(harness.label), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = marimoFile(e) ?: return
        MarimoPairLauncher.launch(project, file, harness)
    }
}

private class CopyPromptAction : AnAction("Copy prompt"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = marimoFile(e) ?: return
        MarimoPairLauncher.copyPrompt(project, file)
    }
}
