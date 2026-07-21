/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent

/**
 * Installs marimo into the interpreter PyCharm resolves for a notebook, then re-probes presence.
 *
 * Installation shells out rather than going through the IDE's package-manager service, whose
 * programmatic install API is marked internal and changes signature between IDE releases. When the
 * interpreter has pip, `<python> -m pip install marimo` is used; uv-created environments often ship
 * without pip, so those fall back to `uv pip install --python <interpreter> marimo`. Relaunching the
 * editor after a successful install is the caller's responsibility.
 */
@Service(Service.Level.PROJECT)
class MarimoInstaller(private val project: Project) {

    /**
     * Show modal install progress for marimo on [notebook]'s interpreter and return the freshly probed
     * presence. Returns [MarimoPresence.Unknown] when no interpreter is configured to install into. Must
     * be called on the EDT; the install and the re-probe run off it, inside the modal progress task.
     */
    fun installMarimo(notebook: VirtualFile): MarimoPresence {
        val sdk = SdkPythonResolver.resolveSdk(project, notebook) ?: return MarimoPresence.Unknown
        val pythonPath = sdk.homePath ?: return MarimoPresence.Unknown
        val presence = try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<MarimoPresence, RuntimeException>(
                {
                    resolveInstallCommand(pythonPath)?.let { command ->
                        val handler = CapturingProcessHandler(command)
                        val indicator = ProgressManager.getInstance().progressIndicator
                        if (indicator != null) handler.runProcessWithProgressIndicator(indicator) else handler.runProcess()
                    }
                    val probe = project.service<MarimoEnvProbe>()
                    probe.invalidate()
                    probe.probe(notebook)
                },
                "Installing marimo",
                true,
                project,
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            MarimoTelemetry.getInstance().captureException(e)
            throw e
        }
        MarimoTelemetry.getInstance()
            .capture(TelemetryEvent.MarimoInstallResult(success = presence is MarimoPresence.Installed))
        return presence
    }

    /**
     * The command that installs marimo into [pythonPath]'s environment, or null when neither pip nor uv
     * can reach it. Runs a subprocess to detect pip, so call it off the EDT.
     */
    private fun resolveInstallCommand(pythonPath: String): GeneralCommandLine? = when {
        hasPip(pythonPath) -> pipInstallCommand(pythonPath)
        else -> UvLauncher.findUv()?.let { uvInstallCommand(it, pythonPath) }
    }

    private fun hasPip(pythonPath: String): Boolean {
        val cmd = GeneralCommandLine(pythonPath, "-m", "pip", "--version")
        val output = runCatching { CapturingProcessHandler(cmd).runProcess(PIP_CHECK_TIMEOUT_MS) }.getOrNull()
        return output != null && output.exitCode == 0
    }

    companion object {
        private const val MARIMO = "marimo"
        private const val PIP_CHECK_TIMEOUT_MS = 5_000

        fun pipInstallCommand(pythonPath: String): GeneralCommandLine =
            GeneralCommandLine(pythonPath, "-m", "pip", "install", MARIMO)

        fun uvInstallCommand(uvPath: String, pythonPath: String): GeneralCommandLine =
            GeneralCommandLine(uvPath, "pip", "install", "--python", pythonPath, MARIMO)
    }
}
