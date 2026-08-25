/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import javax.swing.JComponent

/** The UI surface a marimo [io.marimo.notebook.editor.MarimoNotebookEditor] renders. */
internal interface NotebookEditorView {
    val panel: JComponent

    val preferredFocusedComponent: JComponent?

    fun reload()
}
