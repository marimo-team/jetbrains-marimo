/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class UvLauncherCommandTest {
    private companion object {
        const val TOKEN_FILE = "/tmp/marimo-test-token.txt"
    }

    @Test fun buildsUvRunMarimoEditCommand() {
        val cmd = UvLauncher.buildCommandLine(
            uvPath = "/usr/bin/uv",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
            tokenPasswordFile = TOKEN_FILE,
        )
        assertEquals("/usr/bin/uv", cmd.exePath)
        assertEquals("/proj", cmd.workDirectory?.path)
        val args = cmd.parametersList.parameters
        assertEquals(
            listOf(
                "run", "--with", "marimo", "marimo", "edit", "/proj/nb.py",
                "--headless", "--watch", "--host", "127.0.0.1", "--port", "2718",
                "--token-password-file", TOKEN_FILE,
            ),
            args,
        )
    }

    @Test fun sandboxAppendsSandboxFlag() {
        val cmd = UvLauncher.buildCommandLine(
            uvPath = "/usr/bin/uv",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
            sandbox = true,
            tokenPasswordFile = TOKEN_FILE,
        )
        assertEquals(
            listOf(
                "run", "--with", "marimo", "marimo", "edit", "/proj/nb.py",
                "--headless", "--watch", "--host", "127.0.0.1", "--port", "2718",
                "--token-password-file", TOKEN_FILE, "--sandbox",
            ),
            cmd.parametersList.parameters,
        )
    }

    @Test fun watchDisabledOmitsWatchFlag() {
        val cmd = UvLauncher.buildCommandLine(
            uvPath = "/usr/bin/uv",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
            watch = false,
            tokenPasswordFile = TOKEN_FILE,
        )
        assertEquals(
            listOf(
                "run", "--with", "marimo", "marimo", "edit", "/proj/nb.py",
                "--headless", "--host", "127.0.0.1", "--port", "2718",
                "--token-password-file", TOKEN_FILE,
            ),
            cmd.parametersList.parameters,
        )
    }

    @Test fun disabledTokenAuthAppendsNoToken() {
        val cmd = UvLauncher.buildCommandLine(
            uvPath = "/usr/bin/uv",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
            tokenPasswordFile = null,
        )
        assertEquals(
            listOf(
                "run", "--with", "marimo", "marimo", "edit", "/proj/nb.py",
                "--headless", "--watch", "--host", "127.0.0.1", "--port", "2718", "--no-token",
            ),
            cmd.parametersList.parameters,
        )
    }

    @Test fun expectedUrlMatchesHostAndPort() {
        assertEquals("http://127.0.0.1:2718", expectedMarimoUrl("127.0.0.1", 2718))
    }
}
