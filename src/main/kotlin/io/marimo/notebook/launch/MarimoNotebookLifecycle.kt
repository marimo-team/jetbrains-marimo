/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class MarimoNotebookLifecycle(
    private val scheduleWatchdog: (Runnable) -> Unit = {task ->
        AppExecutorUtil.getAppScheduledExecutorService().schedule(task, STOPPING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    },
): Disposable {
    @Volatile
    var state: MarimoNotebookState = MarimoNotebookState.Starting
    private set

    @Volatile
    private var handle: MarimoServerHandle? = null

    @Volatile
    private var releasing = false

    private val listeners = CopyOnWriteArrayList<(MarimoNotebookState) -> Unit>()

    fun addListener(
        notifyImmediately: Boolean = false,
        listener: (MarimoNotebookState) -> Unit
    ) {
        listeners.add(listener)
        if (notifyImmediately) {
            listener(state)
        }
    }

    fun liveHandle(): MarimoServerHandle? {
        return handle?.takeIf { it.isAlive }
    }

    /** Adopt a freshly launched server and follow it from starting through to a terminal state. */
    fun attach(handle: MarimoServerHandle) {
        releasing = false
        this.handle = handle
        transition(MarimoNotebookState.Starting)
        handle.onTerminated(::onProcessTerminated)
        handle.awaitReady().whenComplete { url, error ->
            if (error != null) onLaunchFailed(error) else onReady(url)
        }
    }

    fun onReady(url: String) {
        if (state is MarimoNotebookState.Starting) {
            transition(MarimoNotebookState.Running(url))
        }
    }

    fun onLaunchFailed(error: Throwable) {
        if (state is MarimoNotebookState.Starting) {
            transition(MarimoNotebookState.Failed(error))
        }
    }

    fun onShutdownObserved() {
        val running = state as? MarimoNotebookState.Running ?: return

        transition(MarimoNotebookState.Stopping(running.url))
        scheduleWatchdog(Runnable { onStoppingTimedOut() })
    }

    fun onShutdownRejected() {
        val stopping = state as? MarimoNotebookState.Stopping ?: return
        transition(MarimoNotebookState.Running(stopping.url))
    }

    fun onProcessTerminated(exitCode: Int, outputTail: String) {
        if (releasing) return

        val cause = when (state) {
            is MarimoNotebookState.Stopping -> StopCause.Deliberate
            is MarimoNotebookState.Stopped,
            is MarimoNotebookState.Failed -> return
            else -> StopCause.Unexpected(exitCode, outputTail)
        }

        transition(MarimoNotebookState.Stopped(cause))
    }

    private fun onStoppingTimedOut() {
        if (state is MarimoNotebookState.Stopping) {
            transition(MarimoNotebookState.Stopped(StopCause.Deliberate))
        }
    }

    private fun transition(next: MarimoNotebookState) {
        state = next
        listeners.forEach { it(next) }
    }

    override fun dispose() = release()

    fun release() {
        releasing = true
        handle?.let{Disposer.dispose(it)}
        handle = null
    }


    companion object {
        const val STOPPING_TIMEOUT_MS = 5_000L
    }
}
