/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.detect

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarimoDetectorVirtualFileTest : BasePlatformTestCase() {
    fun testDetectsMarimoNotebookFile() {
        val file = LightVirtualFile("nb.py", "import marimo\napp = marimo.App()")
        assertTrue(MarimoDetector.looksLikeMarimo(file))
    }

    fun testIgnoresNonPythonFiles() {
        val file = LightVirtualFile("nb.txt", "import marimo\napp = marimo.App()")
        assertFalse(MarimoDetector.looksLikeMarimo(file))
    }

    fun testReDetectsAfterContentChanges() {
        val file = LightVirtualFile("nb.py", "import marimo\napp = marimo.App()")
        assertTrue(MarimoDetector.looksLikeMarimo(file))

        file.setContent(this, "print('hi')", false)
        assertFalse(MarimoDetector.looksLikeMarimo(file))
    }
}
