/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoLauncher
import io.marimo.notebook.launch.MarimoServerHandle
import io.marimo.notebook.launch.NotebookWorkDir
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class NotebookSessionManagerTest : BasePlatformTestCase() {

    class FakeHandle(val authUrl: String) : MarimoServerHandle {
        private val ready = CompletableFuture<String>()
        private var terminationListener: ((Int, String) -> Unit)? = null
        override var isAlive = true
        override val processHandle: ProcessHandler
            get() = throw UnsupportedOperationException()

        override fun awaitReady(): CompletableFuture<String> = ready

        override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
            terminationListener = listener
        }

        fun becomeReady() {
            ready.complete(authUrl)
        }

        fun crash() {
            isAlive = false
            terminationListener?.invoke(1, "crashed")
        }

        override fun dispose() {
            isAlive = false
        }
    }

    class FakeLauncher(override val id: String = "fake") : MarimoLauncher {
        val requests = CopyOnWriteArrayList<LaunchRequest>()
        val handles = CopyOnWriteArrayList<FakeHandle>()
        val secondLaunch = CountDownLatch(1)
        var canLaunch = true
        var launchFailure: Exception? = null

        override fun canLaunch(request: LaunchRequest): Boolean = canLaunch

        override fun launch(request: LaunchRequest): MarimoServerHandle {
            requests.add(request)
            launchFailure?.let { throw it }
            val authUrl =
                request.authenticatedUrl
                    ?: "http://127.0.0.1:${request.port}?access_token=secret${handles.size}"
            val handle = FakeHandle(authUrl)
            handles.add(handle)
            if (requests.size == 2) secondLaunch.countDown()
            return handle
        }

        override fun marimoCliPrefix(request: LaunchRequest): List<String> =
            listOf("fake", "marimo")
    }

    private class ManualTtl(private val honorCancel: Boolean = true) : TtlScheduler {
        val pending = mutableListOf<Pair<Long, Runnable>>()

        override fun schedule(delayMillis: Long, task: Runnable): TtlCancellable {
            val entry = delayMillis to task
            pending.add(entry)
            return TtlCancellable { if (honorCancel) pending.remove(entry) }
        }

        fun fireAll() {
            val due = pending.toList()
            pending.clear()
            due.forEach { it.second.run() }
        }
    }

    private lateinit var sdk: FakeLauncher
    private lateinit var uv: FakeLauncher
    private lateinit var ttl: ManualTtl

    private val manager: NotebookSessionManager
        get() = project.service<NotebookSessionManager>()

    private fun notebook(name: String = "nb.py"): VirtualFile =
        myFixture.addFileToProject(name, "import marimo\n").virtualFile

    override fun setUp() {
        super.setUp()
        sdk = FakeLauncher("fake-sdk")
        uv = FakeLauncher("fake-uv")
        manager.planner = LaunchPlanner(sdk, uv)
        ttl = ManualTtl()
        manager.ttlScheduler = ttl
    }

    // Light platform projects are reused across tests, and the project-level service survives with
    // them. Each test uses its own file name, and teardown drains every session it created.
    override fun tearDown() {
        try {
            manager.sessions().forEach { snapshot ->
                repeat(snapshot.attachedTabs) {
                    com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                        .findFileByUrl(snapshot.fileUrl)
                        ?.let(manager::detach)
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

    fun testLaunchContextRetainsTheTokenAuthModeFromItsLaunch() {
        val settings = SessionSettings.getInstance()
        val before = settings.state.tokenAuthEnabled
        try {
            settings.state.tokenAuthEnabled = true
            val file = notebook("token_auth_snapshot_nb.py")
            manager.urlFor(file)

            settings.state.tokenAuthEnabled = false

            assertTrue(manager.statusFor(file)!!.launch!!.tokenAuthEnabled)
        } finally {
            settings.state.tokenAuthEnabled = before
        }
    }

    fun testSnapshotsNeverCarryTheToken() {
        val file = notebook("token_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        val rendered = manager.statusFor(file).toString()
        assertFalse(
            "snapshots may be logged and rendered anywhere: $rendered",
            rendered.contains("access_token"),
        )
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
        assertFalse(
            "each launch must carry its own token URL",
            sdk.handles[0].authUrl == sdk.handles[1].authUrl,
        )

        manager.stop(a)
        assertFalse("stopping a must kill a's process", sdk.handles[0].isAlive)
        assertTrue("stopping a must not touch b", sdk.handles[1].isAlive)
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(b)!!.state)
    }

    fun testRenameThenRetryUsesOneSessionAndProcess() {
        val file = notebook("rename_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()

        ApplicationManager.getApplication().runWriteAction { file.rename(this, "renamed_nb.py") }

        assertEquals(file.url, manager.sessions().single().fileUrl)

        manager.urlFor(file)

        assertEquals(1, sdk.requests.size)
        assertEquals(1, sdk.handles.size)
        assertEquals(1, manager.sessions().size)
        assertEquals("renamed_nb.py", manager.sessions().single().fileName)
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

        assertTrue(
            "restart must schedule a second process",
            sdk.secondLaunch.await(5, TimeUnit.SECONDS),
        )
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

    fun testStopKillsItsProcess() {
        val file = notebook("dispose_nb.py")
        manager.urlFor(file)
        sdk.handles.single().becomeReady()
        manager.stop(file)
        assertFalse(sdk.handles.single().isAlive)
    }

    fun testTheTokenSettingReachesTheLaunchRequest() {
        val settings = SessionSettings.getInstance()
        val before = settings.state.tokenAuthEnabled
        try {
            settings.state.tokenAuthEnabled = false
            val file = notebook("token_toggle_nb.py")
            manager.urlFor(file)
            assertNull(
                "the escape hatch must omit the password file",
                sdk.requests.single().tokenPasswordFile,
            )
        } finally {
            settings.state.tokenAuthEnabled = before
        }
    }

    fun testPlanFailureDoesNotCreateATokenPasswordFile() {
        sdk.canLaunch = false
        uv.canLaunch = false
        var tokenFileWriterCalled = false
        manager.tokenPasswordFileWriter = {
            tokenFileWriterCalled = true
            File.createTempFile("marimo-token-test-", ".txt")
        }

        val url = manager.urlFor(notebook("no_interpreter_token_nb.py"))

        assertTrue(url.isCompletedExceptionally)
        assertFalse("planning must finish before a password file is created", tokenFileWriterCalled)
    }

    fun testSynchronousLauncherFailureDeletesTheTokenPasswordFile() {
        val tokenFile = File.createTempFile("marimo-token-test-", ".txt")
        manager.tokenPasswordFileWriter = { tokenFile }
        sdk.launchFailure = IOException("launcher failed")
        val file = notebook("sync_launcher_failure_nb.py")

        val url = manager.urlFor(file)

        assertTrue(url.isCompletedExceptionally)
        assertFalse("a pre-handle token file must be cleaned up", tokenFile.exists())
        assertEquals(MarimoSessionState.FAILED, manager.statusFor(file)!!.state)
    }

    fun testTokenPasswordFileWriterFailureCompletesTheLaunchFutureExceptionally() {
        manager.tokenPasswordFileWriter = { throw IOException("cannot write token") }
        val file = notebook("token_write_failure_nb.py")

        val url = manager.urlFor(file)

        assertTrue(url.isCompletedExceptionally)
        assertEquals(MarimoSessionState.FAILED, manager.statusFor(file)!!.state)
    }

    fun testLaunchRunsFromTheContentRootAndRecordsIt() {
        val file = myFixture.addFileToProject("deep/nested/wd_nb.py", "import marimo\n").virtualFile
        manager.urlFor(file)
        val request = sdk.requests.single()
        assertEquals(NotebookWorkDir.resolve(project, file), request.workDir)
        assertEquals(request.workDir, manager.statusFor(file)!!.launch!!.workDir)
        assertFalse(request.workDir!!.endsWith("deep/nested"))
    }

    private fun runningNotebook(name: String): VirtualFile {
        val file = notebook(name)
        manager.urlFor(file)
        sdk.handles.last().becomeReady()
        return file
    }

    fun testAnAttachedTabKeepsTheSessionWithoutAnyTimer() {
        val file = runningNotebook("keep_nb.py")
        manager.attach(file)
        assertTrue("an open tab must never race a timer", ttl.pending.isEmpty())
        assertNull(manager.statusFor(file)!!.expiresAtMillis)
    }

    fun testFinalDetachArmsTheThirtyMinuteTtl() {
        val file = runningNotebook("ttl_nb.py")
        manager.attach(file)
        manager.detach(file)
        assertEquals(1, ttl.pending.size)
        assertEquals(
            SessionSettings.getInstance().backgroundTtlMillis(),
            ttl.pending.single().first,
        )
        assertNotNull(
            "the panel needs a deadline to render",
            manager.statusFor(file)!!.expiresAtMillis,
        )
        assertTrue("the process must stay alive in the background", sdk.handles.single().isAlive)
    }

    fun testReopenBeforeExpiryCancelsTheTtlAndReusesTheProcess() {
        val file = runningNotebook("reopen_nb.py")
        manager.attach(file)
        manager.detach(file)
        manager.attach(file)
        assertTrue(ttl.pending.isEmpty())
        assertNull(manager.statusFor(file)!!.expiresAtMillis)
        manager.urlFor(file)
        assertEquals("reattach must reuse the live process, not relaunch", 1, sdk.handles.size)
    }

    fun testTabMovePreservesTheSessionInEitherEventOrder() {
        val file = runningNotebook("move_nb.py")
        manager.attach(file)
        manager.attach(file)
        manager.detach(file)
        assertTrue("attach-then-detach never reaches zero", ttl.pending.isEmpty())

        manager.detach(file)
        manager.attach(file)
        assertTrue("detach-then-attach cancels the armed timer", ttl.pending.isEmpty())
        assertEquals(1, sdk.handles.size)
        assertTrue(sdk.handles.single().isAlive)
    }

    fun testTtlExpiryStopsDisposesAndRemovesExactlyOnce() {
        val file = runningNotebook("expire_nb.py")
        manager.attach(file)
        manager.detach(file)
        ttl.fireAll()
        assertFalse("expiry must stop the process", sdk.handles.single().isAlive)
        assertNull("expiry must remove the registry entry", manager.statusFor(file))
        ttl.fireAll()
        assertNull(manager.statusFor(file))
    }

    fun testAStaleExpiryTaskCannotKillAReattachedSession() {
        val sticky = ManualTtl(honorCancel = false)
        manager.ttlScheduler = sticky
        val file = runningNotebook("stale_ttl_nb.py")
        manager.attach(file)
        manager.detach(file)
        manager.attach(file)
        sticky.fireAll()
        assertTrue(
            "a cancelled-but-fired task must be ignored by generation",
            sdk.handles.single().isAlive,
        )
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(file)!!.state)
    }

    fun testStopCancelsTheArmedTtl() {
        val file = runningNotebook("stop_ttl_nb.py")
        manager.attach(file)
        manager.detach(file)
        manager.stop(file)
        assertNull(manager.statusFor(file))
        ttl.fireAll()
        assertNull("a fired timer after stop must find nothing", manager.statusFor(file))
    }

    fun testRestartOfABackgroundSessionRearmsTheTtl() {
        val file = runningNotebook("restart_bg_nb.py")
        manager.attach(file)
        manager.detach(file)
        manager.restart(file)
        assertTrue(
            "background restart must schedule a second process",
            sdk.secondLaunch.await(5, TimeUnit.SECONDS),
        )
        assertEquals("the fresh background process needs a fresh deadline", 1, ttl.pending.size)
        assertEquals(2, sdk.handles.size)
    }
}
