/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals

/**
 * First attempt: rejects `--watch` the way marimo before 0.10 does. Second attempt (no `watch`
 * argument): prints the banner and serves.
 */
object WatchPickyProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        if (args.contains("watch")) {
            System.err.println("Usage: marimo edit [OPTIONS]\nError: No such option: --watch")
            System.err.flush()
            System.exit(2)
        }
        println("        ➜  URL: http://127.0.0.1:$port?access_token=fallbackT")
        System.out.flush()
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
}

class MarimoProcessServerWatchRetryTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    fun testUnsupportedWatchRetriesOnceAndDeliversTheSuppliedUrl() {
        val port = ServerSocket(0).use { it.localPort }
        val supplied = "http://127.0.0.1:$port?access_token=fallbackT"
        val handle =
            startMarimoServer(
                command(port, watch = true),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 5,
                watchFallbackCmd = { command(port, watch = false) },
                authenticatedUrl = supplied,
            )
        val readyUrl = handle.awaitReady().get(5, TimeUnit.SECONDS)
        handle.dispose()
        assertEquals(supplied, readyUrl)
    }

    private fun command(port: Int, watch: Boolean): GeneralCommandLine {
        val args = mutableListOf(WatchPickyProcess::class.java.name, port.toString())
        if (watch) args.add("watch")
        return javaProcess(*args.toTypedArray())
    }
}
