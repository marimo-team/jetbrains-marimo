/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

/**
 * Builds pair-terminal commands as argv, then as a POSIX `sh` string when the IDE only accepts
 * text. The named shell is POSIX `sh` (including Git Bash on Windows). PowerShell and `cmd.exe` are
 * not supported.
 *
 * Prefer [argv] for process APIs. [posixShellString] exists because
 * `TerminalWidget.sendCommandToExecute` takes a single string.
 */
internal object TerminalCommandBuilder {

    /** Process image plus arguments, with no shell quoting. */
    fun argv(executable: String, args: List<String>): List<String> = listOf(executable) + args

    /**
     * One POSIX `sh` command. Every token is single-quoted so spaces, `$`, `&`, and quotes stay
     * literal.
     */
    fun posixShellString(executable: String, args: List<String>): String =
        argv(executable, args).joinToString(" ") { quote(it) }

    fun quote(token: String): String = "'" + token.replace("'", "'\\''") + "'"
}
