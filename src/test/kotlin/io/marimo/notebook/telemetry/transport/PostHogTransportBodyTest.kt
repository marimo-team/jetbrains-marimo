/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import io.marimo.notebook.telemetry.Consent
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHogTransportBodyTest {
    @Test
    fun serializedCaptureBodyMatchesPrivacyAllowlist() {
        val bodies = CopyOnWriteArrayList<String>()
        val received = CountDownLatch(1)
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            val body = decodeBody(exchange.requestBody.readBytes())
            if (exchange.requestMethod == "POST" && exchange.requestURI.path.contains("batch")) {
                bodies += body
                received.countDown()
            }
            val response = "{}".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val telemetry = MarimoTelemetry()
        try {
            val host = "http://127.0.0.1:${server.address.port}"
            telemetry.withSinkForTest(PostHogTransport("phc_test", host))
            telemetry.setConsentForTest(Consent.ALLOWED)
            telemetry.capture(TelemetryEvent.NotebookOpened(launcher = "sdk"))
            val distinctId = telemetry.anonymousId()
            telemetry.dispose()

            assertTrue("PostHog did not POST /batch", received.await(15, TimeUnit.SECONDS))
            val event = batchEvent(bodies.single { it.contains("notebook_opened") })
            val properties = event.getAsJsonObject("properties")
            assertEquals("notebook_opened", event.get("event").asString)
            assertEquals(ALLOWED_EVENT_PROPERTIES, properties.keySet())
            assertEquals("sdk", properties.get("launcher").asString)
            assertEquals(distinctId, properties.get("distinct_id").asString)
            assertTrue(properties.get("plugin_version").asString.isNotBlank())
            assertTrue(properties.get("environment").asString.isNotBlank())
            assertTrue(properties.get("\$lib").asString.isNotBlank())
            assertTrue(properties.get("\$lib_version").asString.isNotBlank())
            assertFalse(properties.has("\$ip"))
            assertFalse(properties.keySet().any { it.startsWith("\$geoip") })
            assertFalse(properties.keySet().any { it.contains("path", ignoreCase = true) })
        } finally {
            telemetry.dispose()
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun decodeBody(raw: ByteArray): String =
        try {
            GZIPInputStream(ByteArrayInputStream(raw)).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            raw.toString(Charsets.UTF_8)
        }

    private fun batchEvent(body: String): JsonObject {
        val json = JsonParser.parseString(body).asJsonObject
        require(json.has("batch")) { "expected PostHog /batch body: $body" }
        return json
            .getAsJsonArray("batch")
            .first { it.asJsonObject.get("event").asString == "notebook_opened" }
            .asJsonObject
    }

    companion object {
        private val ALLOWED_EVENT_PROPERTIES =
            setOf(
                "launcher",
                "plugin_version",
                "environment",
                "distinct_id",
                "\$lib",
                "\$lib_version",
            )
    }
}
