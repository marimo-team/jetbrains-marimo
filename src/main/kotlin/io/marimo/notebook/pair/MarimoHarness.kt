/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import java.io.File

/** An AI coding harness the user can pair with. Mirrors `marimo pair prompt` wrapping. */
enum class MarimoHarness(
    val id: String,
    val label: String,
    private val agentFlag: String?,
    /** How the user installs this harness, shown when its CLI isn't found. */
    val installHint: String,
) {
    CLAUDE("claude", "Claude", "--claude", "Install the Claude Code CLI, then retry."),
    CODEX("codex", "Codex", "--codex", "Install the Codex CLI, then retry."),
    OPENCODE("opencode", "opencode", "--opencode", "Install the opencode CLI, then retry.");

    /** The executable this harness runs as; looked up on the shell PATH. */
    val binaryName: String get() = id

    /** Label used when this harness remains available as an explicit terminal workflow. */
    val terminalActionLabel: String
        get() = when (this) {
            CLAUDE -> "Claude Code in Terminal"
            CODEX -> "Codex CLI in Terminal"
            OPENCODE -> "opencode in Terminal"
        }

    /** Terminal tab title so a launch reads as a session, e.g. "Claude · stocks.py". */
    fun tabTitle(fileName: String): String = "$label · $fileName"

    /**
     * Whether [binaryName] resolves in [pathValue] (a PATH string). [exists] tests a candidate
     * absolute path. On Windows an executable carries an extension (.exe/.cmd/...), so every entry
     * in [executableExtensions] is tried alongside the bare name. A null/blank [pathValue] returns
     * true: we can't tell, so don't block a launch the terminal's own shell PATH might resolve.
     */
    fun findOnPath(
        pathValue: String?,
        executableExtensions: List<String> = pathExecutableExtensions(),
        exists: (String) -> Boolean,
    ): Boolean {
        if (pathValue.isNullOrBlank()) return true
        val names = listOf(binaryName) + executableExtensions.map { binaryName + it }
        return pathValue.split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .any { dir -> names.any { name -> exists("$dir${File.separator}$name") } }
    }

    /**
     * Terminal command that starts this harness with the marimo-pair prompt.
     * [cliPrefix] are the tokens that invoke the marimo CLI (e.g. uv run ... marimo).
     */
    fun terminalCommand(cliPrefix: List<String>, url: String): String {
        val promptTokens = cliPrefix +
            listOf("pair", "prompt", "--url", "'$url'") +
            (agentFlag?.let { listOf(it) } ?: emptyList())
        return "$id \"\$(${promptTokens.joinToString(" ")})\""
    }

    companion object {
        /**
         * Tokens to run the marimo-pair prompt directly (no shell wrapping, no agent flag).
         * Suitable for capturing stdout to copy the prompt anywhere.
         */
        fun promptArgs(cliPrefix: List<String>, url: String): List<String> =
            cliPrefix + listOf("pair", "prompt", "--url", url)

        /** Executable name suffixes to try on the current OS (from PATHEXT on Windows; none elsewhere). */
        private fun pathExecutableExtensions(): List<String> {
            val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            if (!isWindows) return emptyList()
            val pathext = System.getenv("PATHEXT")
            if (pathext.isNullOrBlank()) return listOf(".EXE", ".CMD", ".BAT", ".COM")
            return pathext.split(File.pathSeparatorChar).filter { it.isNotBlank() }
        }
    }
}
