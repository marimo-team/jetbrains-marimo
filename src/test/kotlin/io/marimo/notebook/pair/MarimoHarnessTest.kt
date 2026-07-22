/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import org.junit.Assert.assertEquals
import org.junit.Test

class MarimoHarnessTest {
    private val prefix = listOf("/usr/bin/uv", "run", "--with", "marimo", "marimo")
    private val url = "http://127.0.0.1:2718"

    @Test fun claudeWrapsPairPromptWithClaudeFlag() {
        assertEquals(
            "claude \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --claude)\"",
            MarimoHarness.CLAUDE.terminalCommand(prefix, url),
        )
    }

    @Test fun codexWrapsPairPromptWithCodexFlag() {
        assertEquals(
            "codex \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --codex)\"",
            MarimoHarness.CODEX.terminalCommand(prefix, url),
        )
    }

    @Test fun opencodeWrapsPairPromptWithOpencodeFlag() {
        assertEquals(
            "opencode \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --opencode)\"",
            MarimoHarness.OPENCODE.terminalCommand(prefix, url),
        )
    }

    @Test fun genericPromptArgsHaveNoHarnessSpecificFlag() {
        assertEquals(
            prefix + listOf("pair", "prompt", "--url", url),
            MarimoHarness.promptArgs(prefix, url),
        )
    }

    @Test fun sdkPrefixIsHonored() {
        assertEquals(
            "opencode \"\$(/opt/py -m marimo pair prompt --url 'http://127.0.0.1:2718' --opencode)\"",
            MarimoHarness.OPENCODE.terminalCommand(listOf("/opt/py", "-m", "marimo"), url),
        )
    }

    @Test fun binaryNameIsTheCliId() {
        assertEquals("claude", MarimoHarness.CLAUDE.binaryName)
        assertEquals("codex", MarimoHarness.CODEX.binaryName)
        assertEquals("opencode", MarimoHarness.OPENCODE.binaryName)
    }

    @Test fun tabTitlePairsHarnessLabelWithFileName() {
        assertEquals("Claude · stocks.py", MarimoHarness.CLAUDE.tabTitle("stocks.py"))
    }

    @Test fun terminalActionLabelsDistinguishTheExplicitFallbacks() {
        assertEquals("Claude Code in Terminal", MarimoHarness.CLAUDE.terminalActionLabel)
        assertEquals("Codex CLI in Terminal", MarimoHarness.CODEX.terminalActionLabel)
        assertEquals("opencode in Terminal", MarimoHarness.OPENCODE.terminalActionLabel)
    }

    @Test fun installHintNamesTheHarness() {
        assertEquals(true, MarimoHarness.CLAUDE.installHint.contains("Claude"))
    }

    @Test fun findOnPathTrueWhenAnyDirHasExecutable() {
        val path = listOf("/a", "/b").joinToString(java.io.File.pathSeparator)
        val found = MarimoHarness.CLAUDE.findOnPath(path) { it == "/b/claude" }
        assertEquals(true, found)
    }

    @Test fun findOnPathFalseWhenNoDirHasExecutable() {
        val path = listOf("/a", "/b").joinToString(java.io.File.pathSeparator)
        assertEquals(false, MarimoHarness.CLAUDE.findOnPath(path) { false })
    }

    @Test fun findOnPathTrueWhenPathIsNull() {
        // Unknown PATH must not block a launch the terminal could still resolve.
        assertEquals(true, MarimoHarness.CLAUDE.findOnPath(null) { false })
    }

    @Test fun findOnPathTriesGivenExecutableExtensions() {
        val target = "/bin" + java.io.File.separator + "claude.exe"
        assertEquals(true, MarimoHarness.CLAUDE.findOnPath("/bin", listOf(".exe")) { it == target })
    }
}
