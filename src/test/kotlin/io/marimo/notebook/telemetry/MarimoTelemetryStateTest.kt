/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoTelemetryStateTest {
    private object NoOpPostHogSink : PostHogSink {
        override fun capture(distinctId: String, event: String, properties: Map<String, Any>) = Unit

        override fun close() = Unit
    }

    private object NoOpSentrySink : SentrySink {
        override fun captureException(throwable: Throwable) = Unit

        override fun startSession() = Unit

        override fun endSession() = Unit

        override fun close() = Unit
    }

    @Test
    fun unsetStateSerializationDoesNotCreateAnonymousId() {
        val service = MarimoTelemetry()

        assertEquals(Consent.UNSET, service.state.consent)
        assertTrue(service.state.anonymousId.isBlank())
    }

    @Test
    fun deniedStateSerializationDoesNotCreateAnonymousId() {
        val service = MarimoTelemetry()

        service.deny()

        assertEquals(Consent.DENIED, service.state.consent)
        assertTrue(service.state.anonymousId.isBlank())
    }

    @Test
    fun allowedStateSerializationContainsStableAnonymousId() {
        val service =
            MarimoTelemetry().withSinkForTest(NoOpPostHogSink).withSentrySinkForTest(NoOpSentrySink)

        service.allow()
        val persisted = service.state

        assertEquals(Consent.ALLOWED, persisted.consent)
        assertTrue(persisted.anonymousId.isNotBlank())

        val restored = MarimoTelemetry()
        restored.loadState(persisted)
        assertEquals(persisted.anonymousId, restored.anonymousId())
    }
}
