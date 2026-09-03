/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchEnvContribution
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoLauncher
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.MarimoServerHandle
import io.marimo.notebook.launch.NotebookWorkDir
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
        val readinessObserved = CountDownLatch(1)
        override var isAlive = true
        override val processHandle: ProcessHandler
            get() = throw UnsupportedOperationException()

        override fun awaitReady(): CompletableFuture<String> {
            readinessObserved.countDown()
            return ready
        }

        override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
            terminationListener = listener
        }

        fun becomeReady() {
            check(readinessObserved.await(5, TimeUnit.SECONDS)) {
                "launch did not register readiness"
            }
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

    class FakeLauncher(
        override val id: String = "fake",
        var cliPrefix: List<String>? = listOf(id, "marimo"),
    ) : MarimoLauncher {
        val requests = CopyOnWriteArrayList<LaunchRequest>()
        val handles = CopyOnWriteArrayList<FakeHandle>()
        val firstLaunch = CountDownLatch(1)
        val secondLaunchEntered = CountDownLatch(1)
        val secondLaunch = CountDownLatch(1)
        var canLaunch = true
        var launchFailure: Exception? = null
        var secondLaunchGate: CountDownLatch? = null

        override fun canLaunch(request: LaunchRequest): Boolean = canLaunch

        override fun launch(request: LaunchRequest): MarimoServerHandle {
            requests.add(request)
            launchFailure?.let { throw it }
            if (requests.size == 2) {
                secondLaunchEntered.countDown()
                secondLaunchGate?.await(5, TimeUnit.SECONDS)
            }
            val authUrl =
                request.authenticatedUrl
                    ?: "http://127.0.0.1:${request.port}?access_token=secret${handles.size}"
            val handle = FakeHandle(authUrl)
            handles.add(handle)
            firstLaunch.countDown()
            if (requests.size == 2) secondLaunch.countDown()
            return handle
        }

        override fun marimoCliPrefix(request: LaunchRequest): List<String>? = cliPrefix
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
    private lateinit var seams: SessionManagerSeams
    private val leases = mutableListOf<NotebookSessionLease>()

    private val manager: NotebookSessionManager
        get() = project.service<NotebookSessionManager>()

    private fun notebook(name: String = "nb.py"): VirtualFile =
        myFixture.addFileToProject(name, "import marimo\n").virtualFile

    private fun launch(file: VirtualFile): CompletableFuture<String> =
        manager.urlFor(file).also {
            assertTrue("launch did not begin", sdk.firstLaunch.await(5, TimeUnit.SECONDS))
        }

    private fun editorLease(file: VirtualFile): NotebookSessionLease =
        manager.acquire(file, LeaseOwner.EDITOR_TAB).also(leases::add)

    private fun awaitFailure(future: CompletableFuture<String>) {
        runCatching { future.get(5, TimeUnit.SECONDS) }
        assertTrue("launch must fail", future.isCompletedExceptionally)
    }

    override fun setUp() {
        super.setUp()
        seams = SessionManagerSeams(manager)
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
            leases.forEach(NotebookSessionLease::close)
            manager.sessions().forEach { manager.stopUrl(it.fileUrl) }
        } finally {
            seams.restore()
            super.tearDown()
        }
    }

    fun testStatusLookupDoesNotCreateASession() {
        val file = notebook("status_nb.py")
        assertNull(manager.statusFor(file))
        assertNull("a status probe must stay side-effect-free", manager.statusFor(file))
    }

    fun testLeaseReadyUrlFailsAfterNotebookDeletion() {
        val file = notebook("deleted_lease_nb.py")
        val lease = editorLease(file)

        ApplicationManager.getApplication().runWriteAction { file.delete(this) }

        assertTrue(lease.readyUrl().isCompletedExceptionally)
    }

    fun testLaunchRecordsTheLaunchContextAndReportsRunning() {
        val file = notebook("launch_nb.py")
        val url = launch(file)
        val status = manager.statusFor(file)
        assertNotNull(status)
        assertEquals(MarimoSessionState.STARTING, status!!.state)

        sdk.handles.single().becomeReady()
        url.get(5, TimeUnit.SECONDS)
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(file)!!.state)
        assertEquals(sdk.requests.single().port, manager.statusFor(file)!!.launch!!.port)
        assertEquals("fake-sdk", manager.statusFor(file)!!.launch!!.launcherId)
        assertEquals(sdk.handles.single().authUrl, url.get())
    }

    fun testLaunchEnvContributionReachesTheRequestAndTheLaunchContext() {
        manager.launchEnvCollector = {
            LaunchEnvContribution(
                env = mapOf("PGHOST" to "db.internal"),
                labels = listOf("Orders DB (postgresql, primary)"),
            )
        }
        val file = notebook("env_nb.py")
        launch(file)
        assertEquals("db.internal", sdk.requests.single().extraEnv["PGHOST"])
        assertEquals(
            listOf("Orders DB (postgresql, primary)"),
            manager.statusFor(file)!!.launch!!.launchEnvLabels,
        )
    }

    fun testSnapshotsNeverRenderEnvValues() {
        manager.launchEnvCollector = {
            LaunchEnvContribution(mapOf("PGPASSWORD" to "supersecret"), listOf("Orders DB"))
        }
        val file = notebook("env_secret_nb.py")
        launch(file)
        sdk.handles.single().becomeReady()
        val rendered = manager.statusFor(file).toString()
        assertFalse("snapshots may be logged anywhere: $rendered", rendered.contains("supersecret"))
    }

    fun testLaunchContextRetainsTheTokenAuthModeFromItsLaunch() {
        val settings = SessionSettings.getInstance()
        val before = settings.state.tokenAuthEnabled
        try {
            settings.state.tokenAuthEnabled = true
            val file = notebook("token_auth_snapshot_nb.py")
            launch(file)

            settings.state.tokenAuthEnabled = false

            assertTrue(manager.statusFor(file)!!.launch!!.tokenAuthEnabled)
        } finally {
            settings.state.tokenAuthEnabled = before
        }
    }

    fun testSnapshotsNeverCarryTheToken() {
        val file = notebook("token_nb.py")
        launch(file)
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
        awaitFailure(url)
        assertEquals(MarimoSessionState.FAILED, manager.statusFor(file)!!.state)
    }

    fun testCrashMovesTheSessionToStoppedButKeepsTheEntry() {
        val file = notebook("crash_nb.py")
        val url = launch(file)
        sdk.handles.single().becomeReady()
        url.get(5, TimeUnit.SECONDS)
        sdk.handles.single().crash()
        assertEquals(MarimoSessionState.STOPPED, manager.statusFor(file)!!.state)
    }

    fun testEditorLeasesArmTtlOnlyAfterTheFinalClose() {
        val file = notebook("count_nb.py")
        launch(file)
        val first = editorLease(file)
        val second = editorLease(file)

        first.close()
        assertTrue("one remaining editor must suppress the TTL", ttl.pending.isEmpty())

        second.close()
        assertEquals(1, ttl.pending.size)
    }

    fun testTwoNotebooksGetIndependentSessions() {
        val a = notebook("iso_a.py")
        val b = notebook("iso_b.py")
        launch(a)
        manager.urlFor(b)
        assertTrue("second launch did not begin", sdk.secondLaunch.await(5, TimeUnit.SECONDS))
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

    fun testStopWithNoTabsRemovesTheSessionEntry() {
        val file = notebook("stop_bg_nb.py")
        launch(file)
        sdk.handles.single().becomeReady()
        val sessionId = manager.leaseIfPresent(file)!!.sessionId
        val events = mutableListOf<NotebookSessionEvent>()
        manager.addSessionEventListener(testRootDisposable, events::add)

        manager.stop(file)

        assertEquals(listOf(NotebookSessionEvent.Ended(sessionId)), events)
        assertNull("a stopped background session must leave the registry", manager.statusFor(file))
        assertFalse(sdk.handles.single().isAlive)
    }

    fun testStopWithAnActiveLeaseKeepsTheEntryAsStopped() {
        val file = notebook("stop_tab_nb.py")
        launch(file)
        sdk.handles.single().becomeReady()
        val editor = editorLease(file)
        editor.stop()
        val status = manager.statusFor(file)
        assertNotNull("an active lease still needs status to render", status)
        assertEquals(MarimoSessionState.STOPPED, status!!.state)
        assertFalse(sdk.handles.single().isAlive)
    }

    fun testRestartLaunchesAFreshProcess() {
        val file = notebook("restart_nb.py")
        val firstReadyUrl = launch(file)
        sdk.handles.single().becomeReady()
        firstReadyUrl.get(5, TimeUnit.SECONDS)
        val sessionId = manager.leaseIfPresent(file)!!.sessionId
        val events = mutableListOf<NotebookSessionEvent>()
        manager.addSessionEventListener(testRootDisposable, events::add)

        manager.restart(file)

        assertTrue(
            "restart must schedule a second process",
            sdk.secondLaunch.await(5, TimeUnit.SECONDS),
        )
        assertEquals("restart must launch a second process", 2, sdk.handles.size)
        assertFalse("restart must kill the first process", sdk.handles[0].isAlive)
        assertTrue(
            "restart must register readiness",
            sdk.handles[1].readinessObserved.await(5, TimeUnit.SECONDS),
        )
        val restartedUrl = manager.urlFor(file)
        sdk.handles[1].becomeReady()
        restartedUrl.get(5, TimeUnit.SECONDS)
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(file)!!.state)
        assertEquals(sdk.requests[1].port, manager.statusFor(file)!!.launch!!.port)
        assertEquals(
            listOf(NotebookSessionEvent.Restarted(sessionId)),
            events,
        )
    }

    fun testRestartRecollectsTheLaunchEnvironment() {
        var calls = 0
        manager.launchEnvCollector = {
            calls++
            LaunchEnvContribution(emptyMap())
        }
        val file = runningNotebook("env_recollect_nb.py")
        manager.restart(file)
        assertTrue(sdk.secondLaunch.await(5, TimeUnit.SECONDS))
        assertEquals("every restart must recompute the environment", 2, calls)
    }

    fun testMarkLaunchEnvStaleFlagsOnlyTheTargetNotebook() {
        val target = runningNotebook("stale_target_nb.py")
        val other = runningNotebook("fresh_other_nb.py")

        manager.markLaunchEnvStale(target)

        assertTrue(manager.statusFor(target)!!.launchEnvStale)
        assertFalse(manager.statusFor(other)!!.launchEnvStale)
    }

    fun testRestartClearsLaunchEnvironmentStaleness() {
        val file = runningNotebook("stale_restart_nb.py")
        manager.markLaunchEnvStale(file)

        manager.restart(file)

        assertTrue(sdk.secondLaunch.await(5, TimeUnit.SECONDS))
        sdk.handles[1].becomeReady()
        manager.urlFor(file).get(5, TimeUnit.SECONDS)
        assertFalse(manager.statusFor(file)!!.launchEnvStale)
    }

    fun testEnvironmentChangeDuringRestartRemainsStale() {
        val file = runningNotebook("stale_during_restart_nb.py")
        manager.markLaunchEnvStale(file)
        val launchGate = CountDownLatch(1)
        sdk.secondLaunchGate = launchGate

        manager.restart(file)
        assertTrue(sdk.secondLaunchEntered.await(5, TimeUnit.SECONDS))
        manager.markLaunchEnvStale(file)
        launchGate.countDown()

        assertTrue(sdk.secondLaunch.await(5, TimeUnit.SECONDS))
        sdk.handles[1].becomeReady()
        manager.urlFor(file).get(5, TimeUnit.SECONDS)
        assertTrue(manager.statusFor(file)!!.launchEnvStale)
    }

    fun testReleaseClearsLaunchContext() {
        val file = notebook("release_context_nb.py")
        val readyUrl = launch(file)
        sdk.handles.single().becomeReady()
        readyUrl.get(5, TimeUnit.SECONDS)
        val lease = manager.leaseIfPresent(file)!!

        manager.release(file)

        assertNull(manager.statusFor(file)!!.launch)
        assertNull(lease.launcherInfo())
    }

    fun testRestartClearsLaunchContextBeforeTheFreshLaunchAttaches() {
        val file = notebook("restart_context_nb.py")
        val readyUrl = launch(file)
        sdk.handles.single().becomeReady()
        readyUrl.get(5, TimeUnit.SECONDS)
        val lease = manager.leaseIfPresent(file)!!
        val gate = CountDownLatch(1)
        sdk.secondLaunchGate = gate

        try {
            manager.restart(file)

            assertTrue(
                "fresh launch did not begin",
                sdk.secondLaunchEntered.await(5, TimeUnit.SECONDS),
            )
            assertNull(manager.statusFor(file)!!.launch)
            assertNull(lease.launcherInfo())
        } finally {
            gate.countDown()
        }

        assertTrue("fresh launch did not finish", sdk.secondLaunch.await(5, TimeUnit.SECONDS))
        assertTrue(
            "fresh launch must register readiness",
            sdk.handles[1].readinessObserved.await(5, TimeUnit.SECONDS),
        )
        sdk.handles[1].becomeReady()
        assertNotNull(manager.statusFor(file)!!.launch)
    }

    fun testStopPublishesLifecycleChangesAfterReleasingTheSessionLock() {
        val file = notebook("stop_listener_lock_nb.py")
        val readyUrl = launch(file)
        sdk.handles.single().becomeReady()
        readyUrl.get(5, TimeUnit.SECONDS)
        val lease = editorLease(file)
        val executor = Executors.newSingleThreadExecutor()
        try {
            manager.lifecycleFor(file).addListener { update ->
                if (update.state is MarimoNotebookState.Stopped) {
                    val acquired =
                        executor.submit<Boolean> {
                            manager.acquire(file, LeaseOwner.PAIR_PROMPT).close()
                            true
                        }
                    assertTrue(
                        "lifecycle listener must not hold the session lock",
                        acquired.get(1, TimeUnit.SECONDS),
                    )
                }
            }

            lease.stop()
        } finally {
            executor.shutdownNow()
        }
    }

    fun testReleasePublishesLifecycleChangesAfterReleasingTheSessionLock() {
        val file = notebook("release_listener_lock_nb.py")
        val readyUrl = launch(file)
        sdk.handles.single().becomeReady()
        readyUrl.get(5, TimeUnit.SECONDS)
        val executor = Executors.newSingleThreadExecutor()
        var observeRelease = true
        try {
            manager.lifecycleFor(file).addListener { update ->
                if (observeRelease && update.state is MarimoNotebookState.Starting) {
                    val acquired =
                        executor.submit<Boolean> {
                            manager.acquire(file, LeaseOwner.PAIR_PROMPT).close()
                            true
                        }
                    assertTrue(
                        "lifecycle listener must not hold the session lock",
                        acquired.get(1, TimeUnit.SECONDS),
                    )
                }
            }

            manager.release(file)
        } finally {
            observeRelease = false
            executor.shutdownNow()
        }
    }

    fun testListenersFireOnSessionEvents() {
        val file = notebook("listen_nb.py")
        var events = 0
        manager.addSessionsListener(testRootDisposable) { events++ }
        launch(file)
        sdk.handles.single().becomeReady()
        editorLease(file).close()
        assertTrue("expected several change notifications, saw $events", events >= 4)
    }

    fun testStopKillsItsProcess() {
        val file = notebook("dispose_nb.py")
        launch(file)
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
            launch(file)
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

        awaitFailure(url)
        assertFalse("planning must finish before a password file is created", tokenFileWriterCalled)
    }

    fun testSynchronousLauncherFailureDeletesTheTokenPasswordFile() {
        val tokenFile = File.createTempFile("marimo-token-test-", ".txt")
        manager.tokenPasswordFileWriter = { tokenFile }
        sdk.launchFailure = IOException("launcher failed")
        val file = notebook("sync_launcher_failure_nb.py")

        val url = manager.urlFor(file)

        awaitFailure(url)
        assertFalse("a pre-handle token file must be cleaned up", tokenFile.exists())
        assertEquals(MarimoSessionState.FAILED, manager.statusFor(file)!!.state)
    }

    fun testTokenPasswordFileWriterFailureCompletesTheLaunchFutureExceptionally() {
        manager.tokenPasswordFileWriter = { throw IOException("cannot write token") }
        val file = notebook("token_write_failure_nb.py")

        val url = manager.urlFor(file)

        awaitFailure(url)
        assertEquals(MarimoSessionState.FAILED, manager.statusFor(file)!!.state)
    }

    fun testLaunchRunsFromTheContentRootAndRecordsIt() {
        val file = myFixture.addFileToProject("deep/nested/wd_nb.py", "import marimo\n").virtualFile
        launch(file)
        val request = sdk.requests.single()
        assertEquals(NotebookWorkDir.resolve(project, file), request.workDir)
        assertEquals(request.workDir, manager.statusFor(file)!!.launch!!.workDir)
        assertFalse(request.workDir!!.endsWith("deep/nested"))
    }

    private fun runningNotebook(name: String): VirtualFile {
        val file = notebook(name)
        launch(file)
        sdk.handles.last().becomeReady()
        return file
    }

    fun testAnEditorLeaseKeepsTheSessionWithoutAnyTimer() {
        val file = runningNotebook("keep_nb.py")
        editorLease(file)
        assertTrue("an open tab must never race a timer", ttl.pending.isEmpty())
        assertNull(manager.statusFor(file)!!.expiresAtMillis)
    }

    fun testFinalLeaseCloseArmsTheThirtyMinuteTtl() {
        val file = runningNotebook("ttl_nb.py")
        editorLease(file).close()
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
        editorLease(file).close()
        val reopened = editorLease(file)
        assertTrue(ttl.pending.isEmpty())
        assertNull(manager.statusFor(file)!!.expiresAtMillis)
        reopened.readyUrl().get(5, TimeUnit.SECONDS)
        assertEquals("reattach must reuse the live process, not relaunch", 1, sdk.handles.size)
    }

    fun testTabMovePreservesTheSessionInEitherEventOrder() {
        val file = runningNotebook("move_nb.py")
        val first = editorLease(file)
        val second = editorLease(file)
        first.close()
        assertTrue("attach-then-detach never reaches zero", ttl.pending.isEmpty())

        second.close()
        editorLease(file)
        assertTrue("detach-then-attach cancels the armed timer", ttl.pending.isEmpty())
        assertEquals(1, sdk.handles.size)
        assertTrue(sdk.handles.single().isAlive)
    }

    fun testTtlExpiryStopsDisposesAndRemovesExactlyOnce() {
        val file = runningNotebook("expire_nb.py")
        val lease = editorLease(file)
        val events = mutableListOf<NotebookSessionEvent>()
        manager.addSessionEventListener(testRootDisposable, events::add)
        lease.close()
        ttl.fireAll()
        assertFalse("expiry must stop the process", sdk.handles.single().isAlive)
        assertNull("expiry must remove the registry entry", manager.statusFor(file))
        assertEquals(listOf(NotebookSessionEvent.Ended(lease.sessionId)), events)
        ttl.fireAll()
        assertNull(manager.statusFor(file))
        assertEquals("session removal must be single-shot", 1, events.size)
    }

    fun testAStaleExpiryTaskCannotKillAReattachedSession() {
        val sticky = ManualTtl(honorCancel = false)
        manager.ttlScheduler = sticky
        val file = runningNotebook("stale_ttl_nb.py")
        editorLease(file).close()
        editorLease(file)
        sticky.fireAll()
        assertTrue(
            "a cancelled-but-fired task must be ignored by generation",
            sdk.handles.single().isAlive,
        )
        assertEquals(MarimoSessionState.RUNNING, manager.statusFor(file)!!.state)
    }

    fun testStopCancelsTheArmedTtl() {
        val file = runningNotebook("stop_ttl_nb.py")
        editorLease(file).close()
        manager.acquire(file, LeaseOwner.PAIR_PROMPT).use { it.stop() }
        assertNull(manager.statusFor(file))
        ttl.fireAll()
        assertNull("a fired timer after stop must find nothing", manager.statusFor(file))
    }

    fun testRestartOfABackgroundSessionRearmsTheTtl() {
        val file = runningNotebook("restart_bg_nb.py")
        editorLease(file).close()
        manager.acquire(file, LeaseOwner.PAIR_PROMPT).use { it.restart() }
        assertTrue(
            "background restart must schedule a second process",
            sdk.secondLaunch.await(5, TimeUnit.SECONDS),
        )
        assertEquals("the fresh background process needs a fresh deadline", 1, ttl.pending.size)
        assertEquals(2, sdk.handles.size)
    }
}
