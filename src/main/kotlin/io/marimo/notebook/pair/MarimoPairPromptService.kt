/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.server.MarimoServerService

/** Generates the generic marimo pairing prompt without delivering it anywhere. */
internal object MarimoPairPromptService {

    /**
     * Starts (or reuses) the notebook server and delivers the trimmed generic pairing prompt on the
     * EDT. Errors are reported here so every delivery path has the same concise recovery message.
     */
    fun generate(project: Project, file: VirtualFile, onPrompt: (String) -> Unit) {
        MarimoPairSession.resolve(project, file, logContext = "pair prompt") { url, prefix ->
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = runCatching {
                    val command = GeneralCommandLine(MarimoHarness.promptArgs(prefix, url))
                    file.parent?.path?.let { command.withWorkDirectory(it) }
                    val output = ExecUtil.execAndGetOutput(command)
                    PromptCommandResult(output.exitCode, output.stdout)
                }.getOrNull()

                onEdt {
                    val prompt = promptText(result)
                    if (prompt == null) {
                        MarimoPairNotifications.warning(project, "Could not generate the marimo pair prompt.")
                    } else {
                        onPrompt(prompt)
                    }
                }
            }
        }
    }

    internal data class PromptCommandResult(val exitCode: Int, val stdout: String)

    /** The process result is usable only on a successful exit; stdout is delivered without padding. */
    internal fun promptText(result: PromptCommandResult?): String? =
        result?.takeIf { it.exitCode == 0 }?.stdout?.trim()

    private fun onEdt(action: () -> Unit) =
        ApplicationManager.getApplication().invokeLater(action)
}

/**
 * Shared entry guard for the pairing workflow: starts (or reuses) the notebook server, resolves the
 * marimo CLI prefix, and delivers both to [onReady] on the EDT. Failures warn the user with the same
 * concise recovery message on every pairing path and log under [logContext].
 */
internal object MarimoPairSession {

    fun resolve(
        project: Project,
        file: VirtualFile,
        logContext: String,
        onReady: (url: String, prefix: List<String>) -> Unit,
    ) {
        val server = project.service<MarimoServerService>()
        server.urlFor(file).whenComplete { url, err ->
            ApplicationManager.getApplication().invokeLater {
                if (err != null || url == null) {
                    thisLogger().warn("Could not start the marimo server for a $logContext", err)
                    MarimoPairNotifications.warning(project, "Could not start marimo.")
                    return@invokeLater
                }
                val prefix = server.marimoCliPrefixFor(file)
                if (prefix == null) {
                    MarimoPairNotifications.warning(
                        project,
                        "Could not resolve the marimo CLI (need uv on PATH or marimo in the interpreter).",
                    )
                    return@invokeLater
                }
                onReady(url, prefix)
            }
        }
    }
}

/** Shared notification formatting for the pairing workflow. */
internal object MarimoPairNotifications {

    fun warning(project: Project, message: String) = notify(project, message, NotificationType.WARNING)

    fun information(project: Project, message: String) = notify(project, message, NotificationType.INFORMATION)

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Marimo")
            .createNotification(message, type)
            .notify(project)
    }
}
