/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine

class SdkLauncher : MarimoLauncher {
    override val id = "sdk"

    override fun canLaunch(request: LaunchRequest): Boolean =
        SdkPythonResolver.resolvePythonPath(request.project, request.notebook) != null

    override fun launch(request: LaunchRequest): MarimoServerHandle {
        val python = SdkPythonResolver.resolvePythonPath(request.project, request.notebook)
            ?: throw NoApplicableLauncherException(request)
        val workDir = request.notebook.parent?.path ?: System.getProperty("user.dir")
        val (themeEnv, themeHome) = MarimoThemeConfig.environment()
        fun command(watch: Boolean) =
            buildCommandLine(python, request.notebook.path, workDir, request.host, request.port, watch)
                .apply { withEnvironment(themeEnv, themeHome) }
        return startMarimoServer(
            command(watch = true), request.host, request.port,
            watchFallbackCmd = { command(watch = false) },
        )
    }

    override fun marimoCliPrefix(request: LaunchRequest): List<String>? =
        SdkPythonResolver.resolvePythonPath(request.project, request.notebook)
            ?.let { listOf(it, "-m", "marimo") }

    companion object {
        fun buildCommandLine(
            pythonPath: String, notebookPath: String, workDir: String, host: String, port: Int,
            watch: Boolean = true,
        ): GeneralCommandLine {
            val params = buildList {
                addAll(listOf("-m", "marimo", "edit", notebookPath, "--headless"))
                if (watch) add("--watch")
                addAll(listOf("--host", host, "--port", port.toString(), "--no-token"))
            }
            return GeneralCommandLine(pythonPath).withWorkDirectory(workDir).withParameters(params)
        }
    }
}
