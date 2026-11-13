/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchThemeResolverTest {
    private val command = GeneralCommandLine("python")

    private fun result(theme: String, code: Int = 0): ProcessOutput =
        ProcessOutput().apply {
            appendStdout(theme)
            exitCode = code
        }

    @Test
    fun explicitThemesWinOverTheIde() {
        assertEquals("dark", LaunchThemeResolver.resolve(command, "light") { result("dark\n") })
        assertEquals("light", LaunchThemeResolver.resolve(command, "dark") { result("light\n") })
    }

    @Test
    fun systemUsesTheIdeTheme() {
        for (theme in listOf("light", "dark")) {
            assertEquals(theme, LaunchThemeResolver.resolve(command, theme) { result("system\n") })
        }
    }

    @Test
    fun failedOrUnrecognizedProbeDoesNotOverrideConfig() {
        assertNull(LaunchThemeResolver.resolve(command, "light") { result("dark", 1) })
        assertNull(LaunchThemeResolver.resolve(command, "light") { result("unknown") })
        assertNull(
            LaunchThemeResolver.resolve(command, "light") { throw IOException("private config") }
        )
        assertNull(
            LaunchThemeResolver.resolve(command, "light") { result("dark").apply { setTimeout() } }
        )
        assertNull(
            LaunchThemeResolver.resolve(command, "light") {
                result("dark").apply { setCancelled() }
            }
        )
    }

    @Test
    fun probeUsesTheNotebookRuntimeDirectoryAndEnvironment() {
        for (prefix in
            listOf(
                listOf("/venv/python"),
                listOf("/bin/uv", "run", "--with", "marimo", "python"),
            )) {
            val probe =
                LaunchThemeResolver.command(
                    prefix,
                    "/proj/a notebook.py",
                    "/proj",
                    mapOf("MARIMO_DISPLAY_THEME" to "dark"),
                )
            assertEquals(prefix.first(), probe.exePath)
            assertEquals(
                prefix.drop(1) +
                    listOf("-c", LaunchThemeResolver.CONFIG_PROBE, "/proj/a notebook.py"),
                probe.parametersList.parameters,
            )
            assertEquals("/proj", probe.workDirectory?.path)
            assertEquals("dark", probe.environment["MARIMO_DISPLAY_THEME"])
            assertFalse(probe.parametersList.parameters.contains("dark"))
        }
    }
}
