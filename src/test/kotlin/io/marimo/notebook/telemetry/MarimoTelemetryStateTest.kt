/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoTelemetryStateTest {
    @Test fun anonymousIdIsStableAcrossReload() {
        val service = MarimoTelemetry()
        val id = service.anonymousId()
        assertTrue(id.isNotBlank())

        val restored = MarimoTelemetry()
        restored.loadState(service.state)
        assertEquals(id, restored.anonymousId())
    }

    @Test fun consentDefaultsToUnset() {
        assertEquals(Consent.UNSET, MarimoTelemetry().state.consent)
    }
}
