package com.github.kirangadhave.marimopycharm.editor

import com.github.kirangadhave.marimopycharm.server.MarimoServerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class MarimoNotebookEditor(project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private val browser = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val server = project.service<MarimoServerService>()
    private val propertyChangeSupport = PropertyChangeSupport(this)

    init {
        panel.add(JLabel("Starting marimo…", SwingConstants.CENTER), BorderLayout.CENTER)
        server.urlFor(file).whenComplete { url, err ->
            ApplicationManager.getApplication().invokeLater {
                panel.removeAll()
                if (err != null || browser == null) {
                    panel.add(JLabel(errorText(err), SwingConstants.CENTER), BorderLayout.CENTER)
                } else {
                    browser.loadURL(url)
                    panel.add(browser.component, BorderLayout.CENTER)
                }
                panel.revalidate(); panel.repaint()
            }
        }
    }

    private fun errorText(err: Throwable?): String =
        "<html><center>Could not start marimo.<br/>" +
            "Install marimo in the project interpreter (<code>pip install marimo</code>), " +
            "or make <code>uv</code> available on PATH.<br/>" +
            "${err?.message ?: ""}</center></html>"

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
