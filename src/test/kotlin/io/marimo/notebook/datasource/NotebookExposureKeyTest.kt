/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotebookExposureKeyTest {
    @Test
    fun usesTheNormalizedProjectRelativePath() {
        assertEquals(
            "notebooks/orders.py",
            NotebookExposureKey.relativePath(
                "/workspace/project",
                "/workspace/project/examples/../notebooks/orders.py",
            ),
        )
    }

    @Test
    fun rejectsANotebookOutsideTheProject() {
        assertNull(
            NotebookExposureKey.relativePath(
                "/workspace/project",
                "/workspace/another-project/orders.py",
            )
        )
    }
}
