/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.vars

import io.marimo.notebook.server.MarimoKernelClient
import io.marimo.notebook.server.MarimoServerService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class MarimoVariablesToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MarimoVariablesPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        panel.refresh()
    }
}

private class MarimoVariablesPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("No marimo notebook focused"))
    private val tree = Tree(treeModel)

    init {
        val refresh = object : AnAction("Refresh", "Reload marimo variables", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = refresh()
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MarimoVariables", DefaultActionGroup(refresh), true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
        setContent(JBScrollPane(tree))
    }

    fun refresh() {
        val file = FileEditorManager.getInstance(project).selectedEditor?.file
        val baseUrl = file?.let { project.service<MarimoServerService>().baseUrlFor(it) }
        if (file == null || baseUrl == null) {
            setRootLabel("No running marimo notebook focused")
            return
        }
        setRootLabel("Loading ${file.name}…")
        ApplicationManager.getApplication().executeOnPooledThread {
            val vars = runCatching { MarimoKernelClient(baseUrl).readVariables(file.path) }
                .getOrDefault(emptyList())
            ApplicationManager.getApplication().invokeLater {
                val root = DefaultMutableTreeNode("${file.name} — ${vars.size} variables")
                vars.forEach { root.add(DefaultMutableTreeNode("${it.name}: ${it.value}  (${it.type})")) }
                treeModel.setRoot(root)
            }
        }
    }

    private fun setRootLabel(label: String) = treeModel.setRoot(DefaultMutableTreeNode(label))
}
