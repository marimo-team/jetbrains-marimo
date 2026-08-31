/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

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
        LoopbackHttpServer { socket ->
            socket.getOutputStream().use { output ->
                output.write(httpResponse("200 OK", MARIMO_PAGE_BODY))
            }
        }
            .use { destination ->
                LoopbackHttpServer { socket ->
                    val response =
                        "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${destination.port}/\r\nContent-Length: 0\r\n\r\n"
                    socket.getOutputStream().use { output ->
                        output.write(response.toByteArray(StandardCharsets.UTF_8))
                    }
                }
                    .use { redirect ->
                        val ready = CompletableFuture<Void?>()
                        ReadinessProbe.pollUntilReady(
                            "http://127.0.0.1:${redirect.port}/",
                            ready,
                            1,
                        )
                        try {
                            ready.get(3, TimeUnit.SECONDS)
                            fail("readiness must not follow a redirect to another origin")
                        } catch (_: ExecutionException) {
                            // Expected timeout: the redirect body is not a marimo page.
                        }
                    }
            }
    }

    @Test
    fun followsSameOriginTokenRedirectWithSessionCookie() {
        LoopbackHttpServer { socket ->
            val headers = mutableListOf<String>()
            val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                headers.add(line)
            }
            val response =
                if (headers.any { it.startsWith("Cookie:") && it.contains("session=ready") }) {
                    httpResponse("200 OK", MARIMO_PAGE_BODY)
                } else {
                    ("HTTP/1.1 303 See Other\r\n" +
                            "Location: /\r\n" +
                            "Set-Cookie: session=ready; Path=/; HttpOnly\r\n" +
                            "Content-Length: 0\r\n\r\n")
                        .toByteArray(StandardCharsets.UTF_8)
                }
            socket.getOutputStream().use { output ->
                output.write(response)
            }
        }
            .use { server ->
                val ready = CompletableFuture<Void?>()
                ReadinessProbe.pollUntilReady(
                    "http://127.0.0.1:${server.port}/?access_token=TOKEN",
                    ready,
                    1,
                )

                ready.get(3, TimeUnit.SECONDS)
            }
    }

    @Test
    fun endlessResponseBodyCannotOutliveReadinessDeadline() {
        LoopbackHttpServer { socket ->
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
            .use { server ->
                val ready = CompletableFuture<Void?>()
                ReadinessProbe.pollUntilReady("http://127.0.0.1:${server.port}/", ready, 1)
                try {
                    ready.get(3, TimeUnit.SECONDS)
                    fail("an endless non-marimo body must fail readiness")
                } catch (_: ExecutionException) {
                    // Expected timeout after bounded reads.
                }
            }
    }
}
