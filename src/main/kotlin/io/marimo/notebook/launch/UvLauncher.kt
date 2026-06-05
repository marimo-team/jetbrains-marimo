/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

class UvLauncher : MarimoLauncher {
    override val id = "uv"

    override fun canLaunch(request: LaunchRequest): Boolean = findUv() != null

    override fun launch(request: LaunchRequest): MarimoServerHandle {
        val uv = findUv() ?: throw NoApplicableLauncherException(request)
        val workDir = request.notebook.parent?.path ?: System.getProperty("user.dir")
        val cmd = buildCommandLine(uv, request.notebook.path, workDir, request.host, request.port)
        val (themeEnv, themeHome) = MarimoThemeConfig.environment()
        cmd.withEnvironment(themeEnv, themeHome)
        return startMarimoServer(cmd, request.host, request.port)
    }

    override fun marimoCliPrefix(request: LaunchRequest): List<String>? =
        findUv()?.let { listOf(it, "run", "--with", "marimo", "marimo") }

    /**
     * GUI-launched IDEs on macOS inherit a minimal PATH that excludes Homebrew (/opt/homebrew/bin),
     * the uv installer dir (~/.local/bin), and /usr/local/bin — so a PATH-only lookup misses uv that
     * the user's shell can see. Fall back to the well-known install locations before giving up.
     */
    internal fun findUv(): String? {
        com.intellij.execution.configurations.PathEnvironmentVariableUtil
            .findExecutableInPathOnAnyOS("uv")?.let { return it.absolutePath }

        val home = System.getProperty("user.home")
        return FALLBACK_UV_PATHS
            .map { it.replaceFirst("~", home) }
            .map(::File)
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    companion object {
        fun buildCommandLine(
            uvPath: String, notebookPath: String, workDir: String, host: String, port: Int,
        ): GeneralCommandLine = GeneralCommandLine(uvPath)
            .withWorkDirectory(workDir)
            .withParameters(
                "run", "--with", "marimo", "marimo", "edit", notebookPath,
                "--headless", "--host", host, "--port", port.toString(), "--no-token",
            )

        private val FALLBACK_UV_PATHS = listOf(
            "~/.local/bin/uv",
            "/opt/homebrew/bin/uv",
            "/usr/local/bin/uv",
        )
    }
}
