/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoStartupOutputTest {

    @Test
    fun redactionReplacesEveryTokenValue() {
        val text =
            "URL: http://127.0.0.1:2718?access_token=SECRET1\nretry http://127.0.0.1:2718?access_token=SECRET2&x=1"
        val redacted = redactAccessTokens(text)
        assertFalse(redacted.contains("SECRET1"))
        assertFalse(redacted.contains("SECRET2"))
        assertTrue(redacted.contains("<redacted-token>"))
        assertFalse(redacted.contains("access_token"))
        assertTrue("text after the token must survive", redacted.contains("&x=1"))
    }

    @Test
    fun redactionLeavesOtherTextAlone() {
        val text = "marimo exited (code 1) before serving http://127.0.0.1:2718\nTraceback ..."
        assertEquals(text, redactAccessTokens(text))
    }

    @Test
    fun diagnosticTailRedactsATokenSplitAcrossOutputChunks() {
        val tail =
            diagnosticOutputTail(
                listOf("URL: http://127.0.0.1:2718?access_token=SECRET", "TOKEN\nprocess exited")
            )
        assertFalse(tail.contains("SECRETTOKEN"))
        assertTrue(tail.contains("<redacted-token>"))
        assertFalse(tail.contains("access_token"))
    }
}
