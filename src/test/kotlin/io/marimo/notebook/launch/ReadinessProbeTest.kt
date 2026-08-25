/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessProbeTest {
    @Test
    fun recognizesMarimoPageMarker() {
        assertTrue(ReadinessProbe.looksLikeMarimoPage("""<html><marimo-user-config data-config="{}">"""))
    }

    @Test
    fun rejectsUnrelatedHttpBody() {
        assertFalse(ReadinessProbe.looksLikeMarimoPage("HTTP/1.1 200 OK"))
    }
}
