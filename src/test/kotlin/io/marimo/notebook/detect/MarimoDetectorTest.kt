/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoDetectorTest {
    @Test fun detectsMarimoHeader() {
        val src = """
            import marimo
            app = marimo.App(width="medium")
        """.trimIndent()
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }

    @Test fun rejectsPlainPython() {
        assertFalse(MarimoDetector.looksLikeMarimo("print('hello')"))
    }

    @Test fun rejectsImportWithoutApp() {
        assertFalse(MarimoDetector.looksLikeMarimo("import marimo  # but no app"))
    }

    @Test fun toleratesAliasedImport() {
        val src = "import marimo as mo\napp = mo.App()"
        assertTrue(MarimoDetector.looksLikeMarimo(src))
    }
}
