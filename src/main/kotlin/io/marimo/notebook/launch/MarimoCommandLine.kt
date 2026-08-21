/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

/** Owns the common `marimo edit` CLI contract across launcher-specific prefixes. */
object MarimoCommandLine {
    fun buildEditParams(
        cliPrefix: List<String>,
        notebookPath: String,
        host: String,
        port: Int,
        watch: Boolean = true,
        tokenPasswordFile: String? = null,
        sandbox: Boolean = false,
    ): List<String> = buildList {
        addAll(cliPrefix)
        addAll(listOf("edit", notebookPath, "--headless"))
        if (watch) add("--watch")
        addAll(listOf("--host", host, "--port", port.toString()))
        if (tokenPasswordFile != null) {
            add("--token-password-file")
            add(tokenPasswordFile)
        } else {
            add("--no-token")
        }
        if (sandbox) add("--sandbox")
    }
}
