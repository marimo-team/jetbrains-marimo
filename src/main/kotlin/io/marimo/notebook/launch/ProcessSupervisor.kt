/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import io.marimo.notebook.MarimoLocalhost
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * True when marimo aborted because it does not recognise `--watch`. marimo before 0.10 has no such
 * option, so Click rejects it outright ("No such option: --watch") instead of ignoring it. Used to
 * decide whether a launch is worth retrying without the flag.
 */
internal fun indicatesUnsupportedWatch(output: String): Boolean =
    output.contains("No such option") && output.contains("watch")

/** Remembers an unsupported-watch marker even after later process output drops it from the tail. */
internal class UnsupportedWatchDetector {
    private val recent = BoundedProcessOutput(capacityChars = 256, markerOverlapChars = 64)

    var detected: Boolean = false
        private set

    fun append(text: String) {
        if (detected || text.isEmpty()) return
        val combined = recent.snapshot() + text
        detected = indicatesUnsupportedWatch(combined)
        recent.append(text)
    }
}

/** Redacts complete process output before it is retained in a user-visible diagnostic. */
internal fun diagnosticOutputTail(text: String): String =
    redactAccessTokens(text).trim().takeLast(500)

/**
 * Spawns a marimo process and completes [MarimoServerHandle.awaitReady] once BOTH startup signals
 * arrive: the served page looks like marimo, and the URL JCEF must load is known. When
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
    val probeUrl = authenticatedUrl ?: expectedUrl
    val handle =
        ProcessMarimoServerHandle(
            ready = ready,
            httpUp = httpUp,
            expectedUrl = expectedUrl,
            probeUrl = probeUrl,
            readinessTimeoutSeconds = readinessTimeoutSeconds,
            tokenPasswordFile = tokenPasswordFile?.let(::File),
        )
    try {
        handle.launch(cmd, watchFallbackCmd)
    } catch (e: Exception) {
        handle.dispose()
        throw e
    }
    return handle
}

private class ProcessMarimoServerHandle(
    private val ready: CompletableFuture<String>,
    private val httpUp: CompletableFuture<Void?>,
    private val expectedUrl: String,
    private val probeUrl: String,
    private val readinessTimeoutSeconds: Long,
    private val tokenPasswordFile: File?,
) : MarimoServerHandle {
    private val supervisorLock = Any()
    private val terminationLock = Any()
    @Volatile private var disposed = false
    @Volatile private var handler: OSProcessHandler? = null
    private var terminationListener: ((Int, String) -> Unit)? = null
    private var termination: Termination? = null

    private data class Termination(val exitCode: Int, val outputTail: String)

    override val isAlive: Boolean
        get() = handler?.let { !it.isProcessTerminated } == true

    override val processHandle: ProcessHandler
        get() = handler ?: error("no process is attached")

    override fun awaitReady(): CompletableFuture<String> = ready

    override fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit) {
        val previousTermination =
            synchronized(terminationLock) {
                terminationListener = listener
                termination
            }
        previousTermination?.let { listener(it.exitCode, it.outputTail) }
    }

    fun launch(cmd: GeneralCommandLine, watchFallbackCmd: (() -> GeneralCommandLine)?) {
        synchronized(supervisorLock) {
            if (disposed) return
            runAttempt(cmd, watchFallbackCmd)
        }
        ReadinessProbe.pollUntilReady(probeUrl, httpUp, readinessTimeoutSeconds)
    }

    private fun runAttempt(command: GeneralCommandLine, fallback: (() -> GeneralCommandLine)?) {
        val processHandler = OSProcessHandler(command)
        val output = BoundedProcessOutput()
        val unsupportedWatch = UnsupportedWatchDetector()

        synchronized(supervisorLock) {
            if (disposed) {
                processHandler.destroyProcess()
                return
            }
            handler = processHandler
        }

        processHandler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    output.append(event.text)
                    unsupportedWatch.append(event.text)
                }

                override fun processTerminated(event: ProcessEvent) {
                    val full = output.snapshot()
                    val diagnosticTail = diagnosticOutputTail(full)

                    if (ready.isDone) {
                        notifyTerminated(event.exitCode, diagnosticTail)
                        return
                    }

                    if (fallback != null && unsupportedWatch.detected) {
                        val fallbackCommand =
                            try {
                                fallback()
                            } catch (e: Exception) {
                                failBeforeReady(e, diagnosticTail, event.exitCode)
                                return
                            }
                        synchronized(supervisorLock) {
                            if (disposed) return
                            runAttempt(fallbackCommand, fallback = null)
                        }
                        return
                    }
                    failBeforeReady(
                        IOException(
                            "marimo exited (code ${event.exitCode}) before serving $expectedUrl\n$diagnosticTail"
                        ),
                        diagnosticTail,
                        event.exitCode,
                    )
                }
            }
        )
        processHandler.startNotify()
    }

    private fun failBeforeReady(error: Throwable, diagnosticTail: String, exitCode: Int) {
        if (!httpUp.isDone) {
            httpUp.completeExceptionally(error)
        }
        notifyTerminated(exitCode, diagnosticTail)
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

    override fun dispose() {
        synchronized(supervisorLock) {
            if (disposed) return
            disposed = true
            handler?.destroyProcess()
            handler = null
        }
        if (!httpUp.isDone) {
            httpUp.completeExceptionally(
                IOException("marimo server disposed before ready: $expectedUrl")
            )
        }
        deleteTokenPasswordFile()
    }

    private fun deleteTokenPasswordFile() {
        tokenPasswordFile?.delete()
    }
}
