/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.JComponent

data class MarimoToolWindowTab(
    val id: String,
    val title: String,
    val component: JComponent,
)

/** Adds an optional project tab to the Marimo Sessions tool window. */
interface MarimoToolWindowTabProvider {
    val id: String
    val title: String

    fun createComponent(project: Project, selectedNotebook: () -> VirtualFile?): JComponent

    companion object {
        const val TOOL_WINDOW_ID = "Marimo Sessions"
        val CONTENT_ID_KEY: Key<String> = Key.create("marimo.toolWindow.contentId")
        val EP_NAME: ExtensionPointName<MarimoToolWindowTabProvider> =
            ExtensionPointName.create("io.marimo.notebook.toolWindowTabProvider")

        fun registeredTabs(
            project: Project,
            selectedNotebook: () -> VirtualFile?,
        ): List<MarimoToolWindowTab> =
            EP_NAME.extensionList.map { provider ->
                MarimoToolWindowTab(
                    provider.id,
                    provider.title,
                    provider.createComponent(project, selectedNotebook),
                )
            }

        fun show(project: Project, tabId: String) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val toolWindow =
                    ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
                        ?: return@invokeLater
                toolWindow.show {
                    val content =
                        toolWindow.contentManager.contents.firstOrNull {
                            it.getUserData(CONTENT_ID_KEY) == tabId
                        } ?: return@show
                    toolWindow.contentManager.setSelectedContent(content)
                }
            }
        }
    }
}
