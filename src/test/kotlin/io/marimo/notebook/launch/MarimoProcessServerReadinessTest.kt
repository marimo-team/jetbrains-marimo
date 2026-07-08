/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** Prints a marimo-style URL banner, then stays alive without binding a socket. */
object BannerOnlyProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        println("URL: ${args[0]}")
        System.out.flush()
        Thread.sleep(60_000)
    }
}

class MarimoProcessServerReadinessTest : BasePlatformTestCase() {
    /**
     * marimo prints its URL banner tens of milliseconds before its socket accepts connections.
     * Readiness must track the socket, not the banner, or JCEF navigates into the gap and gets
     * ERR_CONNECTION_REFUSED. A fake process prints the banner immediately but the port only starts
     * serving after a delay, so completing at the banner would resolve far too early.
     */
    fun testAwaitReadyWaitsForSocketBindNotBanner() {
        val port = ServerSocket(0).use { it.localPort }
        val url = "http://127.0.0.1:$port"
        val bindDelayMs = 750L

        val binder = Thread {
            Thread.sleep(bindDelayMs)
            ServerSocket(port).use { server ->
                while (true) {
                    val socket = server.accept()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        flush()
                    }
                    socket.close()
                }
            }
        }
        binder.isDaemon = true
        binder.start()

        val handle = startMarimoServer(bannerCommand(url), "127.0.0.1", port, readinessTimeoutSeconds = 10)
        val start = System.nanoTime()
        val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        handle.dispose()

        assertEquals(url, readyUrl)
        assertTrue(
            "readiness completed after ${elapsedMs}ms; it must wait for the socket to bind (~${bindDelayMs}ms)",
            elapsedMs >= bindDelayMs / 2,
        )
    }

    private fun bannerCommand(url: String): GeneralCommandLine {
        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        return GeneralCommandLine(javaBin)
            .withParameters("-cp", System.getProperty("java.class.path"), BannerOnlyProcess::class.java.name, url)
    }
}
