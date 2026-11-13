/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProcessCanceledException
import io.marimo.notebook.MarimoTheme

/**
 * Reads marimo's effective theme before adding the IDE's environment override. Runs off the EDT.
 */
internal object LaunchThemeResolver {
    fun command(
        pythonPrefix: List<String>,
        notebookPath: String,
        workDir: String,
        extraEnv: Map<String, String>,
    ): GeneralCommandLine =
        GeneralCommandLine(pythonPrefix + listOf("-c", CONFIG_PROBE, notebookPath))
            .withWorkDirectory(workDir)
            .withEnvironment(extraEnv)

    fun resolve(
        command: GeneralCommandLine,
        ideTheme: String = MarimoTheme.ideTheme(),
        run: (GeneralCommandLine) -> ProcessOutput = {
            CapturingProcessHandler(it).runProcess(TIMEOUT_MS)
        },
    ): String? {
        val output =
            try {
                run(command)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        // Config APIs differ across marimo versions. Failure must not overwrite a pinned theme.
        // Do not log probe output: config errors can contain credentials or user settings.
        if (output.isTimeout || output.isCancelled || output.exitCode != 0) return null
        return when (val theme = output.stdout.trim()) {
            "light",
            "dark" -> theme
            "system" -> ideTheme
            else -> null
        }
    }

    private const val TIMEOUT_MS = 30_000

    // Read config without importing or executing the notebook. Support both dictionary and typed
    // config objects. A user-supplied environment theme takes priority on old marimo versions too.
    internal val CONFIG_PROBE =
        """
        import os
        import sys
        from marimo._config.manager import get_default_config_manager
        config = get_default_config_manager(current_path=sys.argv[1]).get_config()
        theme = config["display"]["theme"] if isinstance(config, dict) else config.display.theme
        print(os.environ.get("MARIMO_DISPLAY_THEME", theme))
        """
            .trimIndent()
}
