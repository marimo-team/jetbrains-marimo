/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

/**
 * Reads marimo's startup banner and scrubs credentials from retained process output.
 *
 * marimo prints one line shaped like `➜  URL: http://127.0.0.1:<port>?access_token=<token>` when
 * the server starts. When stdout is a terminal, ANSI codes sit inside the URL itself, because
 * marimo dims the query string separately from the bold URL, so codes are stripped before
 * matching. The `Network:` line and log lines never match: only the `URL:` label counts.
 */

private val ANSI_CODES = Regex("\u001B\\[[0-9;]*m")

/**
 * Requires the trailing newline so a URL split across two output chunks is never matched in its
 * truncated form; callers accumulate chunks and retry.
 */
private val URL_LINE = Regex("""(?m)^\s*(?:[➜→]\s*)?URL:\s*(http\S+)[ \t]*\r?$""")

private val ACCESS_TOKEN = Regex("""access_token=[^&\s"'<>]+""")

/** [text] with terminal color codes removed. */
fun stripAnsi(text: String): String = text.replace(ANSI_CODES, "")

/**
 * The startup URL from marimo's banner, or null while no complete `URL:` line has arrived. The
 * returned value can carry the access token; callers must treat it as a credential.
 */
fun extractStartupUrl(text: String): String? {
    val stripped = stripAnsi(text)
    val match = URL_LINE.find(stripped) ?: return null
    val lineEnd = stripped.indexOf('\n', match.range.first)
    if (lineEnd == -1) return null
    return match.groupValues[1]
}

/** [text] with every access-token value replaced. Safe to retain in logs and diagnostics. */
fun redactAccessTokens(text: String): String = text.replace(ACCESS_TOKEN, "access_token=<redacted>")
