/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.jcef.JBCefBrowser
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
 *
 * A `?file=` link is trusted only when it is relative to the active server ([expectedOrigin]) or
 * its absolute form matches that origin exactly — not merely another loopback port.
 */
fun classifyMarimoPopup(targetUrl: String?, expectedOrigin: String?): MarimoPopup? {
    val url = targetUrl?.trim().orEmpty()
    if (url.isEmpty() || url == "about:blank") return null
    val path = notebookPathFrom(url, expectedOrigin)
    return if (path != null) MarimoPopup.Notebook(path) else MarimoPopup.External(url)
}

/**
 * Routes JCEF popups from the notebook: notebook deep links become editor tabs; external links go
 * to the system browser.
 */
internal class PopupRouter(
    private val project: Project,
    private val expectedOrigin: () -> String?,
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
                    when (val popup = classifyMarimoPopup(targetUrl, expectedOrigin())) {
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

private fun notebookPathFrom(url: String, expectedOrigin: String?): String? {
    if (!isTrustedFileLink(url, expectedOrigin)) return null
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    val encoded =
        query.split('&').firstOrNull { it.startsWith("file=") }?.substringAfter('=', "")
            ?: return null
    return URLDecoder.decode(encoded, StandardCharsets.UTF_8).ifBlank { null }
}

private fun resolveTargetUrl(url: String, expectedOrigin: String): String =
    when {
        url.startsWith("//") -> {
            val scheme = URI(expectedOrigin).scheme ?: return url
            "$scheme:$url"
        }
        ABSOLUTE_URL.containsMatchIn(url) -> url
        else -> resolveAgainstOrigin(url, expectedOrigin)
    }

private val ABSOLUTE_URL = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * A `?file=` deep link is only trusted when it targets the notebook's own server. marimo emits
 * relative `window.open("?file=...")` links against [expectedOrigin]; absolute URLs must match that
 * origin exactly so another loopback port cannot smuggle a local path into an IDE tab.
 */
private fun isTrustedFileLink(url: String, expectedOrigin: String?): Boolean {
    if (expectedOrigin == null) return false
    val resolvedOrigin = serverOrigin(resolveTargetUrl(url, expectedOrigin))
    return resolvedOrigin == expectedOrigin
}

private fun resolveAgainstOrigin(relativeUrl: String, expectedOrigin: String): String =
    try {
        URI(expectedOrigin).resolve(relativeUrl).toString()
    } catch (_: URISyntaxException) {
        relativeUrl
    }
