/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoAccessTokenTest {

    @Test fun authenticatedUrlCarriesTheToken() {
        assertEquals(
            "http://127.0.0.1:2718?access_token=abc",
            authenticatedMarimoUrl("127.0.0.1", 2718, "abc"),
        )
    }

    @Test fun tokenPasswordFileIsUniqueAndContainsTheToken() {
        val a = writeTokenPasswordFile("token-a")
        val b = writeTokenPasswordFile("token-b")
        try {
            assertFalse("parallel launches must not share a path", a.absolutePath == b.absolutePath)
            assertEquals("token-a", a.readText().trim())
            assertEquals("token-b", b.readText().trim())
        } finally {
            a.delete()
            b.delete()
        }
    }

    @Test fun generatedTokensAreNonEmpty() {
        assertTrue(generateAccessToken().isNotBlank())
    }
}
