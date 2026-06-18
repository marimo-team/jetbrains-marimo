/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.detect.MarimoDetector
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoNotebookTemplateTest {
    @Test fun scaffoldOpensAsMarimoNotebook() {
        val template = javaClass.getResourceAsStream("/fileTemplates/internal/marimo Notebook.py.ft")
            ?.readBytes()?.decodeToString()
        assertTrue("template resource is bundled", template != null)
        assertTrue("scaffold satisfies marimo detection", MarimoDetector.looksLikeMarimo(template!!))
    }
}
