/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.concurrency.ThreadingAssertions
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.session.NotebookSessionLease
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.PageConfigReader
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * A second browser for the same notebook session. Created when the registry's primary
 * [NotebookView] is already mounted in another split — satisfies Swing's single-parent rule. marimo
 * marks additional clients read-only; this class only loads [NotebookSessionLease.readyUrl].
 */
internal class SecondaryNotebookView(
    project: Project,
    private val lease: NotebookSessionLease,
) : NotebookEditorView, Disposable {

    override val panel: JPanel =
        object : JPanel(BorderLayout()), DataProvider {
            override fun getData(dataId: String): Any? =
                if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) lease.notebook else null
        }
    private val browser =
        if (JBCefApp.isSupported() && !ApplicationManager.getApplication().isUnitTestMode)
            JBCefBrowser()
        else null
    private val lifecycle = project.service<NotebookSessionManager>().lifecycleFor(lease.notebook)
    private val themeController =
        ThemeController(
            disposable = this,
            onEdt = { block -> onEdt(block = block) },
            onThemeReload = ::reloadForIdeTheme,
        )
    private val popupRouter =
        PopupRouter(
            project = project,
            expectedOrigin = { navigationSnapshot.expectedOrigin },
            onEdt = { block -> onEdt(block = block) },
        )

    @Volatile private var disposed = false
    @Volatile private var navigationSnapshot = NavigationSnapshot(0, null)

    init {
        browser?.let(popupRouter::installOn)
        browser?.let(themeController::installOn)
        lifecycle.addListener { update ->
            when (update.state) {
                is MarimoNotebookState.Starting -> loadNotebook()
                is MarimoNotebookState.Stopped ->
                    onEdt {
                        if (lifecycle.isCurrent(update)) {
                            themeController.reset()
                            showContent(
                                JLabel(
                                    "marimo is stopped for ${lease.notebook.name}.",
                                    SwingConstants.CENTER,
                                )
                            )
                        }
                    }
                else -> Unit
            }
        }
        loadNotebook()
    }

    override val preferredFocusedComponent: JComponent?
        get() = browser?.component

    override fun reload() = lease.restart()

    private fun loadNotebook() = onEdt {
        ThreadingAssertions.assertEventDispatchThread()
        val navigation = NavigationSnapshot(navigationSnapshot.generation + 1, null)
        navigationSnapshot = navigation
        themeController.reset()
        showContent(JLabel("Starting marimo…", SwingConstants.CENTER))
        lease.readyUrl().whenComplete { url, err ->
            if (navigation.generation != navigationSnapshot.generation) return@whenComplete
            when {
                err != null ->
                    onEdt(navigation.generation) {
                        if (lifecycle.state is MarimoNotebookState.Stopped) return@onEdt
                        showContent(
                            JLabel(
                                "marimo couldn't be started for ${lease.notebook.name}.",
                                SwingConstants.CENTER,
                            )
                        )
                    }
                browser == null ->
                    onEdt(navigation.generation) {
                        showContent(
                            JLabel(
                                "The embedded browser isn't available in this IDE.",
                                SwingConstants.CENTER,
                            )
                        )
                    }
                else -> showNotebook(navigation.generation, url)
            }
        }
    }

    private fun showNotebook(navigation: Long, url: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val resolvedTheme = PageConfigReader.fetchDisplayTheme(url)
            onEdt(navigation) {
                if (lifecycle.state is MarimoNotebookState.Stopped) return@onEdt
                val themedUrl = themeController.prepareNotebookUrl(resolvedTheme, url)
                navigationSnapshot = NavigationSnapshot(navigation, serverOrigin(url), themedUrl)
                showContent(requireNotNull(browser).component)
                browser.loadURL(themedUrl)
            }
        }
    }

    private fun reloadForIdeTheme(themedUrl: String) {
        ThreadingAssertions.assertEventDispatchThread()
        navigationSnapshot = navigationSnapshot.withExpectedUrl(themedUrl)
        browser?.loadURL(themedUrl)
    }

    private fun showContent(component: JComponent) {
        panel.removeAll()
        panel.add(component, BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
    }

    private fun onEdt(navigation: Long? = null, block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        val guarded = {
            if (!disposed && (navigation == null || navigation == navigationSnapshot.generation)) {
                block()
            }
        }
        if (application.isDispatchThread) {
            guarded()
        } else {
            application.invokeLater(guarded)
        }
    }

    override fun dispose() {
        disposed = true
        browser?.dispose()
    }
}
