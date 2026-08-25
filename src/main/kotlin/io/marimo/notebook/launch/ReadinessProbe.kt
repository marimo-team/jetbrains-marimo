/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Polls a marimo server until its page marker appears or the deadline expires. */
internal object ReadinessProbe {
    /** Matches the config tag [io.marimo.notebook.session.PageConfigReader] relies on. */
    private val MARIMO_PAGE_MARKER = Regex("""<marimo-user-config""")

    fun pollUntilReady(
        probeUrl: String,
        ready: CompletableFuture<Void?>,
        timeoutSeconds: Long,
    ) {
        Thread {
                val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                while (!ready.isDone && System.nanoTime() < deadlineNanos) {
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0) break
                    val attemptTimeoutMs =
                        TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1)
                    val body = fetchPageBody(probeUrl, attemptTimeoutMs.toInt())
                    if (body != null && looksLikeMarimoPage(body)) {
                        ready.complete(null)
                        return@Thread
                    }
                    val sleepMs = minOf(POLL_INTERVAL_MS, attemptTimeoutMs)
                    if (sleepMs > 0) Thread.sleep(sleepMs)
                }
                if (!ready.isDone) {
                    ready.completeExceptionally(
                        IOException("marimo server did not start: $probeUrl")
                    )
                }
            }
            .apply { isDaemon = true }
            .start()
    }

    internal fun looksLikeMarimoPage(body: String): Boolean = MARIMO_PAGE_MARKER.containsMatchIn(body)

    private fun fetchPageBody(probeUrl: String, attemptTimeoutMs: Int): String? =
        try {
            HttpRequests.request(probeUrl)
                .connectTimeout(attemptTimeoutMs)
                .readTimeout(attemptTimeoutMs)
                .connect { request ->
                    val connection = request.connection
                    if (connection is HttpURLConnection) {
                        val stream =
                            if (connection.responseCode in 200..299) request.inputStream
                            else connection.errorStream ?: request.inputStream
                        stream.bufferedReader().use { it.readText() }
                    } else {
                        request.inputStream.bufferedReader().use { it.readText() }
                    }
                }
        } catch (_: IOException) {
            null
        }

    private const val POLL_INTERVAL_MS = 200L
}
