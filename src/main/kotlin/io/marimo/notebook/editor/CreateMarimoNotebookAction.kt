/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.MarimoIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/** Name of the bundled internal template registered in plugin.xml. */
private const val TEMPLATE = "marimo Notebook"

class CreateMarimoNotebookAction :
    CreateFileFromTemplateAction("marimo Notebook", "Create a new marimo notebook", MarimoIcons.FILE),
    DumbAware {

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New marimo Notebook").addKind("marimo Notebook", MarimoIcons.FILE, TEMPLATE)
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String?): String =
        "Create marimo Notebook"
}
