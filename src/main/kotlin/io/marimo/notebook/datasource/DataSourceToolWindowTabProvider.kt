/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.MarimoBundle
import io.marimo.notebook.MarimoToolWindowTabProvider
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
    }
}
