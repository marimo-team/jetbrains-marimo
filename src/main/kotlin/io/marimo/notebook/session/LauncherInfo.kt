/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

/** Contains non-sensitive details for the launcher of a notebook session. */
data class LauncherInfo(
    val cliPrefix: List<String>,
    val sandbox: Boolean,
)
