/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
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
            try {
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
            } catch (_: Exception) {
                // The sanitized failure below also covers malformed URLs and interrupted probes.
            } finally {
                if (!ready.isDone) {
                    ready.completeExceptionally(IOException(readinessFailureMessage(probeUrl)))
                }
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
            val bareHost = host.removeSurrounding("[", "]")
            val urlHost = if (bareHost.contains(':')) "[$bareHost]" else bareHost
            if (uri.port == -1) "$scheme://$urlHost" else "$scheme://$urlHost:${uri.port}"
        } catch (_: Exception) {
            redactAccessTokens(probeUrl)
        }

    /**
     * Uses [HttpURLConnection] directly so non-2xx bodies (for example token-auth 401 pages) can be
     * read from [HttpURLConnection.getErrorStream] without HttpRequests' automatic status
     * exceptions.
     */
    private fun fetchPageBody(probeUrl: String, attemptTimeoutMs: Int): String? {
        return try {
            val connection =
                (URI(probeUrl).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = attemptTimeoutMs
                    readTimeout = attemptTimeoutMs
                    instanceFollowRedirects = false
                }
            try {
                val status = connection.responseCode
                if (status in 300..399) return null
                val stream =
                    when (status) {
                        in 200..299 -> connection.inputStream
                        else -> connection.errorStream ?: connection.inputStream
                    }
                stream?.let { body ->
                    InputStreamReader(body, StandardCharsets.UTF_8).use { reader ->
                        val result = StringBuilder()
                        val chars = CharArray(READ_BUFFER_CHARS)
                        while (result.length < MAX_RESPONSE_CHARS) {
                            val remaining = MAX_RESPONSE_CHARS - result.length
                            val count = reader.read(chars, 0, minOf(chars.size, remaining))
                            if (count < 0) break
                            result.append(chars, 0, count)
                        }
                        result.toString()
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        }
    }

    private const val POLL_INTERVAL_MS = 200L
    private const val MAX_RESPONSE_CHARS = 64 * 1024
    private const val READ_BUFFER_CHARS = 4 * 1024
}
