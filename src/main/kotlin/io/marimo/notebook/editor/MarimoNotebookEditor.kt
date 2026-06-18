/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoEnvProbe
import io.marimo.notebook.launch.MarimoInstaller
import io.marimo.notebook.server.MarimoServerService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Component
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class MarimoNotebookEditor(private val project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val panel = object : JPanel(BorderLayout()), DataProvider {
        override fun getData(dataId: String): Any? =
            if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) file else null
    }
    private val browser = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val server = project.service<MarimoServerService>()
    private val propertyChangeSupport = PropertyChangeSupport(this)

    init {
        loadNotebook()
    }

    private fun loadNotebook() {
        showContent(JLabel("Starting marimo…", SwingConstants.CENTER))
        server.urlFor(file).whenComplete { url, err ->
            when {
                err != null -> showServerError(err)
                browser == null -> onEdt {
                    showContent(JLabel("The embedded browser isn't available in this IDE.", SwingConstants.CENTER))
                }
                else -> onEdt {
                    browser.loadURL(url)
                    showContent(browser.component)
                }
            }
        }
    }

    /** Probe off the EDT — detection may run a subprocess — then render the matching error panel. */
    private fun showServerError(err: Throwable?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val presence = project.service<MarimoEnvProbe>().probe(file)
            val model = MarimoErrorModel.of(MarimoFailure.ServerNotStarted(err), presence)
            onEdt { showContent(MarimoErrorPanel(model, ::onErrorAction)) }
        }
    }

    private fun onErrorAction(action: MarimoErrorAction) {
        when (action) {
            MarimoErrorAction.RETRY -> relaunch()
            MarimoErrorAction.INSTALL -> {
                project.service<MarimoInstaller>().installMarimo(file)
                relaunch()
            }
            MarimoErrorAction.OPEN_AS_PYTHON ->
                FileEditorManager.getInstance(project).setSelectedEditor(file, MARIMO_SOURCE_EDITOR_TYPE)
        }
    }

    /**
     * The service caches the failed handle by file URL, so a retry that reused it would replay the same
     * failure; release first to force a fresh launch.
     */
    private fun relaunch() {
        server.release(file)
        loadNotebook()
    }

    private fun showContent(component: Component) {
        panel.removeAll()
        addPairToolbar()
        panel.add(component, BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
    }

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeLater(block)

    private fun addPairToolbar() {
        val pairGroup = ActionManager.getInstance().getAction("Marimo.Pair") ?: return
        val group = DefaultActionGroup(pairGroup)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MarimoEditorToolbar", group, true)
        toolbar.targetComponent = panel
        panel.add(toolbar.component, BorderLayout.NORTH)
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = browser?.component
    override fun getName(): String = "marimo"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) =
        propertyChangeSupport.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) =
        propertyChangeSupport.removePropertyChangeListener(listener)
    override fun dispose() {
        browser?.dispose()
        server.release(file)
    }
}
