/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryCaptureTest {

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

    @Test fun noCaptureWhenNotAllowed() {
        val sink = RecordingSink()
        val telemetry = MarimoTelemetry().withSinkForTest(sink)
        telemetry.setConsentForTest(Consent.DENIED)

        telemetry.capture(TelemetryEvent.NotebookOpened(launcher = "sdk"))

        assertTrue(sink.events.isEmpty())
    }

    @Test fun captureWhenAllowed() {
        val sink = RecordingSink()
        val telemetry = MarimoTelemetry().withSinkForTest(sink)
        telemetry.setConsentForTest(Consent.ALLOWED)

        telemetry.capture(TelemetryEvent.NotebookOpened(launcher = "sdk"))

        assertEquals(listOf("notebook_opened"), sink.events.map { it.name })
        assertEquals("sdk", sink.events.single().properties["launcher"])
        assertTrue(sink.events.single().properties.keys.none { it.contains("path") })
    }

    @Test fun everyEventCarriesPluginVersion() {
        val sink = RecordingSink()
        val telemetry = MarimoTelemetry().withSinkForTest(sink)
        telemetry.setConsentForTest(Consent.ALLOWED)

        telemetry.capture(TelemetryEvent.SandboxStarted)

        assertTrue(sink.events.single().properties.containsKey("plugin_version"))
    }
}
