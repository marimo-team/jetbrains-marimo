/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Polls a marimo server until its page marker appears or the deadline expires. */
internal object ReadinessProbe {
    /**
     * marimo embeds a `<marimo-user-config` tag in every served notebook page; [PageConfigReader]
     * reads theme settings from the same tag, so its presence distinguishes marimo from any HTTP
     * listener that happens to share the port.
     */
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
                ready.completeExceptionally(IOException(readinessFailureMessage(probeUrl)))
            }
        }
            .apply { isDaemon = true }
            .start()
    }

    internal fun looksLikeMarimoPage(body: String): Boolean =
        MARIMO_PAGE_MARKER.containsMatchIn(body)

    internal fun readinessFailureMessage(probeUrl: String): String {
        val safeTarget = probeOriginForDiagnostics(probeUrl)
        return "marimo server did not start: $safeTarget"
    }

    private fun probeOriginForDiagnostics(probeUrl: String): String =
        try {
            val uri = URI(probeUrl)
            val scheme = uri.scheme ?: return redactAccessTokens(probeUrl)
            val host = uri.host ?: return redactAccessTokens(probeUrl)
            if (scheme != "http" && scheme != "https") return redactAccessTokens(probeUrl)
            if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
        } catch (_: Exception) {
            redactAccessTokens(probeUrl)
        }

    /**
     * Uses [HttpURLConnection] directly so non-2xx bodies (for example token-auth 401 pages) can be
     * read from [HttpURLConnection.getErrorStream] without HttpRequests' automatic status
     * exceptions.
     */
    private fun fetchPageBody(probeUrl: String, attemptTimeoutMs: Int): String? =
        try {
            val connection =
                (URI(probeUrl).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = attemptTimeoutMs
                    readTimeout = attemptTimeoutMs
                }
            try {
                val stream =
                    when (connection.responseCode) {
                        in 200..299 -> connection.inputStream
                        else -> connection.errorStream ?: connection.inputStream
                    }
                stream?.bufferedReader()?.use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        }

    private const val POLL_INTERVAL_MS = 200L
}
