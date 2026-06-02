package com.github.kirangadhave.marimopycharm.launch

import com.intellij.execution.configurations.GeneralCommandLine

class SdkLauncher : MarimoLauncher {
    override val id = "sdk"

    override fun canLaunch(request: LaunchRequest): Boolean =
        SdkPythonResolver.resolvePythonPath(request.project, request.notebook) != null

    override fun launch(request: LaunchRequest): MarimoServerHandle {
        val python = SdkPythonResolver.resolvePythonPath(request.project, request.notebook)
            ?: throw NoApplicableLauncherException(request)
        val workDir = request.notebook.parent?.path ?: System.getProperty("user.dir")
        val cmd = buildCommandLine(python, request.notebook.path, workDir, request.host, request.port)
        val (themeEnv, themeHome) = MarimoThemeConfig.environment()
        cmd.withEnvironment(themeEnv, themeHome)
        return startMarimoServer(cmd, request.host, request.port)
    }

    companion object {
        fun buildCommandLine(
            pythonPath: String, notebookPath: String, workDir: String, host: String, port: Int,
        ): GeneralCommandLine = GeneralCommandLine(pythonPath)
            .withWorkDirectory(workDir)
            .withParameters(
                "-m", "marimo", "edit", notebookPath,
                "--headless", "--host", host, "--port", port.toString(), "--no-token",
            )
    }
}
