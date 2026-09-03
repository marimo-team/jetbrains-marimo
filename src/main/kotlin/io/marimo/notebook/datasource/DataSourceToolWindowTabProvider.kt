/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.MarimoBundle
import io.marimo.notebook.MarimoToolWindowTabProvider
import java.nio.file.Path
import javax.swing.JComponent

class DataSourceToolWindowTabProvider : MarimoToolWindowTabProvider {
    override val id: String = ID
    override val title: String = MarimoBundle.message("datasource.panel.title")

    override fun createComponent(
        project: Project,
        selectedNotebook: () -> VirtualFile?,
    ): JComponent = DataSourceExposurePanel(project, selectedNotebook)

    companion object {
        const val ID = "data-sources"

        fun show(project: Project) {
            MarimoToolWindowTabProvider.show(project, ID)
        }

        fun show(project: Project, notebookKey: String) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val notebook =
                    project.basePath
                        ?.let(Path::of)
                        ?.resolve(notebookKey)
                        ?.normalize()
                        ?.let { LocalFileSystem.getInstance().findFileByNioFile(it) }
                        ?.takeIf { NotebookExposureKey.from(project, it) == notebookKey }
                notebook?.let { FileEditorManager.getInstance(project).openFile(it, true) }
                show(project)
            }
        }
    }
}
