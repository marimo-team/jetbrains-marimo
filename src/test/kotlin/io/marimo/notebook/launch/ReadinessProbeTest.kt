/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReadinessProbeTest {
    @Test
    fun recognizesMarimoPageMarker() {
        assertTrue(
            ReadinessProbe.looksLikeMarimoPage("""<html><marimo-user-config data-config="{}">""")
        )
    }

    @Test
    fun rejectsUnrelatedHttpBody() {
        assertFalse(ReadinessProbe.looksLikeMarimoPage("HTTP/1.1 200 OK"))
    }

    @Test
    fun failureMessageRedactsAccessToken() {
        val message =
            ReadinessProbe.readinessFailureMessage("http://127.0.0.1:2718?access_token=SECRET")
        assertTrue(message.contains("127.0.0.1:2718"))
        assertFalse(message.contains("SECRET"))
        assertFalse(message.contains("access_token=SECRET"))
    }

    @Test
    fun failureMessageKeepsIpv6OriginWellFormed() {
        val message =
            ReadinessProbe.readinessFailureMessage("http://[::1]:2718?access_token=SECRET")
        assertTrue(message.contains("http://[::1]:2718"))
        assertFalse(message.contains("SECRET"))
    }

    @Test
    fun pollFailureDoesNotExposeAccessToken() {
        val ready = CompletableFuture<Void?>()
        ReadinessProbe.pollUntilReady("http://127.0.0.1:1?access_token=SECRET", ready, 1)
        try {
            ready.get(5, TimeUnit.SECONDS)
            fail("readiness must time out against an unused port")
        } catch (e: ExecutionException) {
            val message = e.cause!!.message!!
            assertFalse(message.contains("SECRET"))
            assertFalse(message.contains("access_token=SECRET"))
        }
    }

    @Test
    fun malformedProbeUrlCompletesExceptionally() {
        val ready = CompletableFuture<Void?>()
        ReadinessProbe.pollUntilReady("not a valid URL?access_token=SECRET", ready, 30)

        try {
            ready.get(3, TimeUnit.SECONDS)
            fail("a malformed readiness URL must fail")
        } catch (e: ExecutionException) {
            assertFalse(e.cause!!.message.orEmpty().contains("SECRET"))
        }
    }

    @Test
    fun crossOriginRedirectDoesNotCountAsReady() {
        val destination = serve { socket ->
            socket.getOutputStream().use { output ->
                output.write(httpResponse("200 OK", MARIMO_PAGE_BODY))
            }
        }
        val redirect = serve { socket ->
            val response =
                "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${destination.localPort}/\r\nContent-Length: 0\r\n\r\n"
            socket.getOutputStream().use { output ->
                output.write(response.toByteArray(StandardCharsets.UTF_8))
            }
        }
        try {
            val ready = CompletableFuture<Void?>()
            ReadinessProbe.pollUntilReady("http://127.0.0.1:${redirect.localPort}/", ready, 1)
            try {
                ready.get(3, TimeUnit.SECONDS)
                fail("readiness must not follow a redirect to another origin")
            } catch (_: ExecutionException) {
                // Expected timeout: the redirect body is not a marimo page.
            }
        } finally {
            redirect.close()
            destination.close()
        }
    }

    @Test
    fun endlessResponseBodyCannotOutliveReadinessDeadline() {
        val server = serve { socket ->
            socket.getOutputStream().use { output ->
                output.write(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n"
                        .toByteArray(StandardCharsets.UTF_8)
                )
                val chunk = "x".repeat(1024).toByteArray(StandardCharsets.UTF_8)
                while (true) {
                    output.write(chunk)
                    output.flush()
                }
            }
        }
        try {
            val ready = CompletableFuture<Void?>()
            ReadinessProbe.pollUntilReady("http://127.0.0.1:${server.localPort}/", ready, 1)
            try {
                ready.get(3, TimeUnit.SECONDS)
                fail("an endless non-marimo body must fail readiness")
            } catch (_: ExecutionException) {
                // Expected timeout after bounded reads.
            }
        } finally {
            server.close()
        }
    }

    private fun serve(handler: (Socket) -> Unit): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use(handler)
                } catch (e: Exception) {
                    if (!server.isClosed) throw e
                }
            }
        }
            .apply {
                isDaemon = true
                start()
            }
        return server
    }
}
