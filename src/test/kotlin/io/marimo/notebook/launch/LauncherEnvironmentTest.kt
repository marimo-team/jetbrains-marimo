/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            )
        assertEquals("db.internal", cmd.environment["PGHOST"])
        assertEquals("db.internal", cmd.environment["CUSTOM_DB_HOST"])
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
            )
        assertEquals("db.internal", cmd.environment["PGHOST"])
        assertFalse(cmd.parametersList.parameters.any { it.contains("db.internal") })
    }

    @Test
    fun defaultExtraEnvLeavesTheEnvironmentUntouched() {
        val cmd =
            SdkLauncher.buildCommandLine(
                pythonPath = "/py",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
            )
        assertTrue(cmd.environment.isEmpty())
    }
}
