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
class LauncherCommandTest(private val kind: Kind) {
    enum class Kind {
        UV,
        SDK,
    }

    companion object {
        const val TOKEN_FILE = "/tmp/marimo-test-token.txt"
        const val NOTEBOOK = "/proj/nb.py"
        const val WORK_DIR = "/proj"
        const val HOST = "127.0.0.1"
        const val PORT = 2718

        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<Array<Kind>> = listOf(arrayOf(Kind.UV), arrayOf(Kind.SDK))
    }

    private val exePath =
        when (kind) {
            Kind.UV -> "/usr/bin/uv"
            Kind.SDK -> "/proj/.venv/bin/python"
        }

    private val prefix =
        when (kind) {
            Kind.UV -> listOf("run", "--with", "marimo", "marimo")
            Kind.SDK -> listOf("-m", "marimo")
        }

    private fun command(
        watch: Boolean = true,
        tokenFile: String? = TOKEN_FILE,
    ): GeneralCommandLine =
        when (kind) {
            Kind.UV ->
                UvLauncher.buildCommandLine(
                    uvPath = exePath,
                    notebookPath = NOTEBOOK,
                    workDir = WORK_DIR,
                    host = HOST,
                    port = PORT,
                    watch = watch,
                    tokenPasswordFile = tokenFile,
                )
            Kind.SDK ->
                SdkLauncher.buildCommandLine(
                    pythonPath = exePath,
                    notebookPath = NOTEBOOK,
                    workDir = WORK_DIR,
                    host = HOST,
                    port = PORT,
                    watch = watch,
                    tokenPasswordFile = tokenFile,
                )
        }

    @Test
    fun buildsMarimoEditCommand() {
        val cmd = command()
        assertEquals(exePath, cmd.exePath)
        assertEquals(WORK_DIR, cmd.workDirectory?.path)
        assertEquals(
            prefix +
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
            prefix +
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
            command(watch = false).parametersList.parameters,
        )
    }

    @Test
    fun disabledTokenAuthAppendsNoToken() {
        assertEquals(
            prefix +
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
            command(tokenFile = null).parametersList.parameters,
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

class ExpectedMarimoUrlTest {
    @Test
    fun expectedUrlMatchesHostAndPort() {
        assertEquals("http://127.0.0.1:2718", MarimoLocalhost.origin("127.0.0.1", 2718))
    }
}
