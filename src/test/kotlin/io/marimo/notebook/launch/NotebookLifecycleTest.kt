/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookLifecycleTest {

    private fun lifecycle() = NotebookLifecycle()

    private fun runningLifecycle(
        url: String = "http://127.0.0.1:1234"
    ): Pair<NotebookLifecycle, ScriptedMarimoServerHandle> {
        val l = lifecycle()
        val handle = ScriptedMarimoServerHandle()
        l.attach(handle)
        handle.becomeReady(url)
        return l to handle
    }

    @Test
    fun startsInStarting() {
        assertEquals(MarimoNotebookState.Starting, lifecycle().state)
    }

    @Test
    fun readyBecomesRunning() {
        val (l, _) = runningLifecycle()
        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:1234"), l.state)
    }

    @Test
    fun launchFailureBecomesFailed() {
        val l = lifecycle()
        val handle = ScriptedMarimoServerHandle()
        val boom = RuntimeException("boom")
        l.attach(handle)
        handle.failLaunch(boom)
        assertEquals(MarimoNotebookState.Failed(boom), l.state)
    }

    @Test
    fun planFailureBecomesFailedWithoutAHandle() {
        val l = lifecycle()
        val boom = RuntimeException("no interpreter")
        l.onLaunchPlanFailed(boom)
        assertEquals(MarimoNotebookState.Failed(boom), l.state)
    }

    @Test
    fun cleanProcessExitIsDeliberate() {
        val (l, handle) = runningLifecycle()
        handle.fireTerminated(exitCode = 0, tail = "")
        assertEquals(MarimoNotebookState.Stopped(StopCause.Deliberate), l.state)
    }

    @Test
    fun processExitWithoutCleanCodeIsUnexpected() {
        val (l, handle) = runningLifecycle()
        handle.fireTerminated(exitCode = 137, tail = "Killed")
        assertEquals(MarimoNotebookState.Stopped(StopCause.Unexpected(137, "Killed")), l.state)
    }

    /** The headline regression: a late exit event from launch A must not stop launch B. */
    @Test
    fun lateTerminationFromAReleasedLaunchCannotStopTheReplacement() {
        val l = lifecycle()
        val a = ScriptedMarimoServerHandle()
        l.attach(a)
        a.becomeReady("http://127.0.0.1:1111")

        l.release()
        val b = ScriptedMarimoServerHandle()
        val seen = mutableListOf<MarimoNotebookState>()
        l.attach(b)
        b.becomeReady("http://127.0.0.1:2222")
        l.addListener { seen.add(it.state) }

        a.fireTerminated(exitCode = 143, tail = "terminated")

        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:2222"), l.state)
        assertTrue("stale exit must not notify listeners: $seen", seen.isEmpty())
    }

    @Test
    fun delayedStoppedUpdateFromAIsRejectedAfterBStarts() {
        val l = lifecycle()
        val a = ScriptedMarimoServerHandle()
        l.attach(a)
        a.becomeReady("http://127.0.0.1:1111")

        val rendered = mutableListOf<MarimoNotebookState>()
        var delayedRender: (() -> Unit)? = null
        l.addListener { update ->
            if (update.state is MarimoNotebookState.Stopped) {
                delayedRender = { if (l.isCurrent(update)) rendered.add(update.state) }
            }
        }

        a.fireTerminated(exitCode = 143, tail = "terminated")
        l.release()
        val b = ScriptedMarimoServerHandle()
        l.attach(b)
        b.becomeReady("http://127.0.0.1:2222")

        delayedRender!!.invoke()

        assertTrue(
            "A's delayed stopped panel must not render over B: $rendered",
            rendered.isEmpty(),
        )
        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:2222"), l.state)
    }

    @Test
    fun lateReadinessFromAReleasedLaunchCannotOverrideTheReplacement() {
        val l = lifecycle()
        val a = ScriptedMarimoServerHandle()
        l.attach(a)
        l.release()
        val b = ScriptedMarimoServerHandle()
        l.attach(b)
        b.becomeReady("http://127.0.0.1:2222")

        a.becomeReady("http://127.0.0.1:1111")

        assertEquals(MarimoNotebookState.Running("http://127.0.0.1:2222"), l.state)
    }

    @Test
    fun stopShowsDeliberateAndIgnoresTheExitItCauses() {
        val (l, handle) = runningLifecycle()
        val seen = mutableListOf<MarimoNotebookState>()
        l.addListener { seen.add(it.state) }

        l.stop()
        handle.fireTerminated(exitCode = 137, tail = "Killed")

        assertEquals(MarimoNotebookState.Stopped(StopCause.Deliberate), l.state)
        assertEquals(
            listOf<MarimoNotebookState>(MarimoNotebookState.Stopped(StopCause.Deliberate)),
            seen,
        )
    }

    @Test
    fun releaseResetsToStartingWithoutAStoppedNotification() {
        val (l, handle) = runningLifecycle()
        val seen = mutableListOf<MarimoNotebookState>()
        l.addListener { seen.add(it.state) }
        l.release()
        handle.fireTerminated(exitCode = 0, tail = "")
        assertEquals(listOf(MarimoNotebookState.Starting), seen)
        assertEquals(MarimoNotebookState.Starting, l.state)
    }

    @Test
    fun releaseDisposesTheHandle() {
        val (l, handle) = runningLifecycle()
        l.release()
        assertTrue("release must kill the process", !handle.isAlive)
        assertEquals("no live handle may remain", null, l.liveHandle())
    }

    @Test
    fun terminalStateIsReportedOnlyOnce() {
        val (l, handle) = runningLifecycle()
        val seen = mutableListOf<MarimoNotebookState>()
        l.addListener { seen.add(it.state) }
        handle.fireTerminated(exitCode = 1, tail = "first")
        handle.fireTerminated(exitCode = 2, tail = "second")
        assertEquals(
            listOf<MarimoNotebookState>(
                MarimoNotebookState.Stopped(StopCause.Unexpected(1, "first"))
            ),
            seen,
        )
    }

    @Test
    fun listenerReceivesCurrentStateOnSubscribe() {
        val (l, _) = runningLifecycle()
        val seen = mutableListOf<MarimoNotebookState>()
        l.addListener(notifyImmediately = true) { seen.add(it.state) }
        assertEquals(
            listOf<MarimoNotebookState>(MarimoNotebookState.Running("http://127.0.0.1:1234")),
            seen,
        )
    }
}
