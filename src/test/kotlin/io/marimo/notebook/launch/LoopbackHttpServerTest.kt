/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoopbackHttpServerTest {
    @Test
    fun closeBeforeBindDoesNotListen() {
        val bindGate = CountDownLatch(1)
        DelayedLoopbackHttpServer(bindGate = bindGate) {}
            .use { server ->
                server.close()
                bindGate.countDown()
                assertFalse(server.bound.await(2, TimeUnit.SECONDS))
                ServerSocket(server.port).use { socket ->
                    assertEquals(server.port, socket.localPort)
                }
            }
    }
}
