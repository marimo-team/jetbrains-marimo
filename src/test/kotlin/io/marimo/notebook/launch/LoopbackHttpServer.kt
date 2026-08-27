/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/** Serves HTTP on a loopback port until [close], so the accept thread cannot outlive the test. */
internal class LoopbackHttpServer(handler: (Socket) -> Unit) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int = server.localPort

    init {
        Thread { acceptLoop(server, handler) }
            .apply {
                isDaemon = true
                start()
            }
    }

    override fun close() {
        server.close()
    }
}

/**
 * Frees [port], waits [bindDelayMs], then binds and serves until [close]. [bound] counts down once
 * the delayed socket is listening.
 */
internal class DelayedLoopbackHttpServer(
    bindDelayMs: Long,
    handler: (Socket) -> Unit,
) : AutoCloseable {
    val port: Int = ServerSocket(0).use { it.localPort }
    val bound = CountDownLatch(1)
    private val live = AtomicReference<ServerSocket?>()

    init {
        Thread {
            try {
                if (bindDelayMs > 0) Thread.sleep(bindDelayMs)
                ServerSocket(port).use { server ->
                    live.set(server)
                    bound.countDown()
                    acceptLoop(server, handler)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // Closed before bind, or the test finished.
            } finally {
                live.set(null)
            }
        }
            .apply {
                isDaemon = true
                start()
            }
    }

    override fun close() {
        live.getAndSet(null)?.close()
    }
}

private fun acceptLoop(server: ServerSocket, handler: (Socket) -> Unit) {
    while (!server.isClosed) {
        try {
            server.accept().use(handler)
        } catch (_: Exception) {
            if (server.isClosed) return
        }
    }
}
