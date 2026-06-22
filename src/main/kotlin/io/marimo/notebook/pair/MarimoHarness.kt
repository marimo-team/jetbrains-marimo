/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

/** An AI coding harness the user can pair with. Mirrors `marimo pair prompt` wrapping. */
enum class MarimoHarness(val id: String, val label: String, private val agentFlag: String?) {
    CLAUDE("claude", "Claude", "--claude"),
    CODEX("codex", "Codex", "--codex"),
    PI("pi", "pi", null);

    /**
     * Terminal command that starts this harness with the marimo-pair prompt.
     * [cliPrefix] are the tokens that invoke the marimo CLI (e.g. uv run ... marimo).
     */
    fun terminalCommand(cliPrefix: List<String>, url: String): String {
        val promptTokens = cliPrefix +
            listOf("pair", "prompt", "--url", "'$url'") +
            (agentFlag?.let { listOf(it) } ?: emptyList())
        return "$id \"\$(${promptTokens.joinToString(" ")}) $PROMPT_SUFFIX\""
    }

    companion object {
        /**
         * Plugin-side guidance appended to the marimo-generated prompt. Kept here rather than in
         * the core marimo-pair skill because it is specific to running the harness from PyCharm.
         */
        const val PROMPT_SUFFIX = "Always run shell commands with the sandbox disabled."

        /**
         * Tokens to run the marimo-pair prompt directly (no shell wrapping, no agent flag).
         * Suitable for capturing stdout to copy the prompt anywhere.
         */
        fun promptArgs(cliPrefix: List<String>, url: String): List<String> =
            cliPrefix + listOf("pair", "prompt", "--url", url)

        /** The marimo-generated [promptText] with the plugin-side suffix appended. */
        fun decorate(promptText: String): String = "${promptText.trimEnd()}\n\n$PROMPT_SUFFIX"
    }
}
