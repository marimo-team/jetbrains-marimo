/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoStartupOutputTest {

    private val plainBanner =
        "\n        Edit nb.py in your browser\n\n" +
            "        ➜  URL: http://127.0.0.1:2718?access_token=aXb12Cd\n\n"

    /**
     * The exact shape marimo prints to a terminal: a green label, then a bold URL whose query is
     * dimmed separately, so escape codes sit inside the URL itself.
     */
    private val ansiBanner =
        "        ➜  \u001B[32mURL\u001B[0m: " +
            "\u001B[1mhttp://127.0.0.1:2718\u001B[90m?access_token=aXb12Cd\u001B[0m\u001B[0m\n"

    @Test fun plainBannerYieldsTheFullUrl() {
        assertEquals(
            "http://127.0.0.1:2718?access_token=aXb12Cd",
            extractStartupUrl("        ➜  URL: http://127.0.0.1:2718?access_token=aXb12Cd\n"),
        )
    }

    @Test fun ansiBannerYieldsTheCleanUrl() {
        assertEquals("http://127.0.0.1:2718?access_token=aXb12Cd", extractStartupUrl(ansiBanner))
    }

    @Test fun tokenlessBannerYieldsThePlainUrl() {
        assertEquals("http://127.0.0.1:2718", extractStartupUrl("        ➜  URL: http://127.0.0.1:2718\n"))
    }

    @Test fun bannerWithoutTheArrowStillParses() {
        assertEquals("http://127.0.0.1:2718", extractStartupUrl("URL: http://127.0.0.1:2718\n"))
    }

    @Test fun incompleteLineYieldsNullUntilTheNewlineArrives() {
        assertNull("chunked output must not yield a truncated URL", extractStartupUrl("        ➜  URL: http://127.0.0.1:27"))
        assertEquals(
            "http://127.0.0.1:2718?access_token=t",
            extractStartupUrl("        ➜  URL: http://127.0.0.1:27" + "18?access_token=t\n"),
        )
    }

    @Test fun networkLineIsNotMistakenForTheUrlLine() {
        assertNull(extractStartupUrl("        ➜  Network: http://10.0.0.5:2718?access_token=t\n"))
    }

    @Test fun surroundingOutputDoesNotConfuseTheParser() {
        val output = plainBanner + "        ➜  Network: http://10.0.0.5:2718\nINFO uvicorn running\n"
        assertEquals("http://127.0.0.1:2718?access_token=aXb12Cd", extractStartupUrl(output))
    }

    @Test fun redactionReplacesEveryTokenValue() {
        val text = "URL: http://127.0.0.1:2718?access_token=SECRET1\nretry http://127.0.0.1:2718?access_token=SECRET2&x=1"
        val redacted = redactAccessTokens(text)
        assertFalse(redacted.contains("SECRET1"))
        assertFalse(redacted.contains("SECRET2"))
        assertTrue(redacted.contains("access_token=<redacted>"))
        assertTrue("text after the token must survive", redacted.contains("&x=1"))
    }

    @Test fun redactionLeavesOtherTextAlone() {
        val text = "marimo exited (code 1) before serving http://127.0.0.1:2718\nTraceback ..."
        assertEquals(text, redactAccessTokens(text))
    }

    @Test fun stripAnsiRemovesColorCodesOnly() {
        assertEquals("URL: x", stripAnsi("\u001B[32mURL\u001B[0m: x"))
    }
}
