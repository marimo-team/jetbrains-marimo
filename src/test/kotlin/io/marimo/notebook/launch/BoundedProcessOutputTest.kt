/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedProcessOutputTest {

    @Test
    fun oversizedOutputKeepsOnlyTheTail() {
        val output = BoundedProcessOutput()
        val chunk = "x".repeat(4 * 1024)
        repeat(8) { output.append(chunk) }

        assertTrue(
            "retained output must stay bounded, was ${output.snapshot().length}",
            output.snapshot().length <=
                BoundedProcessOutput.CAPACITY_CHARS + BoundedProcessOutput.MARKER_OVERLAP_CHARS,
        )
        assertTrue(output.snapshot().all { it == 'x' })
    }

    @Test
    fun truncationPreservesUnsupportedWatchMarkerAcrossChunks() {
        val output = BoundedProcessOutput()
        output.append("a".repeat(BoundedProcessOutput.CAPACITY_CHARS))
        output.append("No such opt")
        output.append("ion: --watch\n")

        assertTrue(indicatesUnsupportedWatch(output.snapshot()))
    }

    @Test
    fun truncationPreservesTokenForRedaction() {
        val output = BoundedProcessOutput()
        output.append("b".repeat(BoundedProcessOutput.CAPACITY_CHARS))
        output.append("URL: http://127.0.0.1:2718?access_token=SECRET")
        output.append("TOKEN\n")

        val tail = diagnosticOutputTail(output.snapshot())
        assertFalse(tail.contains("SECRETTOKEN"))
        assertTrue(tail.contains("<redacted-token>"))
        assertFalse(tail.contains("access_token"))
    }
}
