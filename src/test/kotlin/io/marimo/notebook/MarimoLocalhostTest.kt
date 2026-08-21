/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoLocalhostTest {
    @Test
    fun buildsOriginForIpv4Host() {
        assertEquals("http://127.0.0.1:2718", MarimoLocalhost.origin("127.0.0.1", 2718))
    }

    @Test
    fun bracketsIpv6HostInOrigin() {
        assertEquals("http://[::1]:2718", MarimoLocalhost.origin("::1", 2718))
    }

    @Test
    fun keepsBracketsThatTheHostAlreadyCarries() {
        assertEquals("http://[::1]:2718", MarimoLocalhost.origin("[::1]", 2718))
    }

    @Test
    fun bracketsIpv6HostInAuthenticatedUrl() {
        assertEquals(
            "http://[::1]:2718?access_token=abc",
            MarimoLocalhost.authenticatedUrl("::1", 2718, "abc"),
        )
    }

    @Test
    fun acceptsLoopbackHostForms() {
        assertTrue(MarimoLocalhost.isLoopbackHost("localhost"))
        assertTrue(MarimoLocalhost.isLoopbackHost("LOCALHOST"))
        assertTrue(MarimoLocalhost.isLoopbackHost("127.0.0.1"))
        assertTrue(MarimoLocalhost.isLoopbackHost("::1"))
    }

    @Test
    fun acceptsBracketedIpv6LoopbackHost() {
        assertTrue(MarimoLocalhost.isLoopbackHost("[::1]"))
    }

    @Test
    fun rejectsOtherHosts() {
        assertFalse(MarimoLocalhost.isLoopbackHost("evil.example.com"))
        assertFalse(MarimoLocalhost.isLoopbackHost("10.0.0.5"))
    }
}
