/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.server.MarimoServerService
import io.marimo.notebook.server.MarimoSessionManagerTest.FakeLauncher

class MarimoNotebookEditorAttachTest : BasePlatformTestCase() {

    // Light projects and their services are reused across tests; drain what this class created.
    override fun tearDown() {
        try {
            val service = project.service<MarimoServerService>()
            service.sessions().forEach { snapshot ->
                repeat(snapshot.attachedTabs) {
                    com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                        .findFileByUrl(snapshot.fileUrl)?.let(service::detach)
                }
                service.stopUrl(snapshot.fileUrl)
            }
        } finally {
            super.tearDown()
        }
    }

    fun testEditorsAttachAndDetachTheSession() {
        val service = project.service<MarimoServerService>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("attach_nb.py", "import marimo\n").virtualFile

        val first = MarimoNotebookEditor(project, file)
        assertEquals(1, service.statusFor(file)!!.attachedTabs)

        val second = MarimoNotebookEditor(project, file)
        assertEquals("a split shares the session, it does not relaunch it", 2, service.statusFor(file)!!.attachedTabs)

        second.dispose()
        assertEquals(1, service.statusFor(file)!!.attachedTabs)

        first.dispose()
        assertEquals(0, service.statusFor(file)!!.attachedTabs)
    }
}
