/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
 * interpreter home path. Detection runs `python -m marimo --version`: a clean exit with a parseable
 * version means marimo is present. (The IDE's package-manager snapshot would avoid the subprocess, but
 * its package model is an internal API whose shape shifts between IDE releases, so the plugin relies on
 * the interpreter itself instead.)
 *
 * Probing runs a subprocess, so call it off the EDT.
 */
@Service(Service.Level.PROJECT)
class MarimoEnvProbe(private val project: Project) {
    private val cache = ConcurrentHashMap<String, MarimoPresence>()

    fun probe(notebook: VirtualFile): MarimoPresence {
        val sdk = SdkPythonResolver.resolveSdk(project, notebook) ?: return MarimoPresence.Unknown
        val home = sdk.homePath ?: return MarimoPresence.Unknown
        return cache.getOrPut(home) { detect(home) }
    }

    /** Drop cached results so the next probe re-detects (e.g. after an install or interpreter change). */
    fun invalidate() = cache.clear()

    private fun detect(pythonPath: String): MarimoPresence {
        val version = versionViaCli(pythonPath) ?: return MarimoPresence.Missing
        return MarimoPresence.Installed(version)
    }

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
