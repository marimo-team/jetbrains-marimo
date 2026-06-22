/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.detect.MarimoDetector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

/** Editor type id of the source tab; also the target FileEditorManager selects for "Open as Python file". */
const val MARIMO_SOURCE_EDITOR_TYPE = "marimo-source"

/**
 * Second editor tab exposing a marimo notebook's raw Python source. The marimo provider hides the
 * platform's default text editor, so this provider re-supplies it as a real text editor whose only
 * change is the tab name "Source" instead of "Text". State persistence delegates to the platform
 * text editor provider.
 */
class MarimoSourceEditorProvider : FileEditorProvider, DumbAware {
    private val platform get() = TextEditorProvider.getInstance()

    override fun accept(project: Project, file: VirtualFile): Boolean =
        MarimoDetector.looksLikeMarimo(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        object : PsiAwareTextEditorImpl(project, file, platform) {
            override fun getName(): String = "Source"
        }

    override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState =
        platform.readState(element, project, file)

    override fun writeState(state: FileEditorState, project: Project, element: Element) =
        platform.writeState(state, project, element)

    override fun getEditorTypeId(): String = MARIMO_SOURCE_EDITOR_TYPE

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
