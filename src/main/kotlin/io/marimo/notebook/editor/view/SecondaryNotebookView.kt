/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import io.marimo.notebook.session.NotebookSessionLease
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * A second browser for the same notebook session (D1). Created when the registry's primary
 * [NotebookView] is already mounted in another split — satisfies Swing's single-parent rule. marimo
 * marks additional clients read-only; this class only loads [NotebookSessionLease.readyUrl].
 */
internal class SecondaryNotebookView(private val lease: NotebookSessionLease) :
    NotebookEditorView, Disposable {

    override val panel: JPanel =
        object : JPanel(BorderLayout()), DataProvider {
            override fun getData(dataId: String): Any? =
                if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) lease.notebook else null
        }
    private val browser =
        if (JBCefApp.isSupported() && !ApplicationManager.getApplication().isUnitTestMode)
            JBCefBrowser()
        else null

    @Volatile private var disposed = false

    init {
        if (browser == null) {
            panel.add(
                JLabel("The embedded browser isn't available in this IDE.", SwingConstants.CENTER)
            )
        } else {
            panel.add(browser.component, BorderLayout.CENTER)
            loadReadyUrl()
        }
    }

    override val preferredFocusedComponent: JComponent?
        get() = browser?.component

    override fun reload() = loadReadyUrl()

    private fun loadReadyUrl() {
        lease.readyUrl().whenComplete { url, err ->
            onEdt {
                if (disposed || browser == null) return@onEdt
                when {
                    err != null ->
                        panel.removeAll().also {
                            panel.add(
                                JLabel(
                                    "marimo couldn't be started for ${lease.notebook.name}.",
                                    SwingConstants.CENTER,
                                )
                            )
                            panel.revalidate()
                            panel.repaint()
                        }
                    else -> browser.loadURL(url)
                }
            }
        }
    }

    private fun onEdt(block: () -> Unit) =
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) block()
        }

    override fun dispose() {
        disposed = true
        browser?.dispose()
    }
}
