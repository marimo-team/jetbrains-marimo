/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentTransitionTest {

    private data class RecordedEvent(val name: String, val properties: Map<String, Any>)

    private class RecordingSink : PostHogSink {
        val events = mutableListOf<RecordedEvent>()
        var closeCalls = 0

        val closed: Boolean
            get() = closeCalls > 0

        override fun capture(distinctId: String, event: String, properties: Map<String, Any>) {
            events += RecordedEvent(event, properties)
        }

        override fun close() {
            closeCalls++
        }
    }

    private class RecordingSentrySink : SentrySink {
        val captured = mutableListOf<Throwable>()
        var closeCalls = 0
        var sessionsStarted = 0
        var sessionsEnded = 0

        val closed: Boolean
            get() = closeCalls > 0

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
            closeCalls++
        }
    }

    private fun marimoError() =
        RuntimeException("boom").apply {
            stackTrace =
                arrayOf(
                    StackTraceElement(
                        "io.marimo.notebook.launch.SdkLauncher",
                        "start",
                        "SdkLauncher.kt",
                        1,
                    )
                )
        }

    private fun restoredAllowedTelemetry(
        postHog: RecordingSink,
        sentry: RecordingSentrySink,
    ): MarimoTelemetry =
        MarimoTelemetry().withSinkForTest(postHog).withSentrySinkForTest(sentry).also {
            it.loadState(
                MarimoTelemetry.PersistedState(
                    consent = Consent.ALLOWED,
                    anonymousId = "existing-id",
                )
            )
        }

    @Test
    fun allowThenRevoke() {
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

    @Test
    fun denyBuildsNoTransport() {
        val telemetry = MarimoTelemetry()

        telemetry.deny()
        assertEquals(Consent.DENIED, telemetry.consent)

        telemetry.capture(TelemetryEvent.SandboxStarted)
        assertTrue(telemetry.anonymousId().isBlank())
    }

    @Test
    fun deniedConsentPreventsStartupBlockedBehindTeardown() {
        val postHog = RecordingSink()
        val sentry = RecordingSentrySink()
        val telemetry =
            MarimoTelemetry().withSinkForTest(postHog).withSentrySinkForTest(sentry).also {
                it.setConsentForTest(Consent.ALLOWED)
            }
        val capture = Thread { telemetry.captureException(marimoError()) }

        synchronized(telemetry) {
            capture.start()
            assertTrue("capture did not reach startup lock", waitUntilBlocked(capture))
            telemetry.deny()
            telemetry.withSinkForTest(postHog).withSentrySinkForTest(sentry)
        }
        capture.join(TimeUnit.SECONDS.toMillis(5))

        assertTrue("capture thread did not finish", !capture.isAlive)
        assertTrue(telemetry.anonymousId().isBlank())
        assertEquals(0, sentry.sessionsStarted)
        assertTrue(sentry.captured.isEmpty())
        telemetry.dispose()
    }

    @Test
    fun sentryCapturesWhenAllowedAndStopsAfterRevoke() {
        val sentry = RecordingSentrySink()
        val telemetry =
            MarimoTelemetry().withSinkForTest(RecordingSink()).withSentrySinkForTest(sentry)

        telemetry.allow()
        val error = marimoError()
        telemetry.captureException(error)
        assertEquals(listOf<Throwable>(error), sentry.captured)

        telemetry.revoke()
        assertTrue(sentry.closed)

        telemetry.captureException(marimoError())
        assertEquals(1, sentry.captured.size)
    }

    @Test
    fun sentrySessionStartsOnAllowAndEndsOnRevoke() {
        val sentry = RecordingSentrySink()
        val telemetry =
            MarimoTelemetry().withSinkForTest(RecordingSink()).withSentrySinkForTest(sentry)

        telemetry.allow()
        assertEquals(1, sentry.sessionsStarted)
        assertEquals(0, sentry.sessionsEnded)

        telemetry.captureException(marimoError())
        assertEquals(1, sentry.sessionsStarted)

        telemetry.revoke()
        assertEquals(1, sentry.sessionsEnded)
    }

    @Test
    fun persistedAllowedUsageCaptureStartsOneSentrySession() {
        val postHog = RecordingSink()
        val sentry = RecordingSentrySink()
        val telemetry = restoredAllowedTelemetry(postHog, sentry)

        telemetry.capture(TelemetryEvent.SandboxStarted)
        telemetry.capture(TelemetryEvent.SandboxStarted)

        assertEquals(2, postHog.events.size)
        assertEquals(1, sentry.sessionsStarted)
    }

    @Test
    fun persistedAllowedExceptionCaptureStartsOneSentrySession() {
        val sentry = RecordingSentrySink()
        val telemetry = restoredAllowedTelemetry(RecordingSink(), sentry)

        telemetry.captureException(marimoError())
        telemetry.captureException(marimoError())

        assertEquals(2, sentry.captured.size)
        assertEquals(1, sentry.sessionsStarted)
    }

    @Test
    fun sentryNoCaptureWhenDenied() {
        val sentry = RecordingSentrySink()
        val telemetry = MarimoTelemetry().withSentrySinkForTest(sentry)

        telemetry.deny()
        telemetry.captureException(marimoError())
        assertTrue(sentry.captured.isEmpty())
        assertTrue(sentry.closed)
    }

    @Test
    fun staleDenyAfterAllowClosesTransportsOnce() {
        val postHog = RecordingSink()
        val sentry = RecordingSentrySink()
        val telemetry = MarimoTelemetry().withSinkForTest(postHog).withSentrySinkForTest(sentry)

        telemetry.allow()
        telemetry.deny()
        telemetry.deny()
        telemetry.revoke()

        assertEquals(Consent.DENIED, telemetry.consent)
        assertEquals(1, postHog.closeCalls)
        assertEquals(1, sentry.sessionsEnded)
        assertEquals(1, sentry.closeCalls)
    }

    private fun waitUntilBlocked(thread: Thread): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (thread.state == Thread.State.BLOCKED) return true
            Thread.onSpinWait()
        }
        return false
    }
}
