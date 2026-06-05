package com.github.kirangadhave.marimopycharm.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class SdkLauncherCommandTest {
    @Test fun buildsPythonModuleMarimoEditCommand() {
        val cmd = SdkLauncher.buildCommandLine(
            pythonPath = "/proj/.venv/bin/python",
            notebookPath = "/proj/nb.py",
            workDir = "/proj",
            host = "127.0.0.1",
            port = 2718,
        )
        assertEquals("/proj/.venv/bin/python", cmd.exePath)
        assertEquals("/proj", cmd.workDirectory?.path)
        val args = cmd.parametersList.parameters
        assertEquals(
            listOf("-m", "marimo", "edit", "/proj/nb.py",
                "--headless", "--host", "127.0.0.1", "--port", "2718", "--no-token"),
            args,
        )
    }
}
