/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** A lifecycle transition identified by its launch generation and resulting state. */
data class LifecycleStateUpdate(val generation: Long, val state: MarimoNotebookState)

internal class LifecycleTransition(
    private val handleToDispose: MarimoServerHandle?,
    private val publishAction: () -> Unit,
) {
    fun publish() {
        handleToDispose?.let { Disposer.dispose(it) }
        publishAction()
    }
}

/**
 * The state of one notebook's marimo server, and the only place allowed to change it.
 *
 * Every [attach] starts a new launch generation. Callbacks registered against a handle carry the
 * generation that registered them, and a callback whose generation is no longer current changes
 * nothing. A slow exit or readiness event from a replaced server therefore cannot repaint or stop
 * its replacement. [release] and [stop] advance the generation for the same reason.
 *
 * [scheduleWatchdog] is injectable so tests resolve timeouts on demand instead of waiting.
 */
class MarimoNotebookLifecycle(
    private val scheduleWatchdog: (Runnable) -> Unit = { task ->
        AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(task, STOPPING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }
) : Disposable {

    @Volatile
    var state: MarimoNotebookState = MarimoNotebookState.Starting
        private set

    private val lock = Any()
    private var generation = 0L
    private var handle: MarimoServerHandle? = null

    private val listeners = CopyOnWriteArrayList<(LifecycleStateUpdate) -> Unit>()

    fun addListener(notifyImmediately: Boolean = false, listener: (LifecycleStateUpdate) -> Unit) {
        val immediate =
            synchronized(lock) {
                listeners.add(listener)
                if (notifyImmediately) LifecycleStateUpdate(generation, state) else null
            }
        immediate?.let(listener)
    }

    /** True when [update] is still the lifecycle's latest transition. */
    fun isCurrent(update: LifecycleStateUpdate): Boolean =
        synchronized(lock) {
            generation == update.generation && state == update.state
        }

    /** The handle to reuse, or null when there is nothing usable and a fresh launch is needed. */
    fun liveHandle(): MarimoServerHandle? =
        synchronized(lock) {
            if (state is MarimoNotebookState.Stopped || state is MarimoNotebookState.Failed)
                return null
            handle?.takeIf { it.isAlive }
        }

    /** Adopt a freshly launched server and follow it from starting through to a terminal state. */
    fun attach(handle: MarimoServerHandle) = prepareAttach(handle).publish()

    internal fun prepareAttach(handle: MarimoServerHandle): LifecycleTransition {
        val update =
            synchronized(lock) {
                this.handle = handle
                state = MarimoNotebookState.Starting
                LifecycleStateUpdate(++generation, state)
            }
        return LifecycleTransition(handleToDispose = null) {
            if (isCurrent(update)) {
                listeners.forEach { it(update) }
                handle.onTerminated { exitCode, tail ->
                    onProcessTerminated(update.generation, exitCode, tail)
                }
                handle.awaitReady().whenComplete { url, error ->
                    if (error != null) onLaunchFailed(update.generation, error)
                    else onReady(update.generation, url)
                }
            }
        }
    }

    /** Marks a launch that failed before any handle existed (planner or spawn failure). */
    fun onLaunchPlanFailed(error: Throwable) = prepareLaunchPlanFailure(error)?.publish()

    internal fun prepareLaunchPlanFailure(error: Throwable): LifecycleTransition? {
        val gen = synchronized(lock) { generation }
        return prepareState(gen) { MarimoNotebookState.Failed(error) }
    }

    /**
     * The page asked marimo to shut down. Optimistic: resolved by process exit or by the watchdog.
     */
    fun onShutdownObserved() {
        val gen = synchronized(lock) { generation }
        val moved =
            setState(gen) {
                (state as? MarimoNotebookState.Running)?.let {
                    MarimoNotebookState.Stopping(it.url)
                }
            }
        if (moved) scheduleWatchdog(Runnable { onStoppingTimedOut(gen) })
    }

    /**
     * marimo refused the shutdown, so the page is still alive. Only an explicit rejection reverts —
     * a request that never gets a response is the expected shape of a successful shutdown.
     */
    fun onShutdownRejected() {
        val gen = synchronized(lock) { generation }
        setState(gen) {
            (state as? MarimoNotebookState.Stopping)?.let { MarimoNotebookState.Running(it.url) }
        }
    }

    /**
     * Kill the server and reset to a new launch state. Callbacks from it become stale by
     * generation.
     */
    fun release() = prepareRelease().publish()

    internal fun prepareRelease(): LifecycleTransition {
        val (handleToDispose, update) =
            synchronized(lock) {
                generation++
                val currentHandle = handle
                handle = null
                state = MarimoNotebookState.Starting
                currentHandle to LifecycleStateUpdate(generation, state)
            }
        return transition(handleToDispose, update)
    }

    /**
     * Kill the server and present it as deliberately stopped. The manual Stop action uses this so
     * the user sees a stopped panel instead of a crash report for the exit this stop causes.
     */
    fun stop() = prepareStop().publish()

    internal fun prepareStop(): LifecycleTransition {
        val (handleToDispose, update) =
            synchronized(lock) {
                ++generation
                val currentHandle = handle
                handle = null
                state = MarimoNotebookState.Stopped(StopCause.Deliberate)
                currentHandle to LifecycleStateUpdate(generation, state)
            }
        return transition(handleToDispose, update)
    }

    private fun onReady(gen: Long, url: String) {
        setState(gen) {
            if (state is MarimoNotebookState.Starting) MarimoNotebookState.Running(url) else null
        }
    }

    private fun onLaunchFailed(gen: Long, error: Throwable) {
        setState(gen) {
            if (state is MarimoNotebookState.Starting) MarimoNotebookState.Failed(error) else null
        }
    }

    private fun onProcessTerminated(gen: Long, exitCode: Int, outputTail: String) {
        setState(gen) {
            when (state) {
                is MarimoNotebookState.Stopping -> MarimoNotebookState.Stopped(StopCause.Deliberate)
                is MarimoNotebookState.Stopped,
                is MarimoNotebookState.Failed -> null
                else -> MarimoNotebookState.Stopped(StopCause.Unexpected(exitCode, outputTail))
            }
        }
    }

    /**
     * Intent was observed, so the page is dead whether or not the process is: marimo may have
     * closed just this session. Restarting from here releases the process first, so claiming
     * stopped while it lingers is safe.
     */
    private fun onStoppingTimedOut(gen: Long) {
        val timedOut =
            synchronized(lock) {
                if (gen != generation || state !is MarimoNotebookState.Stopping) return
                val handleToDispose = handle
                handle = null
                val next = MarimoNotebookState.Stopped(StopCause.Deliberate)
                state = next
                handleToDispose to LifecycleStateUpdate(gen, next)
            }
        val (handleToDispose, update) = timedOut
        handleToDispose?.let { Disposer.dispose(it) }
        listeners.forEach { it(update) }
    }

    /**
     * Applies [next] under the lock when [gen] is still the current generation, then notifies
     * listeners outside the lock. Returns true when a transition happened. A null from [next] means
     * "no transition from the current state".
     */
    private fun setState(gen: Long, next: () -> MarimoNotebookState?): Boolean {
        val transition = prepareState(gen, next) ?: return false
        transition.publish()
        return true
    }

    private fun prepareState(
        gen: Long,
        next: () -> MarimoNotebookState?,
    ): LifecycleTransition? {
        val update =
            synchronized(lock) {
                if (gen != generation) return null
                val value = next() ?: return null
                state = value
                LifecycleStateUpdate(gen, value)
            }
        return transition(handleToDispose = null, update)
    }

    private fun transition(
        handleToDispose: MarimoServerHandle?,
        update: LifecycleStateUpdate,
    ): LifecycleTransition =
        LifecycleTransition(handleToDispose) {
            if (isCurrent(update)) listeners.forEach { it(update) }
        }

    override fun dispose() = release()

    companion object {
        const val STOPPING_TIMEOUT_MS = 5_000L
    }
}
