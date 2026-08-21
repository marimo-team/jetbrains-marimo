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
            service.sessions().forEach { snapshot ->
                repeat(snapshot.attachedTabs) {
                    com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                        .findFileByUrl(snapshot.fileUrl)
                        ?.let(service::detach)
                }
                service.stopUrl(snapshot.fileUrl)
            }
        } finally {
            super.tearDown()
        }
    }

    fun testEditorsAttachAndDetachTheSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("attach_nb.py", "import marimo\n").virtualFile

        val first = MarimoNotebookEditor(project, file)
        assertEquals(1, service.statusFor(file)!!.attachedTabs)

        val second = MarimoNotebookEditor(project, file)
        assertEquals(
            "a split shares the session, it does not relaunch it",
            2,
            service.statusFor(file)!!.attachedTabs,
        )

        second.dispose()
        assertEquals(1, service.statusFor(file)!!.attachedTabs)

        first.dispose()
        assertEquals(0, service.statusFor(file)!!.attachedTabs)
    }

    fun testRenamedEditorDetachesTheSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("renamed_attach_nb.py", "import marimo\n").virtualFile
        val editor = MarimoNotebookEditor(project, file)

        ApplicationManager.getApplication().runWriteAction { file.rename(this, "renamed_nb.py") }
        editor.dispose()

        val status = service.statusFor(file)
        assertEquals(0, status!!.attachedTabs)
        assertNotNull("closing a renamed tab must arm the background TTL", status.expiresAtMillis)
    }
}
