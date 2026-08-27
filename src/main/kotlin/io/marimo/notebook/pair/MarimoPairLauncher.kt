/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.EnvironmentUtil
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import java.awt.datatransfer.StringSelection
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * A harness terminal needs a live notebook URL. When the session is ready, this type starts or
 * reuses that terminal and holds a `PAIR_TERMINAL` lease for the tab.
 */
object MarimoPairLauncher {

    /** Ensure the server is up, then open a terminal running [harness] with the pair prompt. */
    fun launch(project: Project, file: VirtualFile, harness: MarimoHarness) {
        val path = EnvironmentUtil.getValue("PATH")
        if (!harness.findOnPath(path) { java.io.File(it).canExecute() }) {
            MarimoPairNotifications.warning(
                project,
                "${harness.label} isn't installed. ${harness.installHint}",
            )
            return
        }

        val manager = TerminalToolWindowManager.getInstance(project)
        val contentManager = manager.toolWindow?.contentManager
        if (reuseExistingTerminal(manager, contentManager, file, harness)) {
            MarimoTelemetry.getInstance()
                .capture(TelemetryEvent.PairStarted(method = "terminal", harness = harness.id))
            return
        }

        MarimoPairSession.resolveTerminal(project, file) { url, prefix, closeLease ->
            if (reuseExistingTerminal(manager, manager.toolWindow?.contentManager, file, harness)) {
                closeLease()
                MarimoTelemetry.getInstance()
                    .capture(TelemetryEvent.PairStarted(method = "terminal", harness = harness.id))
                return@resolveTerminal
            }
            val command = harness.terminalCommand(prefix, url)
            if (openTab(project, manager, file, harness, command, closeLease)) {
                MarimoTelemetry.getInstance()
                    .capture(TelemetryEvent.PairStarted(method = "terminal", harness = harness.id))
            }
        }
    }

    /**
     * Opens the pair terminal for [harness] on [file]. A repeated launch reuses the live session
     * for the same notebook and harness (matched by path and harness id, not tab title) and
     * replaces a tab whose shell has already exited.
     */
    private fun reuseExistingTerminal(
        manager: TerminalToolWindowManager,
        contentManager: ContentManager?,
        file: VirtualFile,
        harness: MarimoHarness,
    ): Boolean {
        val contents = contentManager?.contents?.toList().orEmpty()
        val tabs = contents.map {
            PairTerminalTabs.Tab(
                it.getUserData(PairTerminalTabs.IDENTITY_KEY),
                isSessionAlive(it),
            )
        }

        return when (val action = PairTerminalTabs.resolve(tabs, file.path, harness.id)) {
            is PairTerminalTabs.Action.Focus -> {
                contentManager?.setSelectedContent(contents[action.index])
                manager.toolWindow?.activate(null)
                true
            }
            is PairTerminalTabs.Action.Launch -> {
                action.closeIndex?.let { manager.closeTab(contents[it]) }
                false
            }
        }
    }

    private fun openTab(
        project: Project,
        manager: TerminalToolWindowManager,
        file: VirtualFile,
        harness: MarimoHarness,
        command: String,
        closeLease: () -> Unit,
    ): Boolean {
        val workDir = file.parent?.path ?: project.basePath
        try {
            val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
            val tabState =
                TerminalTabState().apply {
                    myTabName = harness.tabTitle(file.name)
                    myWorkingDirectory = workDir
                }
            // A null content manager lets the platform resolve — and lazily create — the terminal
            // tool
            // window, so the first pair launch works even before the tool window has been opened.
            val widget = manager.createNewSession(runner, tabState, null)
            val content = terminalContent(manager.toolWindow?.contentManager, widget)
            if (content == null) {
                closeLease()
                return false
            }
            content.putUserData(
                PairTerminalTabs.IDENTITY_KEY,
                PairTerminalTabs.Identity(file.path, harness.id),
            )
            Disposer.register(content) { closeLease() }
            widget.addTerminationCallback(closeLease, content)
            widget.sendCommandToExecute(command)
            manager.toolWindow?.activate(null)
            return true
        } catch (e: Throwable) {
            closeLease()
            thisLogger().warn("Failed to open a terminal for the pair session", e)
            MarimoTelemetry.getInstance().captureException(e)
            MarimoPairNotifications.warning(
                project,
                "Could not open a terminal. Run this manually:\n$command",
            )
            return false
        }
    }

    /** A tab is reusable only while its shell process is still connected. */
    private fun isSessionAlive(content: Content): Boolean {
        val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return false
        return widget.ttyConnector?.isConnected == true
    }

    private fun terminalContent(
        contentManager: ContentManager?,
        widget: TerminalWidget,
    ): Content? =
        contentManager?.contents?.firstOrNull {
            TerminalToolWindowManager.findWidgetByContent(it) === widget
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
