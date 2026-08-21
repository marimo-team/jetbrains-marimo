/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MarimoServerReuseTest : BasePlatformTestCase() {

    private class BlockingLauncher : MarimoLauncher {
        override val id = "blocking"
        val firstLaunchEntered = CountDownLatch(1)
        val allowFirstLaunch = CountDownLatch(1)
        val launchCount = AtomicInteger()

        override fun canLaunch(request: LaunchRequest): Boolean = true

        override fun launch(request: LaunchRequest): MarimoServerHandle {
            if (launchCount.incrementAndGet() == 1) {
                firstLaunchEntered.countDown()
                allowFirstLaunch.await(5, TimeUnit.SECONDS)
            }
            return ScriptedMarimoServerHandle()
        }

        override fun marimoCliPrefix(request: LaunchRequest): List<String>? = null
    }

    fun testReopenedNotebookKeepsTheStoppedLifecycle() {
        val file = myFixture.addFileToProject("nb.py", "import marimo\n").virtualFile
        val service = project.service<MarimoServerService>()
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
        val file = myFixture.addFileToProject("nb.py", "import marimo\n").virtualFile
        val service = project.service<MarimoServerService>()
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
        val file = myFixture.addFileToProject("nb.py", "import marimo\n").virtualFile
        val service = project.service<MarimoServerService>()
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
            launcher.allowFirstLaunch.countDown()

            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertEquals("two callers must share one launch", 1, launcher.launchCount.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
