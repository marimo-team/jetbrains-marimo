/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarimoEnvProbeTest {
    @Test
    fun parsesVersionFromVersionOnlyOutput() {
        assertEquals("0.9.14", MarimoEnvProbe.parseVersion("0.9.14"))
    }

    @Test
    fun parsesVersionFromLabeledOutput() {
        assertEquals("0.9.14", MarimoEnvProbe.parseVersion("marimo 0.9.14\n"))
    }

    @Test
    fun parsesPrereleaseVersion() {
        assertEquals("0.10.0.dev3", MarimoEnvProbe.parseVersion("marimo 0.10.0.dev3"))
    }

    @Test
    fun returnsNullWhenNoVersionPresent() {
        assertNull(MarimoEnvProbe.parseVersion("command not found"))
    }
}
