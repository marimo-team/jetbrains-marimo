/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class MarimoInstallerCommandTest {
    @Test fun buildsPipInstallMarimoCommand() {
        val cmd = MarimoInstaller.pipInstallCommand("/usr/bin/python3")
        assertEquals("/usr/bin/python3", cmd.exePath)
        assertEquals(listOf("-m", "pip", "install", "marimo"), cmd.parametersList.parameters)
    }

    @Test fun buildsUvInstallMarimoCommandTargetingInterpreter() {
        val cmd = MarimoInstaller.uvInstallCommand("/opt/homebrew/bin/uv", "/proj/.venv/bin/python")
        assertEquals("/opt/homebrew/bin/uv", cmd.exePath)
        assertEquals(
            listOf("pip", "install", "--python", "/proj/.venv/bin/python", "marimo"),
            cmd.parametersList.parameters,
        )
    }
}
