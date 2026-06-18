/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.packaging.management.PythonPackageManager
import java.util.concurrent.ConcurrentHashMap

/** Whether marimo is available on the interpreter resolved for a notebook. */
sealed interface MarimoPresence {
    data class Installed(val version: String) : MarimoPresence
    data object Missing : MarimoPresence

    /** No interpreter is configured, so presence is undefined. */
    data object Unknown : MarimoPresence
}

/**
 * Reports whether marimo is installed on the interpreter PyCharm resolves for a notebook, cached per
 * interpreter ([Sdk.getHomePath]). Detection trusts the package manager's snapshot when it lists marimo;
 * otherwise it confirms with `python -m marimo --version` before concluding marimo is missing, because the
 * snapshot can be empty before the platform has loaded the interpreter's packages.
 *
 * Probing may run a subprocess, so call it off the EDT.
 */
@Service(Service.Level.PROJECT)
class MarimoEnvProbe(private val project: Project) {
    private val cache = ConcurrentHashMap<String, MarimoPresence>()

    fun probe(notebook: VirtualFile): MarimoPresence {
        val sdk = SdkPythonResolver.resolveSdk(project, notebook) ?: return MarimoPresence.Unknown
        val home = sdk.homePath ?: return MarimoPresence.Unknown
        return cache.getOrPut(home) { detect(sdk, home) }
    }

    /** Drop cached results so the next probe re-detects (e.g. after an install or interpreter change). */
    fun invalidate() = cache.clear()

    private fun detect(sdk: Sdk, pythonPath: String): MarimoPresence {
        snapshotVersion(sdk)?.let { return MarimoPresence.Installed(it) }
        val cliVersion = versionViaCli(pythonPath) ?: return MarimoPresence.Missing
        return MarimoPresence.Installed(cliVersion)
    }

    private fun snapshotVersion(sdk: Sdk): String? =
        PythonPackageManager.forSdk(project, sdk).listInstalledPackagesSnapshot()
            .firstOrNull { it.name.equals(MARIMO, ignoreCase = true) }
            ?.version

    private fun versionViaCli(pythonPath: String): String? {
        val cmd = GeneralCommandLine(pythonPath, "-m", MARIMO, "--version")
        val output = runCatching { CapturingProcessHandler(cmd).runProcess(CLI_TIMEOUT_MS) }.getOrNull()
        if (output == null || output.exitCode != 0) return null
        return parseVersion(output.stdout.ifBlank { output.stderr })
    }

    companion object {
        private const val MARIMO = "marimo"
        private const val CLI_TIMEOUT_MS = 5_000
        private val VERSION = Regex("""\d+\.\d+(?:\.\w+)*""")

        /** Pull a version token out of `marimo --version` output, or null if none is present. */
        fun parseVersion(output: String): String? = VERSION.find(output)?.value
    }
}
