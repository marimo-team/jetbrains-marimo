/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentTransitionTest {

    private data class RecordedEvent(val name: String, val properties: Map<String, Any>)

    private class RecordingSink : PostHogSink {
        val events = mutableListOf<RecordedEvent>()
        var closed = false

        override fun capture(distinctId: String, event: String, properties: Map<String, Any>) {
            events += RecordedEvent(event, properties)
        }

        override fun close() {
            closed = true
        }
    }

    private class RecordingSentrySink : SentrySink {
        val captured = mutableListOf<Throwable>()
        var closed = false
        var sessionsStarted = 0
        var sessionsEnded = 0

        override fun captureException(throwable: Throwable) {
            captured += throwable
        }

        override fun startSession() {
            sessionsStarted++
        }

        override fun endSession() {
            sessionsEnded++
        }

        override fun close() {
            closed = true
        }
    }

    private fun marimoError() =
        RuntimeException("boom").apply {
            stackTrace = arrayOf(StackTraceElement("io.marimo.notebook.launch.SdkLauncher", "start", "SdkLauncher.kt", 1))
        }

    @Test fun allowThenRevoke() {
        val sink = RecordingSink()
        val sentry = RecordingSentrySink()
        val telemetry = MarimoTelemetry().withSinkForTest(sink).withSentrySinkForTest(sentry)

        telemetry.allow()
        assertEquals(Consent.ALLOWED, telemetry.consent)
        assertTrue(sink.events.any { it.name == "plugin_activated" })

        sink.events.clear()
        telemetry.revoke()
        assertEquals(Consent.DENIED, telemetry.consent)
        assertTrue(sink.closed)

        telemetry.capture(TelemetryEvent.SandboxStarted)
        assertTrue(sink.events.isEmpty())
    }

    @Test fun denyBuildsNoTransport() {
        val sink = RecordingSink()
        val telemetry = MarimoTelemetry().withSinkForTest(sink)

        telemetry.deny()
        assertEquals(Consent.DENIED, telemetry.consent)
        assertFalse(sink.closed)

        telemetry.capture(TelemetryEvent.SandboxStarted)
        assertTrue(sink.events.isEmpty())
    }

    @Test fun sentryCapturesWhenAllowedAndStopsAfterRevoke() {
        val sentry = RecordingSentrySink()
        val telemetry = MarimoTelemetry().withSinkForTest(RecordingSink()).withSentrySinkForTest(sentry)

        telemetry.allow()
        val error = marimoError()
        telemetry.captureException(error)
        assertEquals(listOf<Throwable>(error), sentry.captured)

        telemetry.revoke()
        assertTrue(sentry.closed)

        telemetry.captureException(marimoError())
        assertEquals(1, sentry.captured.size)
    }

    @Test fun sentrySessionStartsOnAllowAndEndsOnRevoke() {
        val sentry = RecordingSentrySink()
        val telemetry = MarimoTelemetry().withSinkForTest(RecordingSink()).withSentrySinkForTest(sentry)

        telemetry.allow()
        assertEquals(1, sentry.sessionsStarted)
        assertEquals(0, sentry.sessionsEnded)

        telemetry.captureException(marimoError())
        assertEquals(1, sentry.sessionsStarted)

        telemetry.revoke()
        assertEquals(1, sentry.sessionsEnded)
    }

    @Test fun sentryNoCaptureWhenDenied() {
        val sentry = RecordingSentrySink()
        val telemetry = MarimoTelemetry().withSentrySinkForTest(sentry)

        telemetry.deny()
        telemetry.captureException(marimoError())
        assertTrue(sentry.captured.isEmpty())
        assertFalse(sentry.closed)
    }
}
