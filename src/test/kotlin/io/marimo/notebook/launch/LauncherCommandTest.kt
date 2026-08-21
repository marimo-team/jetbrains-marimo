/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import io.marimo.notebook.MarimoLocalhost
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class LauncherCommandTest(private val launcher: LauncherFixture) {
    class LauncherFixture(
        private val displayName: String,
        val executablePath: String,
        val expectedPrefix: List<String>,
        private val buildCommand: (String, Boolean, String?) -> GeneralCommandLine,
    ) {
        fun command(
            watch: Boolean = true,
            tokenFile: String? = TOKEN_FILE,
        ): GeneralCommandLine = buildCommand(executablePath, watch, tokenFile)

        override fun toString(): String = displayName
    }

    companion object {
        const val TOKEN_FILE = "/tmp/marimo-test-token.txt"
        const val NOTEBOOK = "/proj/nb.py"
        const val WORK_DIR = "/proj"
        const val HOST = "127.0.0.1"
        const val PORT = 2718

        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<Array<LauncherFixture>> =
            listOf(
                arrayOf(
                    LauncherFixture(
                        displayName = "uv",
                        executablePath = "/usr/bin/uv",
                        expectedPrefix = listOf("run", "--with", "marimo", "marimo"),
                        buildCommand = { executablePath, watch, tokenFile ->
                            UvLauncher.buildCommandLine(
                                uvPath = executablePath,
                                notebookPath = NOTEBOOK,
                                workDir = WORK_DIR,
                                host = HOST,
                                port = PORT,
                                watch = watch,
                                tokenPasswordFile = tokenFile,
                            )
                        },
                    )
                ),
                arrayOf(
                    LauncherFixture(
                        displayName = "SDK Python",
                        executablePath = "/proj/.venv/bin/python",
                        expectedPrefix = listOf("-m", "marimo"),
                        buildCommand = { executablePath, watch, tokenFile ->
                            SdkLauncher.buildCommandLine(
                                pythonPath = executablePath,
                                notebookPath = NOTEBOOK,
                                workDir = WORK_DIR,
                                host = HOST,
                                port = PORT,
                                watch = watch,
                                tokenPasswordFile = tokenFile,
                            )
                        },
                    )
                ),
            )
    }

    @Test
    fun buildsMarimoEditCommand() {
        val cmd = launcher.command()
        assertEquals(launcher.executablePath, cmd.exePath)
        assertEquals(WORK_DIR, cmd.workDirectory?.path)
        assertEquals(
            launcher.expectedPrefix +
                listOf(
                    "edit",
                    NOTEBOOK,
                    "--headless",
                    "--watch",
                    "--host",
                    HOST,
                    "--port",
                    PORT.toString(),
                    "--token-password-file",
                    TOKEN_FILE,
                ),
            cmd.parametersList.parameters,
        )
    }

    @Test
    fun watchDisabledOmitsWatchFlag() {
        assertEquals(
            launcher.expectedPrefix +
                listOf(
                    "edit",
                    NOTEBOOK,
                    "--headless",
                    "--host",
                    HOST,
                    "--port",
                    PORT.toString(),
                    "--token-password-file",
                    TOKEN_FILE,
                ),
            launcher.command(watch = false).parametersList.parameters,
        )
    }

    @Test
    fun disabledTokenAuthAppendsNoToken() {
        assertEquals(
            launcher.expectedPrefix +
                listOf(
                    "edit",
                    NOTEBOOK,
                    "--headless",
                    "--watch",
                    "--host",
                    HOST,
                    "--port",
                    PORT.toString(),
                    "--no-token",
                ),
            launcher.command(tokenFile = null).parametersList.parameters,
        )
    }
}

class UvLauncherSandboxCommandTest {
    @Test
    fun sandboxAppendsSandboxFlag() {
        val cmd =
            UvLauncher.buildCommandLine(
                uvPath = "/usr/bin/uv",
                notebookPath = "/proj/nb.py",
                workDir = "/proj",
                host = "127.0.0.1",
                port = 2718,
                sandbox = true,
                tokenPasswordFile = "/tmp/marimo-test-token.txt",
            )
        assertEquals(
            listOf(
                "run",
                "--with",
                "marimo",
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
                "/tmp/marimo-test-token.txt",
                "--sandbox",
            ),
            cmd.parametersList.parameters,
        )
    }
}

class MarimoLocalhostOriginTest {
    @Test
    fun originMatchesHostAndPort() {
        assertEquals("http://127.0.0.1:2718", MarimoLocalhost.origin("127.0.0.1", 2718))
    }
}
