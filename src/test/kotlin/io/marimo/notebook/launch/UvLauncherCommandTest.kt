/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class UvLauncherCommandTest {
    @Test fun buildsUvRunMarimoEditCommand() {
        val cmd = UvLauncher.buildCommandLine(
            uvPath = "/usr/bin/uv",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
        )
        assertEquals("/usr/bin/uv", cmd.exePath)
        assertEquals("/proj", cmd.workDirectory?.path)
        val args = cmd.parametersList.parameters
        assertEquals(listOf("run", "--with", "marimo", "marimo", "edit", "/proj/nb.py",
            "--headless", "--host", "127.0.0.1", "--port", "2718", "--no-token"), args)
    }

    @Test fun sandboxAppendsSandboxFlag() {
        val cmd = UvLauncher.buildCommandLine(
            uvPath = "/usr/bin/uv",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
            sandbox = true,
        )
        assertEquals(listOf("run", "--with", "marimo", "marimo", "edit", "/proj/nb.py",
            "--headless", "--host", "127.0.0.1", "--port", "2718", "--no-token", "--sandbox"),
            cmd.parametersList.parameters)
    }

    @Test fun expectedUrlMatchesHostAndPort() {
        assertEquals("http://127.0.0.1:2718", expectedMarimoUrl("127.0.0.1", 2718))
    }
}
