/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTransportsTest {
    @Test
    fun liveFlagIsOffByDefault() {
        assertFalse(liveTelemetryEnabled(null))
        assertFalse(liveTelemetryEnabled("false"))
        assertFalse(liveTelemetryEnabled("development"))
    }

    @Test
    fun liveFlagIsOnOnlyForTrue() {
        assertTrue(liveTelemetryEnabled("true"))
        assertTrue(liveTelemetryEnabled("TRUE"))
    }

    @Test
    fun developmentUsesNoOpPostHog() {
        val sink = postHogSink(live = false, apiKey = "unused", host = "http://127.0.0.1")
        assertSame(NoOpPostHogSink, sink)
        sink.capture("id", "event", emptyMap())
        sink.close()
    }

    @Test
    fun developmentUsesNoOpSentry() {
        val sink =
            sentrySink(
                live = false,
                dsn = "https://public@example.invalid/1",
                release = "jetbrains-marimo@test",
                environment = "development",
                ideName = "test",
                ideVersion = "1",
                pluginVersion = "0",
                anonymousId = "id",
            )
        assertSame(NoOpSentrySink, sink)
        sink.apply {
            captureException(RuntimeException("boom"))
            startSession()
            endSession()
            close()
        }
    }
}
