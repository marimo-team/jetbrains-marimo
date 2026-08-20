/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.process.ProcessHandler
import java.util.concurrent.CompletableFuture

/** A [MarimoServerHandle] whose readiness and termination tests drive explicitly. */
class ScriptedMarimoServerHandle : MarimoServerHandle {
    private val ready = CompletableFuture<String>()
    private var terminationListener: ((Int, String) -> Unit)? = null

    override var isAlive: Boolean = true
    override val processHandle: ProcessHandler get() = throw UnsupportedOperationException()
    override fun awaitReady(): CompletableFuture<String> = ready
    override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
        terminationListener = listener
    }

    fun becomeReady(url: String = "http://127.0.0.1:1") {
        ready.complete(url)
    }

    fun failLaunch(error: Throwable) {
        ready.completeExceptionally(error)
    }

    fun fireTerminated(exitCode: Int = 1, tail: String = "crashed") {
        isAlive = false
        terminationListener?.invoke(exitCode, tail)
    }

    override fun dispose() {
        isAlive = false
    }
}
