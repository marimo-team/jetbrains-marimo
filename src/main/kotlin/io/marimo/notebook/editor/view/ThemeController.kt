/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.concurrency.ThreadingAssertions
import io.marimo.notebook.editor.MarimoThemedUrl
import kotlin.math.ln
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

/**
 * Theme and font-zoom state for the embedded notebook page. Owns [loadedUrl], [appliedTheme], and
 * [followsIdeTheme]; read and written on the EDT only.
 */
internal class ThemeController(
    private val disposable: Disposable,
    private val onEdt: (() -> Unit) -> Unit,
    private val onThemeReload: (themedUrl: String) -> Unit,
) {
    var loadedUrl: String? = null
        private set

    var appliedTheme: String? = null
        private set

    var followsIdeTheme = false
        private set

    fun reset() {
        ThreadingAssertions.assertEventDispatchThread()
        loadedUrl = null
        appliedTheme = null
        followsIdeTheme = false
    }

    /** Resolves marimo's theme and returns the URL the browser should load. */
    fun prepareNotebookUrl(resolvedTheme: String?, baseUrl: String): String {
        ThreadingAssertions.assertEventDispatchThread()
        followsIdeTheme = MarimoThemedUrl.followsIdeTheme(resolvedTheme)
        appliedTheme = MarimoThemedUrl.ideTheme()
        loadedUrl = baseUrl
        return MarimoThemedUrl.of(baseUrl, resolvedTheme, appliedTheme!!)
    }

    fun installOn(browser: JBCefBrowser) {
        installEditorFontZoom(browser)
        installIdeThemeSync(browser)
    }

    /**
     * Keep the embedded notebook's zoom in step with the IDE's editor font size, so enlarging the
     * font across editors enlarges the notebook too. CEF resets zoom on navigation, so reapply on
     * every main-frame load as well as whenever the global scheme changes.
     */
    private fun installEditorFontZoom(browser: JBCefBrowser) {
        ApplicationManager.getApplication()
            .messageBus
            .connect(disposable)
            .subscribe(
                EditorColorsManager.TOPIC,
                EditorColorsListener { onEdt { applyEditorFontZoom(browser) } },
            )
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    httpStatusCode: Int,
                ) {
                    if (frame?.isMain == true) onEdt { applyEditorFontZoom(browser) }
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Map the editor font size onto a CEF zoom level, where the scale factor is `1.2^level`. The
     * platform default font size renders the notebook at its native 100%; larger fonts scale it up
     * proportionally.
     */
    private fun applyEditorFontZoom(browser: JBCefBrowser) {
        val fontSize = EditorColorsManager.getInstance().globalScheme.editorFontSize
        browser.cefBrowser.zoomLevel =
            ln(fontSize.toDouble() / FontPreferences.DEFAULT_FONT_SIZE) / ln(1.2)
    }

    /**
     * Follow the IDE's light/dark theme while it changes. The theme reaches marimo as a query
     * parameter read when the page loads, so applying a new one means reloading the page — done
     * only for a notebook that left the choice to the host, and only when the light/dark answer
     * actually changed (the look and feel also fires for font and colour-scheme edits).
     */
    private fun installIdeThemeSync(browser: JBCefBrowser) {
        ApplicationManager.getApplication()
            .messageBus
            .connect(disposable)
            .subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener { onEdt { syncIdeTheme(browser) } },
            )
    }

    private fun syncIdeTheme(browser: JBCefBrowser) {
        val url = loadedUrl ?: return
        if (!followsIdeTheme) return
        val theme = MarimoThemedUrl.ideTheme()
        if (theme == appliedTheme) return
        appliedTheme = theme
        val themedUrl = MarimoThemedUrl.withTheme(url, theme)
        onThemeReload(themedUrl)
    }
}
