/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.session.LeaseOwner
import io.marimo.notebook.session.NotebookSessionManager
import java.util.concurrent.atomic.AtomicBoolean

internal class PromptLifetime(parent: Disposable, private val closeLease: () -> Unit) : Disposable {
    private val expired = AtomicBoolean(false)

    init {
        Disposer.register(parent, this)
    }

    val isExpired: Boolean
        get() = expired.get()

    override fun dispose() {
        if (expired.compareAndSet(false, true)) closeLease()
    }
}

/** Generates the generic marimo pairing prompt without delivering it anywhere. */
internal object MarimoPairPromptService {

    /**
     * Starts (or reuses) the notebook server and delivers the trimmed generic pairing prompt on the
     * EDT. Errors are reported here so every delivery path has the same concise recovery message.
     * Delivery is tied to [project]: blank stdout is a failure, and a disposed project expires the
     * callback instead of copying or notifying.
     */
    fun generate(project: Project, file: VirtualFile, onPrompt: (String) -> Unit) {
        MarimoPairSession.resolvePrompt(project, file) { url, prefix, closeLease ->
            if (project.isDisposed) {
                closeLease()
                return@resolvePrompt
            }
            val lifetime = PromptLifetime(project, closeLease)
            runCatching {
                ApplicationManager.getApplication().executeOnPooledThread {
                    if (lifetime.isExpired) return@executeOnPooledThread
                    val result = runCatching {
                        val command = GeneralCommandLine(MarimoHarness.promptArgs(prefix, url))
                        file.parent?.path?.let { command.withWorkDirectory(it) }
                        val output = ExecUtil.execAndGetOutput(command)
                        PromptCommandResult(output.exitCode, output.stdout)
                    }
                        .getOrNull()
                    deliverOnEdt(project, lifetime, result, onPrompt)
                }
            }
                .onFailure { Disposer.dispose(lifetime) }
        }
    }

    internal data class PromptCommandResult(val exitCode: Int, val stdout: String)

    internal enum class PromptDelivery {
        DELIVERED,
        FAILED,
        EXPIRED,
    }

    /**
     * The process result is usable only on a successful exit; stdout is delivered without padding
     * and must contain a non-blank prompt.
     */
    internal fun promptText(result: PromptCommandResult?): String? =
        result?.takeIf { it.exitCode == 0 }?.stdout?.trim()?.takeUnless(String::isBlank)

    /** Delivers [result] unless the project (or prompt lifetime) has already been disposed. */
    internal fun completePrompt(
        result: PromptCommandResult?,
        disposed: Boolean,
        onPrompt: (String) -> Unit,
    ): PromptDelivery {
        if (disposed) return PromptDelivery.EXPIRED
        val prompt = promptText(result) ?: return PromptDelivery.FAILED
        onPrompt(prompt)
        return PromptDelivery.DELIVERED
    }

    private fun deliverOnEdt(
        project: Project,
        lifetime: PromptLifetime,
        result: PromptCommandResult?,
        onPrompt: (String) -> Unit,
    ) {
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    try {
                        val delivery = completePrompt(result, lifetime.isExpired, onPrompt)
                        if (delivery == PromptDelivery.FAILED) {
                            MarimoPairNotifications.warning(
                                project,
                                "Could not generate the marimo pair prompt.",
                            )
                        }
                    } finally {
                        Disposer.dispose(lifetime)
                    }
                },
                { lifetime.isExpired },
            )
    }
}

/**
 * Pair work requires a ready server and an active CLI prefix. The callback receives a lease close
 * action after both values are available.
 */
internal object MarimoPairSession {

    fun resolvePrompt(
        project: Project,
        file: VirtualFile,
        onReady: (url: String, prefix: List<String>, closeLease: () -> Unit) -> Unit,
    ) =
        resolve(
            project,
            file,
            LeaseOwner.PAIR_PROMPT,
            "Could not start the marimo server for a pair prompt",
            onReady,
        )

    fun resolveTerminal(
        project: Project,
        file: VirtualFile,
        onReady: (url: String, prefix: List<String>, closeLease: () -> Unit) -> Unit,
    ) =
        resolve(
            project,
            file,
            LeaseOwner.PAIR_TERMINAL,
            "Could not start the marimo server for a pair session",
            onReady,
        )

    private fun resolve(
        project: Project,
        file: VirtualFile,
        owner: LeaseOwner,
        startFailureLog: String,
        onReady: (url: String, prefix: List<String>, closeLease: () -> Unit) -> Unit,
    ) {
        val lease = project.service<NotebookSessionManager>().acquire(file, owner)
        lease.readyUrl().whenComplete { url, err ->
            runCatching {
                ApplicationManager.getApplication().invokeLater {
                    if (err != null || url == null) {
                        thisLogger().warn(startFailureLog, err)
                        MarimoPairNotifications.warning(project, "Could not start marimo.")
                        lease.close()
                        return@invokeLater
                    }
                    val prefix = lease.launcherInfo()?.cliPrefix
                    if (prefix == null) {
                        MarimoPairNotifications.warning(
                            project,
                            "Could not resolve the marimo CLI (need uv on PATH or marimo in the interpreter).",
                        )
                        lease.close()
                        return@invokeLater
                    }
                    runCatching { onReady(url, prefix, lease::close) }.onFailure { lease.close() }
                }
            }
                .onFailure { lease.close() }
        }
    }
}

/** Shared notification formatting for the pairing workflow. */
internal object MarimoPairNotifications {

    fun warning(project: Project, message: String) =
        notify(project, message, NotificationType.WARNING)

    fun information(project: Project, message: String) =
        notify(project, message, NotificationType.INFORMATION)

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Marimo")
            .createNotification(message, type)
            .notify(project)
    }
}
