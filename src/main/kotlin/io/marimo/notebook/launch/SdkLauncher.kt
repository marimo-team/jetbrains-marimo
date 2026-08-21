/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine

class SdkLauncher : MarimoLauncher {
    override val id = "sdk"

    override fun canLaunch(request: LaunchRequest): Boolean =
        SdkPythonResolver.resolvePythonPath(request.project, request.notebook) != null

    override fun launch(request: LaunchRequest): MarimoServerHandle {
        val python =
            SdkPythonResolver.resolvePythonPath(request.project, request.notebook)
                ?: throw NoApplicableLauncherException(request)
        val workDir = request.workDir ?: NotebookWorkDir.resolve(request.project, request.notebook)
        fun command(watch: Boolean) =
            buildCommandLine(
                python,
                request.notebook.path,
                workDir,
                request.host,
                request.port,
                watch,
                request.tokenPasswordFile,
            )
        return startMarimoServer(
            command(watch = true),
            request.host,
            request.port,
            watchFallbackCmd = { command(watch = false) },
            authenticatedUrl = request.authenticatedUrl,
            tokenPasswordFile = request.tokenPasswordFile,
        )
    }

    override fun marimoCliPrefix(request: LaunchRequest): List<String>? =
        SdkPythonResolver.resolvePythonPath(request.project, request.notebook)?.let {
            listOf(it, "-m", "marimo")
        }

    companion object {
        fun buildCommandLine(
            pythonPath: String,
            notebookPath: String,
            workDir: String,
            host: String,
            port: Int,
            watch: Boolean = true,
            tokenPasswordFile: String? = null,
        ): GeneralCommandLine {
            val params =
                MarimoCommandLine.buildEditParams(
                    cliPrefix = listOf("-m", "marimo"),
                    notebookPath = notebookPath,
                    host = host,
                    port = port,
                    watch = watch,
                    tokenPasswordFile = tokenPasswordFile,
                )
            return GeneralCommandLine(pythonPath).withWorkDirectory(workDir).withParameters(params)
        }
    }
}
