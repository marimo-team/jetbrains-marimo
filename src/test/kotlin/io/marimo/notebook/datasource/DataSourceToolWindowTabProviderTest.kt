/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DataSourceToolWindowTabProviderTest : BasePlatformTestCase() {
    fun testProviderCreatesTheDataSourcesTab() {
        val provider = DataSourceToolWindowTabProvider()

        assertEquals("data-sources", provider.id)
        assertEquals("Data Sources", provider.title)
        val component = provider.createComponent(project) { null }
        assertTrue(component is DataSourceExposurePanel)
        Disposer.dispose(component as DataSourceExposurePanel)
    }

    fun testConfigureSelectsTheNotebookNamedByTheConsentPrompt() {
        val prompted = projectFile("notebooks/prompted.py")
        val other = projectFile("notebooks/other.py")
        FileEditorManager.getInstance(project).openFile(other, true)

        DataSourceToolWindowTabProvider.show(project, "notebooks/prompted.py")
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals(prompted, FileEditorManager.getInstance(project).selectedFiles.single())
    }

    private fun projectFile(relativePath: String) =
        Path.of(requireNotNull(project.basePath))
            .resolve(relativePath)
            .also {
                Files.createDirectories(it.parent)
                Files.writeString(it, "import marimo\n")
            }
            .let { requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it)) }
}
