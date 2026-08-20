/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class SdkLauncherCommandTest {
    private companion object {
        const val TOKEN_FILE = "/tmp/marimo-test-token.txt"
    }

    @Test
    fun buildsPythonModuleMarimoEditCommand() {
        val cmd =
            SdkLauncher.buildCommandLine(
                pythonPath = "/proj/.venv/bin/python",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
                tokenPasswordFile = TOKEN_FILE,
            )
        assertEquals("/proj/.venv/bin/python", cmd.exePath)
        assertEquals("/proj", cmd.workDirectory?.path)
        val args = cmd.parametersList.parameters
        assertEquals(
            listOf(
                "-m",
                "marimo",
                "edit",
                "/proj/nb.py",
                "--headless",
                "--watch",
                "--host",
                "127.0.0.1",
                "--port",
                "2718",
                "--token-password-file",
                TOKEN_FILE,
            ),
            args,
        )
    }

    @Test
    fun omitsWatchWhenDisabled() {
        val cmd =
            SdkLauncher.buildCommandLine(
                pythonPath = "/proj/.venv/bin/python",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
                watch = false,
                tokenPasswordFile = TOKEN_FILE,
            )
        assertEquals(
            listOf(
                "-m",
                "marimo",
                "edit",
                "/proj/nb.py",
                "--headless",
                "--host",
                "127.0.0.1",
                "--port",
                "2718",
                "--token-password-file",
                TOKEN_FILE,
            ),
            cmd.parametersList.parameters,
        )
    }

    @Test
    fun disabledTokenAuthAppendsNoToken() {
        val cmd =
            SdkLauncher.buildCommandLine(
                pythonPath = "/proj/.venv/bin/python",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
                tokenPasswordFile = null,
            )
        assertEquals(
            listOf(
                "-m",
                "marimo",
                "edit",
                "/proj/nb.py",
                "--headless",
                "--watch",
                "--host",
                "127.0.0.1",
                "--port",
                "2718",
                "--no-token",
            ),
            cmd.parametersList.parameters,
        )
    }
}
