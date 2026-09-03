/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarimoNotebookSelectionTest : BasePlatformTestCase() {
    fun testSelectsTheFocusedFileWhenItIsAMarimoNotebook() {
        val python = LightVirtualFile("script.py", "print('hello')")
        val notebook =
            LightVirtualFile(
                "orders.py",
                "import marimo\napp = marimo.App()\n",
            )

        assertEquals(notebook, MarimoNotebookSelection.from(listOf(notebook, python)))
    }

    fun testDoesNotSelectAMarimoNotebookFromAnUnfocusedSplit() {
        val python = LightVirtualFile("script.py", "print('hello')")
        val notebook =
            LightVirtualFile(
                "orders.py",
                "import marimo\napp = marimo.App()\n",
            )

        assertNull(MarimoNotebookSelection.from(listOf(python, notebook)))
    }

    fun testReturnsNullWithoutAMarimoNotebook() {
        val python = LightVirtualFile("script.py", "print('hello')")

        assertNull(MarimoNotebookSelection.from(listOf(python)))
    }
}
