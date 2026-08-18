/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoLauncher
import io.marimo.notebook.launch.MarimoServerHandle
import java.util.concurrent.CompletableFuture

class MarimoSessionManagerTest : BasePlatformTestCase() {

    class FakeHandle(val authUrl: String) : MarimoServerHandle {
        private val ready = CompletableFuture<String>()
        private var terminationListener: ((Int, String) -> Unit)? = null
        override var isAlive = true
        override val processHandle: ProcessHandler get() = throw UnsupportedOperationException()
        override fun awaitReady(): CompletableFuture<String> = ready
        override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
            terminationListener = listener
        }
        fun becomeReady() { ready.complete(authUrl) }
        fun crash() { isAlive = false; terminationListener?.invoke(1, "crashed") }
        override fun dispose() { isAlive = false }
    }

    class FakeLauncher(override val id: String = "fake") : MarimoLauncher {
        val requests = mutableListOf<LaunchRequest>()
        val handles = mutableListOf<FakeHandle>()
        var canLaunch = true
        override fun canLaunch(request: LaunchRequest): Boolean = canLaunch
        override fun launch(request: LaunchRequest): MarimoServerHandle {
            requests.add(request)
            val handle = FakeHandle("http://127.0.0.1:${request.port}?access_token=secret${handles.size}")
            handles.add(handle)
            return handle
        }
        override fun marimoCliPrefix(request: LaunchRequest): List<String> = listOf("fake", "marimo")
    }

    private lateinit var sdk: FakeLauncher
    private lateinit var uv: FakeLauncher

    private val manager: MarimoServerService get() = project.service<MarimoServerService>()

    private fun notebook(name: String = "nb.py"): VirtualFile =
        myFixture.addFileToProject(name, "import marimo\n").virtualFile

    override fun setUp() {
        super.setUp()
        sdk = FakeLauncher("fake-sdk")
        uv = FakeLauncher("fake-uv")
        manager.planner = LaunchPlanner(sdk, uv)
    }

    // Light platform projects are reused across tests, and the project-level service survives with
    // them. Each test uses its own file name, and teardown drains every session it created.
    override fun tearDown() {
        try {
            manager.sessions().forEach { snapshot ->
                repeat(snapshot.attachedTabs) {
                    com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                        .findFileByUrl(snapshot.fileUrl)?.let(manager::detach)
                }
                manager.stopUrl(snapshot.fileUrl)
            }
        } finally {
            super.tearDown()
        }
    }

    fun testStatusLookupDoesNotCreateASession() {
        val file = notebook("status_nb.py")
        assertNull(manager.statusFor(file))
        assertNull("a status probe must stay side-effect-free", manager.statusFor(file))
    }

    fun testLaunchRecordsTheLaunchContextAndReportsRunning() {
        val file = notebook("launch_nb.py")
        val url = manager.urlFor(file)
        val status = manager.statusFor(file)
        assertNotNull(status)
        assertEquals(MarimoSessionState.STARTING, status!!.state)

        sdk.handles.single().becomeReady()
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(file)!!.state)
        assertEquals(sdk.requests.single().port, manager.statusFor(file)!!.launch!!.port)
        assertEquals("fake-sdk", manager.statusFor(file)!!.launch!!.launcherId)
        assertEquals(sdk.handles.single().authUrl, url.get())
    }

    fun testSnapshotsNeverCarryTheToken() {
        val file = notebook("token_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        val rendered = manager.statusFor(file).toString()
        assertFalse("snapshots may be logged and rendered anywhere: $rendered", rendered.contains("access_token"))
        assertFalse(rendered.contains("secret"))
    }

    fun testPlanFailureMarksTheSessionFailed() {
        sdk.canLaunch = false
        uv.canLaunch = false
        val file = notebook("plan_nb.py")
        val url = manager.urlFor(file)
        assertTrue(url.isCompletedExceptionally)
        assertEquals(MarimoSessionState.FAILED, manager.statusFor(file)!!.state)
    }

    fun testCrashMovesTheSessionToStoppedButKeepsTheEntry() {
        val file = notebook("crash_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        sdk.handles.single().crash()
        assertEquals(MarimoSessionState.STOPPED, manager.statusFor(file)!!.state)
    }

    fun testAttachAndDetachCountTabs() {
        val file = notebook("count_nb.py")
        manager.urlFor(file)
        manager.attach(file)
        manager.attach(file)
        assertEquals(2, manager.statusFor(file)!!.attachedTabs)
        manager.detach(file)
        assertEquals(1, manager.statusFor(file)!!.attachedTabs)
    }

    fun testTwoNotebooksGetIndependentSessions() {
        val a = notebook("iso_a.py")
        val b = notebook("iso_b.py")
        manager.urlFor(a)
        manager.urlFor(b)
        sdk.handles.forEach { it.becomeReady() }
        assertEquals(2, manager.sessions().size)
        assertFalse("each launch must carry its own token URL", sdk.handles[0].authUrl == sdk.handles[1].authUrl)

        manager.stop(a)
        assertFalse("stopping a must kill a's process", sdk.handles[0].isAlive)
        assertTrue("stopping a must not touch b", sdk.handles[1].isAlive)
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(b)!!.state)
    }

    fun testStopWithNoTabsRemovesTheSessionEntry() {
        val file = notebook("stop_bg_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        manager.stop(file)
        assertNull("a stopped background session must leave the registry", manager.statusFor(file))
        assertFalse(sdk.handles.single().isAlive)
    }

    fun testStopWithAnAttachedTabKeepsTheEntryAsStopped() {
        val file = notebook("stop_tab_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        manager.attach(file)
        manager.stop(file)
        val status = manager.statusFor(file)
        assertNotNull("an open tab still needs status to render", status)
        assertEquals(MarimoSessionState.STOPPED, status!!.state)
        assertFalse(sdk.handles.single().isAlive)
    }

    fun testRestartLaunchesAFreshProcess() {
        val file = notebook("restart_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()

        manager.restart(file)

        assertEquals("restart must launch a second process", 2, sdk.handles.size)
        assertFalse("restart must kill the first process", sdk.handles[0].isAlive)
        sdk.handles[1].becomeReady()
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(file)!!.state)
        assertEquals(sdk.requests[1].port, manager.statusFor(file)!!.launch!!.port)
    }

    fun testListenersFireOnSessionEvents() {
        val file = notebook("listen_nb.py")
        var events = 0
        manager.addSessionsListener(testRootDisposable) { events++ }
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        manager.attach(file)
        manager.detach(file)
        assertTrue("expected several change notifications, saw $events", events >= 4)
    }

    fun testDisposingASessionKillsItsProcess() {
        val file = notebook("dispose_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        manager.stop(file)
        assertFalse(sdk.handles.single().isAlive)
    }
}
