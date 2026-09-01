/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.detect.MarimoDetector

/** Resolves the marimo notebook selected in the editor. */
object MarimoNotebookSelection {
    fun selected(project: Project): VirtualFile? =
        from(FileEditorManager.getInstance(project).selectedFiles.asList())

    internal fun from(files: List<VirtualFile>): VirtualFile? =
        files.firstOrNull(MarimoDetector::looksLikeMarimo)
}
