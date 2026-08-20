/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class NotebookWorkDirTest : BasePlatformTestCase() {

    fun testNotebookInsideAContentRootResolvesToThatRoot() {
        val nested = myFixture.addFileToProject("sub/dir/nb.py", "import marimo\n").virtualFile
        val expected = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
            .getContentRootForFile(nested)!!.path
        assertEquals(expected, NotebookWorkDir.resolve(project, nested))
        assertFalse(
            "the notebook's own directory is the bug this fixes",
            NotebookWorkDir.resolve(project, nested).endsWith("sub/dir"),
        )
    }

    fun testFileOutsideEveryContentRootFallsBackToTheProjectBasePath() {
        val orphan = LightVirtualFile("loose.py", "import marimo\n")
        assertEquals(project.basePath, NotebookWorkDir.resolve(project, orphan))
    }
}
