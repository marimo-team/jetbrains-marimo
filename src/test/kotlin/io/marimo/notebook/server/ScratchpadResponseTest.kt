/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchpadResponseTest {
    @Test fun assemblesStdoutChunksAndDone() {
        val raw = """
            event: stdout
            data: {"data": "hello "}

            event: stdout
            data: {"data": "world"}

            event: done
            data: {"success": true, "output": {"mimetype": "text/plain", "data": "42"}}
        """.trimIndent()
        val r = parseScratchpadSse(raw)
        assertEquals("hello world", r.stdout)
        assertTrue(r.success)
        assertEquals("42", r.outputData)
        assertNull(r.errorMsg)
    }

    @Test fun capturesErrorOnFailure() {
        val raw = """
            event: done
            data: {"success": false, "error": {"msg": "boom"}}
        """.trimIndent()
        val r = parseScratchpadSse(raw)
        assertFalse(r.success)
        assertEquals("boom", r.errorMsg)
    }
}
