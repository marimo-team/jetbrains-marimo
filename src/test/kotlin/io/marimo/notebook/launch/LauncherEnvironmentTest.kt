/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LauncherEnvironmentTest {
    private val extraEnv = mapOf("PGHOST" to "db.internal", "CUSTOM_DB_HOST" to "db.internal")

    @Test
    fun sdkCommandCarriesExtraEnvOutsideArgv() {
        val cmd =
            SdkLauncher.buildCommandLine(
                pythonPath = "/proj/.venv/bin/python",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
                extraEnv = extraEnv,
                theme = "dark",
            )
        assertEquals("db.internal", cmd.environment["PGHOST"])
        assertEquals("db.internal", cmd.environment["CUSTOM_DB_HOST"])
        assertEquals(
            "dark",
            cmd.environment["MARIMO_DISPLAY_THEME"],
        )
        assertFalse(
            "env values must never appear in argv",
            cmd.parametersList.parameters.any { it.contains("db.internal") },
        )
    }

    @Test
    fun uvCommandCarriesExtraEnvOutsideArgv() {
        val cmd =
            UvLauncher.buildCommandLine(
                uvPath = "/usr/bin/uv",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
                extraEnv = extraEnv,
                theme = "light",
            )
        assertEquals("db.internal", cmd.environment["PGHOST"])
        assertFalse(cmd.parametersList.parameters.any { it.contains("db.internal") })
        assertEquals(
            "light",
            cmd.environment["MARIMO_DISPLAY_THEME"],
        )
    }

    @Test
    fun bothLaunchersPassTheResolvedThemeWithAndWithoutWatch() {
        for (expected in listOf("light", "dark")) {
            for (watch in listOf(false, true)) {
                val sdk =
                    SdkLauncher.buildCommandLine(
                        pythonPath = "/py",
                        notebookPath = "/proj/nb.py",
                        workDir = "/proj",
                        host = "127.0.0.1",
                        port = 2718,
                        watch = watch,
                        theme = expected,
                    )
                assertEquals(mapOf("MARIMO_DISPLAY_THEME" to expected), sdk.environment)
                for (sandbox in listOf(false, true)) {
                    val uv =
                        UvLauncher.buildCommandLine(
                            uvPath = "/usr/bin/uv",
                            notebookPath = "/proj/nb.py",
                            workDir = "/proj",
                            host = "127.0.0.1",
                            port = 2718,
                            watch = watch,
                            sandbox = sandbox,
                            theme = expected,
                        )
                    assertEquals(mapOf("MARIMO_DISPLAY_THEME" to expected), uv.environment)
                }
            }
        }
    }

    @Test
    fun unresolvedThemeDoesNotOverrideTheEnvironment() {
        val sdk = SdkLauncher.buildCommandLine("/py", "/nb.py", "/", "127.0.0.1", 2718)
        val uv = UvLauncher.buildCommandLine("/uv", "/nb.py", "/", "127.0.0.1", 2718)
        assertFalse(sdk.environment.containsKey("MARIMO_DISPLAY_THEME"))
        assertFalse(uv.environment.containsKey("MARIMO_DISPLAY_THEME"))
    }
}
