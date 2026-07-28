/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.process.ProcessHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture

class MarimoNotebookLifecycleTest {

    private class ManualWatchdog : (Runnable) -> Unit {
        private val pending = mutableListOf<Runnable>()

        override fun invoke(task: Runnable) {
            pending.add(task)
        }

        fun fireAll() {
            val due = pending.toList()
            pending.clear()
            due.forEach(Runnable::run)
        }
    }

    private class FakeHandle : MarimoServerHandle {
        private val ready = CompletableFuture<String>()
        private var listener: ((Int, String) -> Unit)? = null

        override val processHandle: ProcessHandler
            get() = throw UnsupportedOperationException("The lifecycle does not need a process handler")
        override var isAlive = true

        override fun awaitReady(): CompletableFuture<String> = ready

        override fun onTerminated(listener: (Int, String) -> Unit) {
            this.listener = listener
        }

        fun becomeReady(url: String) {
            ready.complete(url)
        }

        fun terminate(exitCode: Int, outputTail: String) {
            isAlive = false
            listener?.invoke(exitCode, outputTail)
        }

        override fun dispose() {
            isAlive = false
        }
    }

    private fun lifecycle(watchdog: ManualWatchdog = ManualWatchdog()) =
        MarimoNotebookLifecycle(scheduleWatchdog = watchdog)

    @Test fun startsInStarting() {
        assertEquals(MarimoNotebookState.Starting, lifecycle().state)
    }

    @Test fun readyBecomesRunning() {
        val lifecycle = lifecycle()

        lifecycle.onReady("http://127.0.0.1:1234")

        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:1234"), lifecycle.state)
    }

    @Test fun launchFailureBecomesFailed() {
        val lifecycle = lifecycle()
        val boom = RuntimeException("boom")

        lifecycle.onLaunchFailed(boom)

        assertEquals(MarimoNotebookState.Failed(boom), lifecycle.state)
    }

    @Test fun shutdownIntentBecomesStoppingAndKeepsUrl() {
        val lifecycle = lifecycle()
        lifecycle.onReady("http://127.0.0.1:1234")

        lifecycle.onShutdownObserved()

        assertEquals(MarimoNotebookState.Stopping("http://127.0.0.1:1234"), lifecycle.state)
    }

    @Test fun shutdownIntentIsIgnoredWhenNotRunning() {
        val lifecycle = lifecycle()

        lifecycle.onShutdownObserved()

        assertEquals(MarimoNotebookState.Starting, lifecycle.state)
    }

    @Test fun rejectedShutdownRevertsToRunning() {
        val lifecycle = lifecycle()
        lifecycle.onReady("http://127.0.0.1:1234")
        lifecycle.onShutdownObserved()

        lifecycle.onShutdownRejected()

        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:1234"), lifecycle.state)
    }

    @Test fun processExitAfterIntentIsDeliberate() {
        val lifecycle = lifecycle()
        lifecycle.onReady("http://127.0.0.1:1234")
        lifecycle.onShutdownObserved()

        lifecycle.onProcessTerminated(exitCode = 0, outputTail = "")

        assertEquals(MarimoNotebookState.Stopped(StopCause.Deliberate), lifecycle.state)
    }

    @Test fun processExitWithoutIntentIsUnexpected() {
        val lifecycle = lifecycle()
        lifecycle.onReady("http://127.0.0.1:1234")

        lifecycle.onProcessTerminated(exitCode = 137, outputTail = "Killed")

        assertEquals(
            MarimoNotebookState.Stopped(StopCause.Unexpected(137, "Killed")),
            lifecycle.state,
        )
    }

    @Test fun watchdogResolvesStoppingEvenWithProcessAlive() {
        val watchdog = ManualWatchdog()
        val lifecycle = lifecycle(watchdog)
        lifecycle.onReady("http://127.0.0.1:1234")
        lifecycle.onShutdownObserved()

        watchdog.fireAll()

        assertEquals(MarimoNotebookState.Stopped(StopCause.Deliberate), lifecycle.state)
    }

    @Test fun watchdogDoesNothingAfterRevert() {
        val watchdog = ManualWatchdog()
        val lifecycle = lifecycle(watchdog)
        lifecycle.onReady("http://127.0.0.1:1234")
        lifecycle.onShutdownObserved()
        lifecycle.onShutdownRejected()

        watchdog.fireAll()

        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:1234"), lifecycle.state)
    }

    @Test fun releaseSuppressesTheStopNotification() {
        val lifecycle = lifecycle()
        val seen = mutableListOf<MarimoNotebookState>()
        lifecycle.onReady("http://127.0.0.1:1234")
        lifecycle.addListener { seen.add(it) }

        lifecycle.release()
        lifecycle.onProcessTerminated(exitCode = 0, outputTail = "")

        assertTrue("plugin-initiated teardown must not surface an error panel: $seen", seen.isEmpty())
    }

    @Test fun terminalStateIsReportedOnlyOnce() {
        val lifecycle = lifecycle()
        val seen = mutableListOf<MarimoNotebookState>()
        lifecycle.onReady("http://127.0.0.1:1234")
        lifecycle.addListener { seen.add(it) }

        lifecycle.onProcessTerminated(exitCode = 1, outputTail = "first")
        lifecycle.onProcessTerminated(exitCode = 2, outputTail = "second")

        assertEquals(listOf(MarimoNotebookState.Stopped(StopCause.Unexpected(1, "first"))), seen)
    }

    @Test fun listenerReceivesCurrentStateOnSubscribe() {
        val lifecycle = lifecycle()
        lifecycle.onReady("http://127.0.0.1:1234")
        val seen = mutableListOf<MarimoNotebookState>()

        lifecycle.addListener(notifyImmediately = true) { seen.add(it) }

        assertEquals(listOf(MarimoNotebookState.Running("http://127.0.0.1:1234")), seen)
    }

    @Test fun attachForwardsReadinessAndTerminationFromTheHandle() {
        val lifecycle = lifecycle()
        val handle = FakeHandle()

        lifecycle.attach(handle)
        handle.becomeReady("http://127.0.0.1:1234")
        handle.terminate(exitCode = 9, outputTail = "crashed")

        assertEquals(
            MarimoNotebookState.Stopped(StopCause.Unexpected(9, "crashed")),
            lifecycle.state,
        )
        assertNull(lifecycle.liveHandle())
    }

    @Test fun attachedLiveHandleIsAvailableForReuse() {
        val lifecycle = lifecycle()
        val handle = FakeHandle()

        lifecycle.attach(handle)

        assertSame(handle, lifecycle.liveHandle())
    }
}
