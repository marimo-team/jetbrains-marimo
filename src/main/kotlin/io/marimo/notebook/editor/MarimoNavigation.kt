/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoNotebookState
import java.net.URI
import java.net.URISyntaxException

/**
 * Identity checks for the view's asynchronous callbacks. Every loadNotebook call starts a new
 * navigation generation, and JCEF load errors additionally must match the server origin the view
 * currently renders — CEF delivers errors for whatever the browser was doing, including a page that
 * a Restart already replaced.
 */

/** The scheme://host:port prefix that identifies one server. Credentials and paths are dropped. */
internal fun serverOrigin(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val uri =
        try {
            URI(url)
        } catch (_: URISyntaxException) {
            return null
        }
    val scheme = uri.scheme ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    return if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
}

/** True when a main-frame load error belongs to the server the view currently renders. */
internal fun loadErrorIsCurrent(failedUrl: String?, expectedOrigin: String?): Boolean =
    expectedOrigin != null && serverOrigin(failedUrl) == expectedOrigin

/** The navigation identity a JCEF callback must retain from its originating attempt. */
internal data class NavigationSnapshot(
    val generation: Long,
    val expectedOrigin: String?,
    val expectedUrl: String? = null,
) {
    /** Keeps the navigation generation while replacing its current main-frame URL. */
    fun withExpectedUrl(url: String): NavigationSnapshot = copy(expectedUrl = url)
}

/**
 * The generation for a current load error, or null when the callback belongs to another attempt.
 */
internal fun loadErrorGeneration(failedUrl: String?, snapshot: NavigationSnapshot): Long? {
    val expectedUrl = snapshot.expectedUrl
    if (expectedUrl != null && failedUrl != expectedUrl) return null
    return snapshot.generation.takeIf { loadErrorIsCurrent(failedUrl, snapshot.expectedOrigin) }
}

/** A terminal or stopping lifecycle owns the panel instead of a pending browser callback. */
internal fun canRenderNotebookFor(state: MarimoNotebookState): Boolean =
    state !is MarimoNotebookState.Stopped
