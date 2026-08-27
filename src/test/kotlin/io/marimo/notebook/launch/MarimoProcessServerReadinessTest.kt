/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/** Prints a marimo-style URL banner, then stays alive without binding a socket. */
object BannerOnlyProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        println("        ➜  URL: ${args[0]}")
        System.out.flush()
        Thread.sleep(60_000)
    }
}

/** Binds and serves immediately, then prints the banner only after [bannerGatePath] exists. */
object ServeThenBannerProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        val bannerGatePath = args[1]
        val banner = args[2]
        Thread {
            ServerSocket(port).use { server ->
                while (true) {
                    val socket = server.accept()
                    socket.getOutputStream().apply {
                        write(httpResponse("200 OK", MARIMO_PAGE_BODY))
                        flush()
                    }
                    socket.close()
                }
            }
        }
            .apply { isDaemon = true }
            .start()
        if (banner.isNotEmpty()) {
            val gate = File(bannerGatePath)
            while (!gate.exists()) {
                Thread.sleep(20)
            }
            println("        ➜  URL: $banner")
            System.out.flush()
        }
        Thread.sleep(60_000)
    }
}

/**
 * Binds and answers 401 to every request, then prints the banner. Auth-on marimo looks like this.
 */
object UnauthorizedServerProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        Thread {
            ServerSocket(port).use { server ->
                while (true) {
                    val socket = server.accept()
                    socket.getOutputStream().apply {
                        write(httpResponse("401 Unauthorized", MARIMO_PAGE_BODY))
                        flush()
                    }
                    socket.close()
                }
            }
        }
            .apply { isDaemon = true }
            .start()
        println("        ➜  URL: http://127.0.0.1:$port?access_token=T")
        System.out.flush()
        Thread.sleep(60_000)
    }
}

/** Accepts connections but never writes a response body. */
object StallAfterAcceptProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        Thread {
            ServerSocket(port).use { server ->
                while (true) {
                    server.accept()
                }
            }
        }
            .apply { isDaemon = true }
            .start()
        Thread.sleep(60_000)
    }
}

class MarimoProcessServerReadinessTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    /** A generic HTTP listener on the port must not count as a ready marimo server. */
    fun testUnrelatedHttpListenerDoesNotCountAsReady() {
        LoopbackHttpServer { socket ->
            socket.getOutputStream().apply {
                write(httpResponse("200 OK"))
                flush()
            }
        }
            .use { listener ->
                val url = "http://127.0.0.1:${listener.port}"
                val handle =
                    startMarimoServer(
                        javaProcess(BannerOnlyProcess::class.java.name, url),
                        "127.0.0.1",
                        listener.port,
                        readinessTimeoutSeconds = 2,
                    )
                try {
                    handle.awaitReady().get(5, TimeUnit.SECONDS)
                    fail("readiness must fail when the listener does not serve a marimo page")
                } catch (e: ExecutionException) {
                    assertTrue(e.cause is IOException)
                } finally {
                    handle.dispose()
                }
            }
    }

    /**
     * marimo prints its URL banner before its socket serves the page. Readiness must track the
     * marimo page, not the banner, or JCEF navigates into the gap and gets ERR_CONNECTION_REFUSED.
     */
    fun testAwaitReadyWaitsForMarimoPageNotBanner() {
        DelayedLoopbackHttpServer(bindDelayMs = 750L) { socket ->
                socket.getOutputStream().apply {
                    write(httpResponse("200 OK", MARIMO_PAGE_BODY))
                    flush()
                }
            }
            .use { listener ->
                val url = "http://127.0.0.1:${listener.port}"
                val handle =
                    startMarimoServer(
                        javaProcess(BannerOnlyProcess::class.java.name, url),
                        "127.0.0.1",
                        listener.port,
                        readinessTimeoutSeconds = 10,
                    )
                val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
                handle.dispose()

                assertEquals(url, readyUrl)
                assertEquals(
                    "readiness must observe the delayed marimo page, not the banner",
                    0L,
                    listener.bound.count,
                )
            }
    }

    /** When the plugin supplies the URL, readiness must not wait for a delayed banner. */
    fun testSuppliedAuthenticatedUrlDoesNotWaitForTheBanner() {
        val port = ServerSocket(0).use { it.localPort }
        val supplied = "http://127.0.0.1:$port?access_token=supplied"
        val bannerGate = File.createTempFile("marimo-banner-gate-", ".txt")
        check(bannerGate.delete())
        val handle =
            startMarimoServer(
                javaProcess(
                    ServeThenBannerProcess::class.java.name,
                    port.toString(),
                    bannerGate.absolutePath,
                    "http://127.0.0.1:$port?access_token=late",
                ),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 10,
                authenticatedUrl = supplied,
            )
        try {
            val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
            assertEquals(supplied, readyUrl)
            assertFalse("readiness must finish before the banner gate exists", bannerGate.exists())
        } finally {
            handle.dispose()
        }
    }

    fun testDirectHandleDisposeDeletesTheTokenPasswordFile() {
        val port = ServerSocket(0).use { it.localPort }
        val tokenFile = File.createTempFile("marimo-token-test-", ".txt")
        tokenFile.writeText("secret")
        val handle =
            startMarimoServer(
                javaProcess(BannerOnlyProcess::class.java.name, "http://127.0.0.1:$port"),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 10,
                tokenPasswordFile = tokenFile.absolutePath,
            )

        handle.dispose()

        assertFalse("direct handle disposal must own token-file cleanup", tokenFile.exists())
    }

    /** A 401 with a marimo page body still counts as ready when the probe can read the page. */
    fun testMarimoPageBodyCountsAsReadyEvenWith401() {
        val port = ServerSocket(0).use { it.localPort }
        val supplied = "http://127.0.0.1:$port?access_token=T"
        val handle =
            startMarimoServer(
                javaProcess(UnauthorizedServerProcess::class.java.name, port.toString()),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 10,
                authenticatedUrl = supplied,
            )
        val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
        handle.dispose()
        assertEquals(supplied, readyUrl)
    }

    /** With token auth off, readiness delivers the plain URL without a banner. */
    fun testPlainUrlWhenNoAuthenticatedUrlIsSupplied() {
        val port = ServerSocket(0).use { it.localPort }
        val handle =
            startMarimoServer(
                javaProcess(ServeThenBannerProcess::class.java.name, port.toString(), "", ""),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 2,
            )
        val readyUrl = handle.awaitReady().get(10, TimeUnit.SECONDS)
        handle.dispose()
        assertEquals("http://127.0.0.1:$port", readyUrl)
    }

    fun testStalledResponseDoesNotCountAsReady() {
        val port = ServerSocket(0).use { it.localPort }
        val handle =
            startMarimoServer(
                javaProcess(StallAfterAcceptProcess::class.java.name, port.toString()),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 2,
            )
        try {
            handle.awaitReady().get(5, TimeUnit.SECONDS)
            fail("readiness must fail when the server accepts but never responds")
        } catch (e: ExecutionException) {
            assertTrue(e.cause is IOException)
        } finally {
            handle.dispose()
        }
    }
}
