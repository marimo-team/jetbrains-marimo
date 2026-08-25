/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReadinessProbeTest {
    @Test
    fun recognizesMarimoPageMarker() {
        assertTrue(
            ReadinessProbe.looksLikeMarimoPage("""<html><marimo-user-config data-config="{}">""")
        )
    }

    @Test
    fun rejectsUnrelatedHttpBody() {
        assertFalse(ReadinessProbe.looksLikeMarimoPage("HTTP/1.1 200 OK"))
    }

    @Test
    fun failureMessageRedactsAccessToken() {
        val message =
            ReadinessProbe.readinessFailureMessage("http://127.0.0.1:2718?access_token=SECRET")
        assertTrue(message.contains("127.0.0.1:2718"))
        assertFalse(message.contains("SECRET"))
        assertFalse(message.contains("access_token=SECRET"))
    }

    @Test
    fun pollFailureDoesNotExposeAccessToken() {
        val ready = CompletableFuture<Void?>()
        ReadinessProbe.pollUntilReady("http://127.0.0.1:1?access_token=SECRET", ready, 1)
        try {
            ready.get(5, TimeUnit.SECONDS)
            fail("readiness must time out against an unused port")
        } catch (e: ExecutionException) {
            val message = e.cause!!.message!!
            assertFalse(message.contains("SECRET"))
            assertFalse(message.contains("access_token=SECRET"))
        }
    }
}
