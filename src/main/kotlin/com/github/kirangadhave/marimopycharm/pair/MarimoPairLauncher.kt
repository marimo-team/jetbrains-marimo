package com.github.kirangadhave.marimopycharm.pair

import com.github.kirangadhave.marimopycharm.server.MarimoServerService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object MarimoPairLauncher {

    /** Ensure the server is up, then open a terminal running [harness] with the pair prompt. */
    fun launch(project: Project, file: VirtualFile, harness: MarimoHarness) {
        val server = project.service<MarimoServerService>()
        server.urlFor(file).whenComplete { url, err ->
            ApplicationManager.getApplication().invokeLater {
                if (err != null || url == null) {
                    notify(project, "Could not start marimo: ${err?.message ?: "unknown error"}")
                    return@invokeLater
                }
                val prefix = server.marimoCliPrefixFor(file)
                if (prefix == null) {
                    notify(project, "Could not resolve the marimo CLI (need uv on PATH or marimo in the interpreter).")
                    return@invokeLater
                }
                runInTerminal(project, file, harness.terminalCommand(prefix, url))
            }
        }
    }

    private fun runInTerminal(project: Project, file: VirtualFile, command: String) {
        val workDir = file.parent?.path ?: project.basePath
        try {
            val widget = TerminalToolWindowManager.getInstance(project)
                .createLocalShellWidget(workDir, "marimo pair")
            widget.executeCommand(command)
        } catch (e: Throwable) {
            notify(project, "Could not open a terminal. Run this manually:\n$command")
        }
    }

    /** Capture the marimo-pair prompt (with the plugin suffix) and put it on the clipboard. */
    fun copyPrompt(project: Project, file: VirtualFile) {
        val server = project.service<MarimoServerService>()
        server.urlFor(file).whenComplete { url, err ->
            if (err != null || url == null) {
                onEdt { notify(project, "Could not start marimo: ${err?.message ?: "unknown error"}") }
                return@whenComplete
            }
            val prefix = server.marimoCliPrefixFor(file)
            if (prefix == null) {
                onEdt { notify(project, "Could not resolve the marimo CLI (need uv on PATH or marimo in the interpreter).") }
                return@whenComplete
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                val output = runCatching {
                    val cmd = GeneralCommandLine(MarimoHarness.promptArgs(prefix, url))
                    file.parent?.path?.let { cmd.withWorkDirectory(it) }
                    ExecUtil.execAndGetOutput(cmd)
                }.getOrNull()
                onEdt {
                    if (output == null || output.exitCode != 0) {
                        notify(project, "Could not generate the marimo pair prompt.")
                    } else {
                        CopyPasteManager.getInstance()
                            .setContents(StringSelection(MarimoHarness.decorate(output.stdout)))
                        notify(project, "marimo pair prompt copied to clipboard.", NotificationType.INFORMATION)
                    }
                }
            }
        }
    }

    private fun onEdt(action: () -> Unit) =
        ApplicationManager.getApplication().invokeLater(action)

    private fun notify(project: Project, message: String, type: NotificationType = NotificationType.WARNING) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Marimo")
            .createNotification(message, type)
            .notify(project)
    }
}
