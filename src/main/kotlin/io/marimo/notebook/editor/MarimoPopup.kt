/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Where a JCEF popup opened from the notebook should go. */
sealed interface MarimoPopup {
    /** A marimo `?file=<abs path>` deep link — open it as an IDE editor tab, not a detached window. */
    data class Notebook(val path: String) : MarimoPopup

    /** Any other destination (docs, external sites) — belongs in the user's system browser. */
    data class External(val url: String) : MarimoPopup
}

/**
 * Classify a JCEF popup target. marimo opens a duplicated (or otherwise linked) notebook with
 * `window.open("?file=<abs path>", "_blank")`; left to JCEF's default that popup becomes a detached
 * OS window instead of an IDE tab. Returns null for targets not worth intercepting (blank,
 * `about:blank`), leaving JCEF's default handling in place.
 */
fun classifyMarimoPopup(targetUrl: String?): MarimoPopup? {
    val url = targetUrl?.trim().orEmpty()
    if (url.isEmpty() || url == "about:blank") return null
    val path = notebookPathFrom(url)
    return if (path != null) MarimoPopup.Notebook(path) else MarimoPopup.External(url)
}

private fun notebookPathFrom(url: String): String? {
    if (!isInternalTarget(url)) return null
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    val encoded = query.split('&')
        .firstOrNull { it.startsWith("file=") }
        ?.substringAfter('=', "")
        ?: return null
    return URLDecoder.decode(encoded, StandardCharsets.UTF_8).ifBlank { null }
}

private val ABSOLUTE_URL = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * A `?file=` deep link is only trusted when it points back at the notebook's own server. marimo emits it as
 * a relative `window.open("?file=...")`, which JCEF resolves against the localhost server. Refusing absolute
 * URLs to any other host stops external pages (or link content) from smuggling arbitrary local paths into an
 * IDE tab via `?file=`.
 */
private fun isInternalTarget(url: String): Boolean {
    if (!ABSOLUTE_URL.containsMatchIn(url)) return true
    val host = try {
        URI(url).host
    } catch (e: URISyntaxException) {
        return false
    } ?: return false
    return host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "::1"
}
