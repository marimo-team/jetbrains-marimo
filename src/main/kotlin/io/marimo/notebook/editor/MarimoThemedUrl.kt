/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.util.Urls
import io.marimo.notebook.MarimoTheme

/**
 * Decides the theme the embedded editor is loaded with.
 *
 * marimo resolves a `system` theme against the browser's `prefers-color-scheme`, which in the
 * embedded JCEF browser reports the OS appearance and not the IDE's look and feel — so a notebook
 * set to `system` would render light inside a dark IDE. Inside the IDE, `system` is therefore
 * resolved here, to the IDE's own light/dark, and passed explicitly as marimo's `theme` query
 * parameter (marimo 0.23.15+; older versions ignore it). The URL leaves a resolved light or dark
 * theme alone. Launchers also set MARIMO_DISPLAY_THEME for versions that replace the query
 * parameter with an environment override. That override takes effect at server launch.
 */
object MarimoThemedUrl {

    /** The IDE's own light/dark, in marimo's vocabulary. */
    fun ideTheme(): String = MarimoTheme.ideTheme()

    /**
     * Whether [resolvedTheme] — marimo's effective `display.theme` — leaves the choice to the host,
     * and so should track the IDE. Leave an unreadable theme alone to preserve explicit settings.
     */
    fun followsIdeTheme(resolvedTheme: String?): Boolean = resolvedTheme == "system"

    /** [url] with the IDE's theme applied, or unchanged when the user pinned one. */
    fun of(url: String, resolvedTheme: String?, ideTheme: String): String =
        if (followsIdeTheme(resolvedTheme)) withTheme(url, ideTheme) else url

    /** [url] with [theme] applied unconditionally. */
    fun withTheme(url: String, theme: String): String =
        Urls.newFromEncoded(url).addParameters(mapOf("theme" to theme)).toExternalForm()
}
