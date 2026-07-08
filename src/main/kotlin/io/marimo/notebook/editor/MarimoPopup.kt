/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

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
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    val encoded = query.split('&')
        .firstOrNull { it == "file" || it.startsWith("file=") }
        ?.substringAfter('=', "")
        ?: return null
    return URLDecoder.decode(encoded, StandardCharsets.UTF_8).ifBlank { null }
}
