/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import io.marimo.notebook.detect.MarimoDetector

/** Centralizes whether a file can open in a marimo editor. */
object EditorAvailability {
    fun isNotebook(file: VirtualFile): Boolean = MarimoDetector.looksLikeMarimo(file)

    fun canEmbedNotebook(file: VirtualFile): Boolean = JBCefApp.isSupported() && isNotebook(file)
}
