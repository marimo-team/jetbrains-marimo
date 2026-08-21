/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import io.marimo.notebook.detect.MarimoDetector

/** Owns the policy for recognizing notebooks and gating the embedded editor on JCEF. */
object EditorAvailability {
    fun isNotebook(file: VirtualFile): Boolean = MarimoDetector.looksLikeMarimo(file)

    fun canEmbedNotebook(file: VirtualFile): Boolean = JBCefApp.isSupported() && isNotebook(file)
}
