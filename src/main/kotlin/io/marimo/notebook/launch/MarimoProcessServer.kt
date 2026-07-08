/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun expectedMarimoUrl(host: String, port: Int): String = "http://$host:$port"

/**
 * True when marimo aborted because it does not recognise `--watch`. marimo before 0.10 has no such
 * option, so Click rejects it outright ("No such option: --watch") instead of ignoring it. Used to
 * decide whether a launch is worth retrying without the flag.
 */
internal fun indicatesUnsupportedWatch(output: String): Boolean =
    output.contains("No such option") && output.contains("watch")

/**
 * Spawns a marimo process and completes [MarimoServerHandle.awaitReady] once the server accepts
 * connections. Readiness is driven solely by an HTTP poll: marimo prints its URL banner tens of
 * milliseconds before the socket binds, so completing on the banner would let JCEF navigate into the
 * gap and hit ERR_CONNECTION_REFUSED. Stdout is still collected for process-exit diagnostics. Shared
 * by every process-based launcher (uv, sdk).
 *
 * If [watchFallbackCmd] is supplied and the first attempt exits reporting an unsupported `--watch`
 * option, marimo is relaunched once with that command so interpreters carrying an older marimo still
 * open (losing only external-edit watching).
 */
fun startMarimoServer(
    cmd: GeneralCommandLine,
    host: String,
    port: Int,
    readinessTimeoutSeconds: Long = 30,
    watchFallbackCmd: (() -> GeneralCommandLine)? = null,
): MarimoServerHandle {
    val url = expectedMarimoUrl(host, port)
    val ready = CompletableFuture<String>()
    val handle = ProcessMarimoServerHandle(ready)

    fun runAttempt(command: GeneralCommandLine, fallback: (() -> GeneralCommandLine)?) {
        val handler = OSProcessHandler(command)
        handle.attach(handler)
        val output = StringBuilder()

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                synchronized(output) { output.append(event.text) }
            }

            // A dead process is otherwise indistinguishable from a slow one — without this the tab waits
            // the full poll timeout (e.g. `python -m marimo` exiting on a missing module). Fail fast and
            // surface the process output so the error panel explains why.
            override fun processTerminated(event: ProcessEvent) {
                if (ready.isDone) return
                val full = synchronized(output) { output.toString() }
                if (fallback != null && indicatesUnsupportedWatch(full)) {
                    runAttempt(fallback(), fallback = null)
                    return
                }
                ready.completeExceptionally(
                    IOException("marimo exited (code ${event.exitCode}) before serving $url\n${full.trim().takeLast(500)}"),
                )
            }
        })
        handler.startNotify()
    }

    runAttempt(cmd, watchFallbackCmd)
    pollUntilUp(url, ready, readinessTimeoutSeconds)
    return handle
}

private fun pollUntilUp(url: String, ready: CompletableFuture<String>, timeoutSeconds: Long) {
    Thread {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!ready.isDone && System.nanoTime() < deadline) {
            try {
                HttpRequests.head(url).tryConnect()
                ready.complete(url); return@Thread
            } catch (_: IOException) {
                Thread.sleep(200)
            }
        }
        if (!ready.isDone) ready.completeExceptionally(IOException("marimo server did not start: $url"))
    }.apply { isDaemon = true }.start()
}

private class ProcessMarimoServerHandle(
    private val ready: CompletableFuture<String>,
) : MarimoServerHandle {
    @Volatile private lateinit var handler: OSProcessHandler

    /** Points the handle at the live process; called again when a fallback attempt is spawned. */
    fun attach(handler: OSProcessHandler) {
        this.handler = handler
    }

    override val processHandle: ProcessHandler get() = handler
    override fun awaitReady(): CompletableFuture<String> = ready
    override fun dispose() {
        handler.destroyProcess()
    }
}
