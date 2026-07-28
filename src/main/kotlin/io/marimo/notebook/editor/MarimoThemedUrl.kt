/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.ui.JBColor
import com.intellij.util.Urls

/**
 * Decides the theme the embedded editor is loaded with.
 *
 * marimo resolves a `system` theme against the browser's `prefers-color-scheme`, which in the embedded
 * JCEF browser reports the OS appearance and not the IDE's look and feel — so a notebook set to `system`
 * would render light inside a dark IDE. Inside the IDE, `system` is therefore resolved here, to the IDE's
 * own light/dark, and passed explicitly as marimo's `theme` query parameter (marimo 0.23.15+; older
 * versions ignore it). A theme the user pinned to light or dark is theirs, and is left alone.
 */
object MarimoThemedUrl {

    /** The IDE's own light/dark, in marimo's vocabulary. */
    fun ideTheme(): String = if (JBColor.isBright()) "light" else "dark"

    /**
     * Whether [resolvedTheme] — marimo's effective `display.theme` — leaves the choice to the host, and
     * so should track the IDE. An unreadable theme is treated as `system`, which is marimo's own default.
     */
    fun followsIdeTheme(resolvedTheme: String?): Boolean =
        resolvedTheme == null || resolvedTheme == "system"

    /** [url] with the IDE's theme applied, or unchanged when the user pinned one. */
    fun of(url: String, resolvedTheme: String?, ideTheme: String): String =
        if (followsIdeTheme(resolvedTheme)) withTheme(url, ideTheme) else url

    /** [url] with [theme] applied unconditionally. */
    fun withTheme(url: String, theme: String): String =
        Urls.newFromEncoded(url).addParameters(mapOf("theme" to theme)).toExternalForm()
}
