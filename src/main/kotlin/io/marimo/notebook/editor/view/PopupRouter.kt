/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.jcef.JBCefBrowser
import io.marimo.notebook.MarimoLocalhost
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter

/** Where a JCEF popup opened from the notebook should go. */
sealed interface MarimoPopup {
    /**
     * A marimo `?file=<abs path>` deep link — open it as an IDE editor tab, not a detached window.
     */
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

/**
 * Routes JCEF popups from the notebook: notebook deep links become editor tabs; external links go
 * to the system browser.
 */
internal class PopupRouter(
    private val project: Project,
    private val onEdt: (() -> Unit) -> Unit,
) {
    fun installOn(browser: JBCefBrowser) {
        browser.jbCefClient.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean {
                    when (val popup = classifyMarimoPopup(targetUrl)) {
                        null -> return false
                        is MarimoPopup.Notebook -> openNotebookTab(popup.path)
                        is MarimoPopup.External -> BrowserUtil.browse(popup.url)
                    }
                    return true
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Resolve a just-created notebook path to a [com.intellij.openapi.vfs.VirtualFile] and open it
     * as an editor tab. The VFS refresh is synchronous and must run off the EDT.
     */
    private fun openNotebookTab(path: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val target = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            if (target == null) {
                thisLogger().warn("marimo popup: could not resolve notebook path $path")
                return@executeOnPooledThread
            }
            onEdt { FileEditorManager.getInstance(project).openFile(target, true) }
        }
    }
}

private fun notebookPathFrom(url: String): String? {
    if (!isInternalTarget(url)) return null
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    val encoded =
        query.split('&').firstOrNull { it.startsWith("file=") }?.substringAfter('=', "")
            ?: return null
    return URLDecoder.decode(encoded, StandardCharsets.UTF_8).ifBlank { null }
}

private val ABSOLUTE_URL = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * A `?file=` deep link is only trusted when it points back at the notebook's own server. marimo
 * emits it as a relative `window.open("?file=...")`, which JCEF resolves against the localhost
 * server. Refusing absolute URLs to any other host stops external pages (or link content) from
 * smuggling arbitrary local paths into an IDE tab via `?file=`.
 */
private fun isInternalTarget(url: String): Boolean {
    if (!ABSOLUTE_URL.containsMatchIn(url)) return true
    val host =
        try {
            URI(url).host
        } catch (e: URISyntaxException) {
            return false
        } ?: return false
    return MarimoLocalhost.isLoopbackHost(host)
}
