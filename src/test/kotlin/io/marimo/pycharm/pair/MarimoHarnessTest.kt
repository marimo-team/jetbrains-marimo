/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.pycharm.pair

import org.junit.Assert.assertEquals
import org.junit.Test

class MarimoHarnessTest {
    private val prefix = listOf("/usr/bin/uv", "run", "--with", "marimo", "marimo")
    private val url = "http://127.0.0.1:2718"

    private val suffix = MarimoHarness.PROMPT_SUFFIX

    @Test fun claudeWrapsPairPromptWithClaudeFlag() {
        assertEquals(
            "claude \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --claude) $suffix\"",
            MarimoHarness.CLAUDE.terminalCommand(prefix, url),
        )
    }

    @Test fun codexWrapsPairPromptWithCodexFlag() {
        assertEquals(
            "codex \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --codex) $suffix\"",
            MarimoHarness.CODEX.terminalCommand(prefix, url),
        )
    }

    @Test fun piWrapsPairPromptWithNoAgentFlag() {
        assertEquals(
            "pi \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718') $suffix\"",
            MarimoHarness.PI.terminalCommand(prefix, url),
        )
    }

    @Test fun sdkPrefixIsHonored() {
        assertEquals(
            "pi \"\$(/opt/py -m marimo pair prompt --url 'http://127.0.0.1:2718') $suffix\"",
            MarimoHarness.PI.terminalCommand(listOf("/opt/py", "-m", "marimo"), url),
        )
    }

    @Test fun decorateAppendsSuffix() {
        assertEquals("hello\n\n$suffix", MarimoHarness.decorate("hello\n"))
    }
}
