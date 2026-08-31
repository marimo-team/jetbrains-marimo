/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.editor.view.NotebookViewRegistry
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.LeaseOwner
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher
import io.marimo.notebook.session.SessionManagerSeams
import java.awt.BorderLayout
import java.awt.Container
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

class MarimoNotebookEditorAttachTest : BasePlatformTestCase() {

    private val editors = mutableListOf<MarimoNotebookEditor>()
    private lateinit var seams: SessionManagerSeams

    // Light projects and their services are reused across tests; drain what this class created.
    override fun setUp() {
        super.setUp()
        seams = SessionManagerSeams(project.service())
    }

    override fun tearDown() {
        try {
            editors.forEach(Disposer::dispose)
            val service = project.service<NotebookSessionManager>()
            service.sessions().forEach { service.stopUrl(it.fileUrl) }
        } finally {
            seams.restore()
            super.tearDown()
        }
    }

    fun testSimultaneousSplitsKeepChildrenInSeparateParents() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("two_parent_nb.py", "import marimo\n").virtualFile

        val first = editor(file)
        val second = editor(file)

        val splitA = JPanel(BorderLayout())
        val splitB = JPanel(BorderLayout())
        splitA.add(first.component, BorderLayout.CENTER)
        assertEquals(1, splitA.componentCount)

        splitB.add(second.component, BorderLayout.CENTER)

        assertEquals(
            "mounting the second editor must not steal the first split's child",
            1,
            splitA.componentCount,
        )
        assertEquals(1, splitB.componentCount)
        assertSame(first.component, splitA.getComponent(0))
        assertSame(second.component, splitB.getComponent(0))
    }

    fun testEditorsAttachAndDetachTheSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("attach_nb.py", "import marimo\n").virtualFile

        val first = editor(file)
        assertNotNull(service.peek(file))
        assertNull(service.peek(file)!!.expiresAtMillis)

        val second = editor(file)
        assertEquals("a split shares one session", 1, service.sessions().size)
        assertNotSame(
            "a second split gets its own browser while the primary is mounted elsewhere",
            first.component,
            second.component,
        )

        second.dispose()
        assertNull(service.peek(file)!!.expiresAtMillis)

        first.dispose()
        assertNotNull(
            "closing the final tab must arm the background TTL",
            service.peek(file)!!.expiresAtMillis,
        )
    }

    fun testSecondaryViewTracksStoppedSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("secondary_stop_nb.py", "import marimo\n").virtualFile
        editor(file)
        val secondary = editor(file)

        service.stop(file)

        assertTrue(
            "the secondary split must replace stale notebook content after Stop",
            labelsIn(secondary.component).any { it.contains("stopped") },
        )
    }

    fun testDeletedNotebookMakesEditorInvalid() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("deleted_nb.py", "import marimo\n").virtualFile
        val editor = editor(file)

        assertTrue(editor.isValid)

        ApplicationManager.getApplication().runWriteAction { file.delete(this) }

        assertFalse("deleted notebook must invalidate its editor", editor.isValid)
    }

    fun testDeletedNotebookFiresValidPropertyChange() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("deleted_prop_nb.py", "import marimo\n").virtualFile
        val editor = editor(file)
        var validAfterDelete: Boolean? = null
        editor.addPropertyChangeListener { event ->
            if (event.propertyName == FileEditor.getPropValid()) {
                validAfterDelete = event.newValue as Boolean
            }
        }

        ApplicationManager.getApplication().runWriteAction { file.delete(this) }

        assertFalse(editor.isValid)
        assertEquals(false, validAfterDelete)
    }

    fun testDeletingContainingDirectoryInvalidatesEditor() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file =
            myFixture.addFileToProject("deleted_parent/notebook.py", "import marimo\n").virtualFile
        val editor = editor(file)

        ApplicationManager.getApplication().runWriteAction { file.parent.delete(this) }

        assertFalse("deleting a notebook's parent must invalidate its editor", editor.isValid)
    }

    fun testRenamedEditorDetachesTheSession() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("renamed_attach_nb.py", "import marimo\n").virtualFile
        val editor = editor(file)

        ApplicationManager.getApplication().runWriteAction { file.rename(this, "renamed_nb.py") }
        editor.dispose()

        val status = service.statusFor(file)
        assertNotNull("closing a renamed tab must arm the background TTL", status!!.expiresAtMillis)
    }

    fun testRegistryDisposesThePrimaryViewWhenItsSessionEnds() {
        val service = project.service<NotebookSessionManager>()
        service.planner = LaunchPlanner(FakeLauncher(), FakeLauncher())
        val file = myFixture.addFileToProject("ended_view_nb.py", "import marimo\n").virtualFile
        val lease = service.acquire(file, LeaseOwner.EDITOR_TAB)
        val view = project.service<NotebookViewRegistry>().primaryViewFor(lease)
        var viewDisposed = false
        Disposer.register(view) { viewDisposed = true }

        lease.close()
        service.stop(file)

        assertTrue("a removed session must dispose its retained view", viewDisposed)
    }

    fun testManagerRestartKeepsTheMountedPrimaryView() {
        val service = project.service<NotebookSessionManager>()
        val sdk = FakeLauncher("fake-sdk")
        service.planner = LaunchPlanner(sdk, FakeLauncher("fake-uv"))
        val file = myFixture.addFileToProject("restart_view_nb.py", "import marimo\n").virtualFile
        val editor = editor(file)
        try {
            val component = editor.component
            assertTrue("initial launch did not begin", sdk.firstLaunch.await(5, TimeUnit.SECONDS))
            sdk.handles.single().becomeReady()

            service.restart(file)

            assertTrue("restart did not begin", sdk.secondLaunch.await(5, TimeUnit.SECONDS))
            assertFalse("restart must stop the first process", sdk.handles.first().isAlive)
            assertSame("restart keeps the mounted primary view", component, editor.component)
        } finally {
            editor.dispose()
        }
    }

    fun testReopeningAStoppedNotebookStartsAFreshProcess() {
        val service = project.service<NotebookSessionManager>()
        val sdk = FakeLauncher("fake-sdk")
        service.planner = LaunchPlanner(sdk, FakeLauncher("fake-uv"))
        val file = myFixture.addFileToProject("reopen_stopped_nb.py", "import marimo\n").virtualFile
        val first = editor(file)
        assertTrue("initial launch did not begin", sdk.firstLaunch.await(5, TimeUnit.SECONDS))
        sdk.handles.single().becomeReady()

        service.stop(file)
        first.dispose()
        editor(file)

        assertTrue(
            "reopening the notebook must start a fresh process",
            sdk.secondLaunch.await(1, TimeUnit.SECONDS),
        )
    }

    private fun editor(file: com.intellij.openapi.vfs.VirtualFile): MarimoNotebookEditor =
        MarimoNotebookEditor(project, file).also(editors::add)

    private fun labelsIn(component: java.awt.Component): List<String> = buildList {
        if (component is JLabel) add(component.text)
        if (component is Container) {
            component.components.forEach { addAll(labelsIn(it)) }
        }
    }
}
