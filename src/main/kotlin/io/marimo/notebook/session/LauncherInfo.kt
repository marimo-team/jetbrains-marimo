/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

/** Non-sensitive details about the launcher serving a notebook session. */
data class LauncherInfo(
    val cliPrefix: List<String>,
    val sandbox: Boolean,
)
