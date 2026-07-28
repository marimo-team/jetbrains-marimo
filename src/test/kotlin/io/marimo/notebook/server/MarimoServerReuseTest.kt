/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.MarimoServerHandle
import io.marimo.notebook.launch.StopCause
import java.util.concurrent.CompletableFuture

class MarimoServerReuseTest : BasePlatformTestCase() {

    private class FakeHandle : MarimoServerHandle {
        private val ready = CompletableFuture<String>()
        private var terminationListener: ((Int, String) -> Unit)? = null

        override val processHandle: ProcessHandler
            get() = throw UnsupportedOperationException()
        override var isAlive = true

        override fun awaitReady(): CompletableFuture<String> = ready

        override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
            terminationListener = listener
        }

        fun becomeReady() {
            ready.complete("http://127.0.0.1:1")
        }

        fun terminate() {
            isAlive = false
            terminationListener?.invoke(1, "crashed")
        }

        override fun dispose() {
            isAlive = false
        }
    }

    fun testReopenedNotebookKeepsTheStoppedLifecycle() {
        val file = myFixture.addFileToProject("nb.py", "import marimo\n").virtualFile
        val service = project.service<MarimoServerService>()
        val handle = FakeHandle()

        val initial = service.lifecycleFor(file)
        initial.attach(handle)
        handle.becomeReady()
        handle.terminate()

        val reopened = service.lifecycleFor(file)

        assertSame(initial, reopened)
        assertEquals(MarimoNotebookState.Stopped(StopCause.Unexpected(1, "crashed")), reopened.state)
    }

    fun testReleaseStopsTheLifecycleWithoutClearingSandboxMode() {
        val file = myFixture.addFileToProject("nb.py", "import marimo\n").virtualFile
        val service = project.service<MarimoServerService>()
        val handle = FakeHandle()
        val lifecycle = service.lifecycleFor(file)
        lifecycle.attach(handle)
        service.enableSandbox(file)

        service.release(file)

        assertFalse("release must dispose the lifecycle's handle", handle.isAlive)
        assertSame(lifecycle, service.lifecycleFor(file))
        assertTrue("retrying in sandbox must retain the launch mode", service.isSandbox(file))
    }
}
