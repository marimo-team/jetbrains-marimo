/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.error

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.environment.MarimoEnvProbe
import io.marimo.notebook.session.environment.MarimoInstaller
import io.marimo.notebook.session.environment.MarimoPresence
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent

/** Probes the notebook environment and builds an [ErrorModel] for a failed server launch. */
object FailureDiagnostics {
    /**
     * Runs marimo presence detection and records launch-failure telemetry. Call off the EDT —
     * probing may shell out to the interpreter.
     */
    fun diagnose(project: Project, file: VirtualFile, error: Throwable?): ErrorModel {
        val sandbox = project.service<NotebookSessionManager>().isSandbox(file)
        val presence =
            if (sandbox) {
                MarimoPresence.Unknown
            } else {
                val probe = project.service<MarimoEnvProbe>()
                probe.invalidate()
                probe.probe(file)
            }
        val uvAvailable = UvLauncher.findUv() != null
        val reason =
            when {
                presence is MarimoPresence.Unknown -> "no_interpreter"
                presence is MarimoPresence.Missing -> "marimo_missing"
                !uvAvailable -> "uv_missing"
                else -> "other"
            }
        MarimoTelemetry.getInstance().capture(TelemetryEvent.NotebookLaunchFailed(reason))
        MarimoTelemetry.getInstance()
            .captureException(error ?: RuntimeException("marimo failed to start"))
        return ErrorModel.of(
            Failure.ServerNotStarted(error),
            presence,
            uvAvailable = uvAvailable,
            sandbox = sandbox,
        )
    }

    /**
     * Installs marimo on [file]'s interpreter via the project installer. Call on the EDT; the
     * install runs in modal progress.
     */
    fun installMarimo(project: Project, file: VirtualFile): MarimoPresence =
        project.service<MarimoInstaller>().installMarimo(file)
}
