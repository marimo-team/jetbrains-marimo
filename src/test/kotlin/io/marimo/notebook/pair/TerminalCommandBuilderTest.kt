/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class TerminalCommandBuilderQuoteTest(private val token: String, private val quoted: String) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<Array<String>> =
            listOf(
                arrayOf("simple", "'simple'"),
                arrayOf("/path with spaces/uv", "'/path with spaces/uv'"),
                arrayOf("it's", "'it'\\''s'"),
                arrayOf("\$HOME", "'\$HOME'"),
                arrayOf("a&b", "'a&b'"),
                arrayOf("", "''"),
            )
    }

    @Test
    fun singleQuotesForPosixSh() {
        assertEquals(quoted, TerminalCommandBuilder.quote(token))
    }
}

class TerminalCommandBuilderTest {
    @Test
    fun argvKeepsExecutableAndArgsUnquoted() {
        assertEquals(
            listOf("/usr/bin/uv", "run", "--with", "marimo"),
            TerminalCommandBuilder.argv("/usr/bin/uv", listOf("run", "--with", "marimo")),
        )
    }

    @Test
    fun posixShellStringQuotesEveryToken() {
        assertEquals(
            "'/path with spaces/uv' 'run' '--with' 'marimo' 'pair' 'prompt' '--url' " +
                "'http://127.0.0.1:2718' 'a&b' '\$HOME' 'it'\\''s'",
            TerminalCommandBuilder.posixShellString(
                "/path with spaces/uv",
                listOf(
                    "run",
                    "--with",
                    "marimo",
                    "pair",
                    "prompt",
                    "--url",
                    "http://127.0.0.1:2718",
                    "a&b",
                    "\$HOME",
                    "it's",
                ),
            ),
        )
    }
}
