/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch

internal fun bindLoopback(port: Int = 0): ServerSocket =
    ServerSocket(port, 0, InetAddress.getLoopbackAddress())

/** Serves HTTP on a loopback port until [close], so the accept thread cannot outlive the test. */
internal class LoopbackHttpServer(handler: (Socket) -> Unit) : AutoCloseable {
    private val server = bindLoopback()
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
 * Frees [port], waits [bindGate] and [bindDelayMs], then binds and serves until [close]. [bound]
 * counts down once the delayed socket is listening.
 */
internal class DelayedLoopbackHttpServer(
    bindDelayMs: Long = 0L,
    bindGate: CountDownLatch? = null,
    handler: (Socket) -> Unit,
) : AutoCloseable {
    val port: Int = bindLoopback().use { it.localPort }
    val bound = CountDownLatch(1)
    private val lock = Any()
    private var closed = false
    private var live: ServerSocket? = null

    init {
        Thread {
            try {
                bindGate?.await()
                if (bindDelayMs > 0) Thread.sleep(bindDelayMs)
                val server =
                    synchronized(lock) {
                        if (closed) return@Thread
                        bindLoopback(port).also { live = it }
                    }
                bound.countDown()
                try {
                    acceptLoop(server, handler)
                } finally {
                    server.close()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // Closed before bind, or the test finished.
            } finally {
                synchronized(lock) { live = null }
            }
        }
            .apply {
                isDaemon = true
                start()
            }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            live?.close()
            live = null
        }
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
