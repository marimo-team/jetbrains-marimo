/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.EnvironmentUtil
import java.awt.datatransfer.StringSelection
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object MarimoPairLauncher {

    /** Ensure the server is up, then open a terminal running [harness] with the pair prompt. */
    fun launch(project: Project, file: VirtualFile, harness: MarimoHarness) {
        val path = EnvironmentUtil.getValue("PATH")
        if (!harness.findOnPath(path) { java.io.File(it).canExecute() }) {
            MarimoPairNotifications.warning(project, "${harness.label} isn't installed. ${harness.installHint}")
            return
        }

        MarimoPairSession.resolve(project, file, logContext = "pair session") { url, prefix ->
            runInTerminal(project, file, harness, harness.terminalCommand(prefix, url))
            MarimoTelemetry.getInstance()
                .capture(TelemetryEvent.PairStarted(method = "terminal", harness = harness.id))
        }
    }

    /**
     * Opens the pair terminal for [harness] on [file]. A repeated launch reuses the live session for
     * the same notebook (matched by file path, not tab title, so notebooks sharing a file name keep
     * separate sessions) and replaces a tab whose shell has already exited.
     */
    private fun runInTerminal(project: Project, file: VirtualFile, harness: MarimoHarness, command: String) {
        val manager = TerminalToolWindowManager.getInstance(project)
        val contentManager = manager.toolWindow?.contentManager
        val contents = contentManager?.contents?.toList().orEmpty()
        val tabs = contents.map {
            PairTerminalTabs.Tab(it.getUserData(PairTerminalTabs.NOTEBOOK_KEY), isSessionAlive(it))
        }

        when (val action = PairTerminalTabs.resolve(tabs, file.path)) {
            is PairTerminalTabs.Action.Focus -> {
                contentManager?.setSelectedContent(contents[action.index])
                manager.toolWindow?.activate(null)
            }
            is PairTerminalTabs.Action.Launch -> {
                action.closeIndex?.let { manager.closeTab(contents[it]) }
                openTab(project, manager, contentManager, file, harness.tabTitle(file.name), command)
            }
        }
    }

    private fun openTab(
        project: Project,
        manager: TerminalToolWindowManager,
        contentManager: ContentManager?,
        file: VirtualFile,
        title: String,
        command: String,
    ) {
        val workDir = file.parent?.path ?: project.basePath
        try {
            val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
            val tabState = TerminalTabState().apply {
                myTabName = title
                myWorkingDirectory = workDir
            }
            // A null content manager lets the platform resolve — and lazily create — the terminal tool
            // window, so the first pair launch works even before the tool window has been opened.
            val widget = manager.createNewSession(runner, tabState, null)
            tagWithNotebook(contentManager ?: manager.toolWindow?.contentManager, widget, file.path)
            widget.sendCommandToExecute(command)
            manager.toolWindow?.activate(null)
        } catch (e: Throwable) {
            thisLogger().warn("Failed to open a terminal for the pair session", e)
            MarimoTelemetry.getInstance().captureException(e)
            MarimoPairNotifications.warning(project, "Could not open a terminal. Run this manually:\n$command")
        }
    }

    /** A tab is reusable only while its shell process is still connected. */
    private fun isSessionAlive(content: Content): Boolean {
        val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return false
        return widget.ttyConnector?.isConnected == true
    }

    private fun tagWithNotebook(contentManager: ContentManager?, widget: TerminalWidget, notebookPath: String) {
        contentManager?.contents
            ?.firstOrNull { TerminalToolWindowManager.findWidgetByContent(it) === widget }
            ?.putUserData(PairTerminalTabs.NOTEBOOK_KEY, notebookPath)
    }

    /** Generate the generic marimo-pair prompt and put it on the clipboard. */
    fun copyPrompt(project: Project, file: VirtualFile) {
        MarimoPairPromptService.generate(project, file) { prompt ->
            CopyPasteManager.getInstance().setContents(StringSelection(prompt))
            MarimoPairNotifications.information(project, "Pairing prompt copied.")
            MarimoTelemetry.getInstance()
                .capture(TelemetryEvent.PairStarted(method = "copy_prompt", harness = "none"))
        }
    }
}
