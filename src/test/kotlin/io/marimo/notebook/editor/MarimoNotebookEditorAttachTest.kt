/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher

class MarimoNotebookEditorAttachTest : BasePlatformTestCase() {

    // Light projects and their services are reused across tests; drain what this class created.
    override fun tearDown() {
        try {
            val service = project.service<NotebookSessionManager>()
            service.sessions().forEach { service.stopUrl(it.fileUrl) }
        } finally {
            super.tearDown()
        }
    }

    fun testEditorsAttachAndDetachTheSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("attach_nb.py", "import marimo\n").virtualFile

        val first = MarimoNotebookEditor(project, file)
        assertNotNull(service.peek(file))
        assertNull(service.peek(file)!!.expiresAtMillis)

        val second = MarimoNotebookEditor(project, file)
        assertEquals("a split shares one session", 1, service.sessions().size)

        second.dispose()
        assertNull(service.peek(file)!!.expiresAtMillis)

        first.dispose()
        assertNotNull(
            "closing the final tab must arm the background TTL",
            service.peek(file)!!.expiresAtMillis,
        )
    }

    fun testRenamedEditorDetachesTheSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("renamed_attach_nb.py", "import marimo\n").virtualFile
        val editor = MarimoNotebookEditor(project, file)

        ApplicationManager.getApplication().runWriteAction { file.rename(this, "renamed_nb.py") }
        editor.dispose()

        val status = service.statusFor(file)
        assertNotNull("closing a renamed tab must arm the background TTL", status!!.expiresAtMillis)
    }
}
