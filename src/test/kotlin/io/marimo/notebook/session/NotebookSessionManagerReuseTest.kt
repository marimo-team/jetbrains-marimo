/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoLauncher
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.MarimoServerHandle
import io.marimo.notebook.launch.ScriptedMarimoServerHandle
import io.marimo.notebook.launch.StopCause
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NotebookSessionManagerReuseTest : BasePlatformTestCase() {

    private class TrackingHandle(private val disposed: CountDownLatch) : MarimoServerHandle {
        private val delegate = ScriptedMarimoServerHandle()

        override var isAlive: Boolean
            get() = delegate.isAlive
            set(value) {
                delegate.isAlive = value
            }

        override val processHandle: ProcessHandler
            get() = delegate.processHandle

        override fun awaitReady(): CompletableFuture<String> = delegate.awaitReady()

        override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
            delegate.onTerminated(listener)
        }

        fun becomeReady() = delegate.becomeReady()

        override fun dispose() {
            delegate.dispose()
            disposed.countDown()
        }
    }

    private class BlockingLauncher : MarimoLauncher {
        override val id = "blocking"
        val firstLaunchEntered = CountDownLatch(1)
        val allowFirstLaunch = CountDownLatch(1)
        val firstHandleCreated = CountDownLatch(1)
        val firstHandleDisposed = CountDownLatch(1)
        val launchCount = AtomicInteger()
        val handles = CopyOnWriteArrayList<TrackingHandle>()

        override fun canLaunch(request: LaunchRequest): Boolean = true

        override fun launch(request: LaunchRequest): MarimoServerHandle {
            if (launchCount.incrementAndGet() == 1) {
                firstLaunchEntered.countDown()
                allowFirstLaunch.await(5, TimeUnit.SECONDS)
            }
            return TrackingHandle(firstHandleDisposed).also {
                handles.add(it)
                firstHandleCreated.countDown()
            }
        }

        override fun marimoCliPrefix(request: LaunchRequest): List<String>? = null
    }

    fun testReopenedNotebookKeepsTheStoppedLifecycle() {
        val file = myFixture.addFileToProject("reopened_nb.py", "import marimo\n").virtualFile
        val service = project.service<NotebookSessionManager>()
        val handle = ScriptedMarimoServerHandle()

        val initial = service.lifecycleFor(file)
        initial.attach(handle)
        handle.becomeReady()
        handle.fireTerminated()

        val reopened = service.lifecycleFor(file)

        assertSame(initial, reopened)
        assertEquals(
            MarimoNotebookState.Stopped(StopCause.Unexpected(1, "crashed")),
            reopened.state,
        )
    }

    fun testReleaseStopsTheLifecycleWithoutClearingSandboxMode() {
        val file = myFixture.addFileToProject("release_nb.py", "import marimo\n").virtualFile
        val service = project.service<NotebookSessionManager>()
        val handle = ScriptedMarimoServerHandle()
        val lifecycle = service.lifecycleFor(file)
        lifecycle.attach(handle)
        service.enableSandbox(file)

        service.release(file)

        assertFalse("release must dispose the lifecycle's handle", handle.isAlive)
        assertSame(lifecycle, service.lifecycleFor(file))
        assertTrue("retrying in sandbox must retain the launch mode", service.isSandbox(file))
    }

    fun testConcurrentUrlRequestsLaunchOnlyOnce() {
        val file = myFixture.addFileToProject("concurrent_nb.py", "import marimo\n").virtualFile
        val service = project.service<NotebookSessionManager>()
        val launcher = BlockingLauncher()
        service.planner = LaunchPlanner(launcher, launcher)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<CompletableFuture<String>> { service.urlFor(file) }
            assertTrue(
                "first launch did not begin",
                launcher.firstLaunchEntered.await(5, TimeUnit.SECONDS),
            )

            val second = executor.submit<CompletableFuture<String>> { service.urlFor(file) }
            val firstReadyUrl = first.get(5, TimeUnit.SECONDS)
            val secondReadyUrl = second.get(5, TimeUnit.SECONDS)
            assertSame("callers must share one in-flight future", firstReadyUrl, secondReadyUrl)
            launcher.allowFirstLaunch.countDown()
            assertEquals("two callers must share one launch", 1, launcher.launchCount.get())
        } finally {
            executor.shutdownNow()
        }
    }

    fun testLeaseReadyUrlReturnsBeforeBlockingLaunchCompletes() {
        val file = myFixture.addFileToProject("lease_nb.py", "import marimo\n").virtualFile
        val service = project.service<NotebookSessionManager>()
        val launcher = BlockingLauncher()
        val lease = service.acquire(file, LeaseOwner.EDITOR_TAB)
        service.planner = LaunchPlanner(launcher, launcher)
        val returnedFuture = AtomicReference<CompletableFuture<String>>()
        val readyUrlReturned = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.execute {
                returnedFuture.set(lease.readyUrl())
                readyUrlReturned.countDown()
            }

            assertTrue(
                "launch did not begin",
                launcher.firstLaunchEntered.await(5, TimeUnit.SECONDS),
            )
            assertTrue(
                "readyUrl must return without waiting for launch",
                readyUrlReturned.await(1, TimeUnit.SECONDS),
            )
            assertFalse(returnedFuture.get().isDone)

            launcher.allowFirstLaunch.countDown()
            assertTrue(
                "launch did not create a handle",
                launcher.firstHandleCreated.await(5, TimeUnit.SECONDS),
            )
            assertSame(
                "callers must share the future until readiness",
                returnedFuture.get(),
                lease.readyUrl(),
            )
            launcher.handles.single().becomeReady()

            assertEquals("http://127.0.0.1:1", returnedFuture.get().get(5, TimeUnit.SECONDS))
        } finally {
            launcher.allowFirstLaunch.countDown()
            lease.close()
            executor.shutdownNow()
        }
    }

    fun testStoppedSessionDisposesALateBlockedLaunch() {
        val file = myFixture.addFileToProject("stopped_launch_nb.py", "import marimo\n").virtualFile
        val service = project.service<NotebookSessionManager>()
        val launcher = BlockingLauncher()
        val lease = service.acquire(file, LeaseOwner.EDITOR_TAB)
        service.planner = LaunchPlanner(launcher, launcher)
        try {
            val readyUrl = lease.readyUrl()
            assertTrue(
                "launch did not begin",
                launcher.firstLaunchEntered.await(5, TimeUnit.SECONDS),
            )

            service.stop(file)

            assertTrue("stopped launch must fail", readyUrl.isCompletedExceptionally)
            launcher.allowFirstLaunch.countDown()
            assertTrue(
                "late launch did not create a handle",
                launcher.firstHandleCreated.await(5, TimeUnit.SECONDS),
            )
            assertTrue(
                "late handle was not disposed",
                launcher.firstHandleDisposed.await(5, TimeUnit.SECONDS),
            )
            assertFalse("late handle must be disposed", launcher.handles.single().isAlive)
            assertEquals(
                MarimoNotebookState.Stopped(StopCause.Deliberate),
                service.lifecycleFor(file).state,
            )
        } finally {
            launcher.allowFirstLaunch.countDown()
            lease.close()
        }
    }

    fun testDisposingTheManagerInvalidatesALeaseAndDisposesALateLaunch() {
        val file =
            myFixture.addFileToProject("disposed_manager_nb.py", "import marimo\n").virtualFile
        val service = project.service<NotebookSessionManager>()
        val launcher = BlockingLauncher()
        val lease = service.acquire(file, LeaseOwner.EDITOR_TAB)
        service.planner = LaunchPlanner(launcher, launcher)
        try {
            val readyUrl = lease.readyUrl()
            assertTrue(
                "launch did not begin",
                launcher.firstLaunchEntered.await(5, TimeUnit.SECONDS),
            )

            service.dispose()

            assertTrue(
                "project disposal must fail the lease future",
                readyUrl.isCompletedExceptionally,
            )
            assertTrue(
                "disposed leases cannot create a replacement",
                lease.readyUrl().isCompletedExceptionally,
            )
            launcher.allowFirstLaunch.countDown()
            assertTrue(
                "late launch did not create a handle",
                launcher.firstHandleCreated.await(5, TimeUnit.SECONDS),
            )
            assertTrue(
                "project disposal must dispose a late handle",
                launcher.firstHandleDisposed.await(5, TimeUnit.SECONDS),
            )
            assertFalse("late handle must be disposed", launcher.handles.single().isAlive)
        } finally {
            launcher.allowFirstLaunch.countDown()
            lease.close()
        }
    }
}
