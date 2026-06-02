package com.github.kirangadhave.marimopycharm.editor

import com.github.kirangadhave.marimopycharm.detect.MarimoDetector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp

class MarimoFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        JBCefApp.isSupported() && MarimoDetector.looksLikeMarimo(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        MarimoNotebookEditor(project, file)

    override fun getEditorTypeId(): String = "marimo-notebook"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
