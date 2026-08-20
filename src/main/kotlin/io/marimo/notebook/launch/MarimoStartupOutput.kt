/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

/**
 * Scrubs credentials from retained process output and diagnostic URLs.
 */

private val ACCESS_TOKEN = Regex("""access_token=[^&\s"'<>]+""")

/** [text] with every access-token value replaced. Safe to retain in logs and diagnostics. */
fun redactAccessTokens(text: String): String = text.replace(ACCESS_TOKEN, "access_token=<redacted>")
