/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackHttpServerTest {
    @Test
    fun ephemeralServerAcceptsALoopbackClient() {
        val accepted = CountDownLatch(1)
        LoopbackHttpServer { accepted.countDown() }
            .use { server ->
                Socket(InetAddress.getLoopbackAddress(), server.port).use {}
                assertTrue(accepted.await(2, TimeUnit.SECONDS))
            }
    }

    @Test
    fun delayedBindAcceptsALoopbackClient() {
        val bindGate = CountDownLatch(1)
        val accepted = CountDownLatch(1)
        DelayedLoopbackHttpServer(bindGate = bindGate) { accepted.countDown() }
            .use { server ->
                bindGate.countDown()
                assertTrue(server.bound.await(2, TimeUnit.SECONDS))
                Socket(InetAddress.getLoopbackAddress(), server.port).use {}
                assertTrue(accepted.await(2, TimeUnit.SECONDS))
            }
    }

    @Test
    fun closeBeforeBindDoesNotListen() {
        val bindGate = CountDownLatch(1)
        DelayedLoopbackHttpServer(bindGate = bindGate) {}
            .use { server ->
                server.close()
                bindGate.countDown()
                assertFalse(server.bound.await(2, TimeUnit.SECONDS))
                bindLoopback(server.port).use { socket ->
                    assertEquals(server.port, socket.localPort)
                    assertTrue(socket.inetAddress.isLoopbackAddress)
                }
            }
    }
}
