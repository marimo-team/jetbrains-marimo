/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoDetectorTest {
    @Test
    fun detectsMarimoHeader() {
        val src =
            """
            import marimo
            app = marimo.App(width="medium")
            """
                .trimIndent()
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun rejectsPlainPython() {
        assertFalse(MarimoDetector.looksLikeMarimo("print('hello')"))
    }

    @Test
    fun rejectsImportWithoutApp() {
        assertFalse(MarimoDetector.looksLikeMarimo("import marimo  # but no app"))
    }

    @Test
    fun toleratesAliasedImport() {
        val src = "import marimo as mo\napp = mo.App()"
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun detectsMarimoFirstInMultiModuleImport() {
        val src = "import marimo, os\napp = marimo.App()"
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun detectsAliasedMarimoFirstInMultiModuleImport() {
        val src = "import marimo as mo, os\napp = mo.App()"
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun rejectsCommentedMarimoExample() {
        val src = "# import marimo\n# app = marimo.App()"
        assertFalse(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun rejectsDocstringMarimoExample() {
        val src =
            """
            '''
            import marimo
            app = marimo.App()
            '''
            """
                .trimIndent()
        assertFalse(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun rejectsSimilarModuleName() {
        val src = "import marimo_tools\napp = marimo_tools.App()"
        assertFalse(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun rejectsPrefixedAppReceiver() {
        val src = "import marimo\napp = xmarimo.App()"
        assertFalse(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun rejectsAppReferenceInsideString() {
        val src = "import marimo\nexample = \"app = marimo.App()\""
        assertFalse(MarimoDetector.looksLikeMarimo(src))
    }

    @Test
    fun detectsValidHeaderAroundCommentsAndStrings() {
        val src =
            """
            # Generated notebook
            description = "a marimo notebook"
            import marimo as mo  # application dependency
            app = mo.App()
            """
                .trimIndent()
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }
}
