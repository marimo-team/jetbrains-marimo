/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** Prints a marimo-style URL banner, then stays alive without binding a socket. */
object BannerOnlyProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        println("        ➜  URL: ${args[0]}")
        System.out.flush()
        Thread.sleep(60_000)
    }
}

/** Binds and serves immediately, then prints the banner only after a delay. */
object ServeThenBannerProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        val bannerDelayMs = args[1].toLong()
        val banner = args[2]
        Thread {
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
        }.apply { isDaemon = true }.start()
        Thread.sleep(bannerDelayMs)
        if (banner.isNotEmpty()) {
            println("        ➜  URL: $banner")
            System.out.flush()
        }
        Thread.sleep(60_000)
    }
}

/** Binds and answers 401 to every request, then prints the banner. Auth-on marimo looks like this. */
object UnauthorizedServerProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        Thread {
            ServerSocket(port).use { server ->
                while (true) {
                    val socket = server.accept()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        flush()
                    }
                    socket.close()
                }
            }
        }.apply { isDaemon = true }.start()
        println("        ➜  URL: http://127.0.0.1:$port?access_token=T")
        System.out.flush()
        Thread.sleep(60_000)
    }
}

class MarimoProcessServerReadinessTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    /**
     * marimo prints its URL banner tens of milliseconds before its socket accepts connections.
     * Readiness must track the socket, not the banner, or JCEF navigates into the gap and gets
     * ERR_CONNECTION_REFUSED.
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

        val handle = startMarimoServer(
            javaProcess(BannerOnlyProcess::class.java.name, url),
            "127.0.0.1", port, readinessTimeoutSeconds = 10,
        )
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

    /** When the plugin supplies the URL, readiness must not wait for a delayed banner. */
    fun testSuppliedAuthenticatedUrlDoesNotWaitForTheBanner() {
        val port = ServerSocket(0).use { it.localPort }
        val supplied = "http://127.0.0.1:$port?access_token=supplied"
        val handle = startMarimoServer(
            javaProcess(
                ServeThenBannerProcess::class.java.name,
                port.toString(), "750", "http://127.0.0.1:$port?access_token=late",
            ),
            "127.0.0.1", port, readinessTimeoutSeconds = 10, authenticatedUrl = supplied,
        )
        val start = System.nanoTime()
        val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        handle.dispose()

        assertEquals(supplied, readyUrl)
        assertTrue(
            "completed after ${elapsedMs}ms; it must not wait for the delayed banner (~750ms)",
            elapsedMs < 600,
        )
    }

    fun testDirectHandleDisposeDeletesTheTokenPasswordFile() {
        val port = ServerSocket(0).use { it.localPort }
        val tokenFile = File.createTempFile("marimo-token-test-", ".txt")
        tokenFile.writeText("secret")
        val handle = startMarimoServer(
            javaProcess(BannerOnlyProcess::class.java.name, "http://127.0.0.1:$port"),
            "127.0.0.1", port, readinessTimeoutSeconds = 10,
            tokenPasswordFile = tokenFile.absolutePath,
        )

        handle.dispose()

        assertFalse("direct handle disposal must own token-file cleanup", tokenFile.exists())
    }

    /** An anonymous probe of a token-protected server is not logged in; 401 still means "up". */
    fun testAnyHttpStatusCountsAsReady() {
        val port = ServerSocket(0).use { it.localPort }
        val supplied = "http://127.0.0.1:$port?access_token=T"
        val handle = startMarimoServer(
            javaProcess(UnauthorizedServerProcess::class.java.name, port.toString()),
            "127.0.0.1", port, readinessTimeoutSeconds = 10, authenticatedUrl = supplied,
        )
        val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
        handle.dispose()
        assertEquals(supplied, readyUrl)
    }

    /** With token auth off, readiness delivers the plain URL without a banner. */
    fun testPlainUrlWhenNoAuthenticatedUrlIsSupplied() {
        val port = ServerSocket(0).use { it.localPort }
        val handle = startMarimoServer(
            javaProcess(ServeThenBannerProcess::class.java.name, port.toString(), "0", ""),
            "127.0.0.1", port, readinessTimeoutSeconds = 2,
        )
        val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
        handle.dispose()
        assertEquals("http://127.0.0.1:$port", readyUrl)
    }

}
