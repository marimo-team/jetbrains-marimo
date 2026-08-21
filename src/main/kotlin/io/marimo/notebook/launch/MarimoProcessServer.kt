/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.util.io.HttpRequests
import io.marimo.notebook.MarimoLocalhost
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * True when marimo aborted because it does not recognise `--watch`. marimo before 0.10 has no such
 * option, so Click rejects it outright ("No such option: --watch") instead of ignoring it. Used to
 * decide whether a launch is worth retrying without the flag.
 */
internal fun indicatesUnsupportedWatch(output: String): Boolean =
    output.contains("No such option") && output.contains("watch")

/** Redacts complete process output before it is retained in a user-visible diagnostic. */
internal fun diagnosticOutputTail(chunks: Iterable<String>): String =
    redactAccessTokens(chunks.joinToString(separator = "")).trim().takeLast(500)

/**
 * Spawns a marimo process and completes [MarimoServerHandle.awaitReady] once BOTH startup signals
 * arrive: the socket answers HTTP (any status), and the URL JCEF must load is known. When
 * [authenticatedUrl] is non-null the plugin supplied it (token auth on); when null readiness
 * delivers the plain server origin. Retained stdout is redacted before it is used for diagnostics.
 * Banner parsing is not used for readiness.
 *
 * If [watchFallbackCmd] is supplied and the first attempt exits reporting an unsupported `--watch`
 * option, marimo is relaunched once with that command. The fallback attempt completes the same
 * futures and reuses the same [authenticatedUrl].
 */
fun startMarimoServer(
    cmd: GeneralCommandLine,
    host: String,
    port: Int,
    readinessTimeoutSeconds: Long = 30,
    watchFallbackCmd: (() -> GeneralCommandLine)? = null,
    authenticatedUrl: String? = null,
    tokenPasswordFile: String? = null,
): MarimoServerHandle {
    val expectedUrl = MarimoLocalhost.origin(host, port)
    val urlFuture = CompletableFuture<String>()
    if (authenticatedUrl != null) {
        urlFuture.complete(authenticatedUrl)
    } else {
        urlFuture.complete(expectedUrl)
    }
    val httpUp = CompletableFuture<Void?>()
    val ready = urlFuture.thenCombine(httpUp) { url, _ -> url }
    val handle = ProcessMarimoServerHandle(ready, tokenPasswordFile?.let(::File))

    fun runAttempt(command: GeneralCommandLine, fallback: (() -> GeneralCommandLine)?) {
        val handler = OSProcessHandler(command)
        handle.attach(handler)
        val output = StringBuilder()

        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    synchronized(output) { output.append(event.text) }
                }

                // A dead process is otherwise indistinguishable from a slow one — without this the
                // tab waits
                // the full poll timeout (e.g. `python -m marimo` exiting on a missing module). Fail
                // fast and
                // surface the process output so the error panel explains why.
                override fun processTerminated(event: ProcessEvent) {
                    val full = synchronized(output) { output.toString() }
                    val diagnosticTail = diagnosticOutputTail(listOf(full))

                    if (ready.isDone) {
                        handle.notifyTerminated(event.exitCode, diagnosticTail)
                        return
                    }

                    if (fallback != null && indicatesUnsupportedWatch(full)) {
                        runAttempt(fallback(), fallback = null)
                        return
                    }
                    httpUp.completeExceptionally(
                        IOException(
                            "marimo exited (code ${event.exitCode}) before serving $expectedUrl\n$diagnosticTail"
                        )
                    )
                    handle.notifyTerminated(event.exitCode, diagnosticTail)
                }
            }
        )
        handler.startNotify()
    }

    try {
        runAttempt(cmd, watchFallbackCmd)
    } catch (e: Exception) {
        handle.dispose()
        throw e
    }
    pollUntilUp(expectedUrl, httpUp, readinessTimeoutSeconds)
    return handle
}

private fun pollUntilUp(url: String, httpUp: CompletableFuture<Void?>, timeoutSeconds: Long) {
    Thread {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!httpUp.isDone && System.nanoTime() < deadline) {
            try {
                HttpRequests.head(url).tryConnect()
                httpUp.complete(null)
                return@Thread
            } catch (_: HttpRequests.HttpStatusException) {
                httpUp.complete(null)
                return@Thread
            } catch (_: IOException) {
                Thread.sleep(200)
            }
        }
        if (!httpUp.isDone)
            httpUp.completeExceptionally(IOException("marimo server did not start: $url"))
    }
        .apply { isDaemon = true }
        .start()
}

private class ProcessMarimoServerHandle(
    private val ready: CompletableFuture<String>,
    private val tokenPasswordFile: File?,
) : MarimoServerHandle {
    @Volatile private lateinit var handler: OSProcessHandler
    private val terminationLock = Any()
    private var terminationListener: ((Int, String) -> Unit)? = null
    private var termination: Termination? = null

    private data class Termination(val exitCode: Int, val outputTail: String)

    override val isAlive: Boolean
        get() = !handler.isProcessTerminated

    override val processHandle: ProcessHandler
        get() = handler

    override fun awaitReady(): CompletableFuture<String> = ready

    override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
        val previousTermination =
            synchronized(terminationLock) {
                terminationListener = listener
                termination
            }
        previousTermination?.let { listener(it.exitCode, it.outputTail) }
    }

    fun notifyTerminated(exitCode: Int, outputTail: String) {
        val listener =
            synchronized(terminationLock) {
                if (termination != null) return
                termination = Termination(exitCode, outputTail)
                terminationListener
            }
        try {
            listener?.invoke(exitCode, outputTail)
        } finally {
            deleteTokenPasswordFile()
        }
    }

    /** Points the handle at the live process; called again when a fallback attempt is spawned. */
    fun attach(handler: OSProcessHandler) {
        this.handler = handler
    }

    override fun dispose() {
        try {
            if (::handler.isInitialized) handler.destroyProcess()
        } finally {
            deleteTokenPasswordFile()
        }
    }

    private fun deleteTokenPasswordFile() {
        tokenPasswordFile?.delete()
    }
}
