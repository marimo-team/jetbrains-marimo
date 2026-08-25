/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private const val MARIMO_PAGE_BODY = """<html><marimo-user-config data-config="{}"></html>"""

private fun httpResponse(statusLine: String, body: String = ""): ByteArray {
    if (body.isEmpty()) {
        return "HTTP/1.1 $statusLine\r\nContent-Length: 0\r\n\r\n".toByteArray()
    }
    return "HTTP/1.1 $statusLine\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body"
        .toByteArray()
}

/** Prints a marimo-style banner, serves one HTTP response, then exits with the requested code. */
object ServeThenExitProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        val exitCode = args[1].toInt()
        println("        ➜  URL: http://127.0.0.1:$port?access_token=SECRETTOKEN")
        System.out.flush()
        ServerSocket(port).use { server ->
            val socket = server.accept()
            socket.getOutputStream().apply {
                write(httpResponse("200 OK", MARIMO_PAGE_BODY))
                flush()
            }
            socket.close()
        }
        println("marimo shutting down")
        System.out.flush()
        System.exit(exitCode)
    }
}

class MarimoProcessServerExitTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    fun testExitAfterReadinessIsReportedOnceWithRedactedTail() {
        val port = ServerSocket(0).use { it.localPort }
        val authUrl = "http://127.0.0.1:$port?access_token=SECRETTOKEN"
        val handle =
            startMarimoServer(
                command(port, exitCode = 3),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 5,
                authenticatedUrl = authUrl,
            )

        val reported = CountDownLatch(1)
        val calls = AtomicInteger()
        val code = AtomicInteger(-1)
        var tail = ""
        handle.onTerminated { exitCode, outputTail ->
            calls.incrementAndGet()
            code.set(exitCode)
            tail = outputTail
            reported.countDown()
        }

        val readyUrl = handle.awaitReady().get(5, TimeUnit.SECONDS)
        assertEquals(
            "readiness must deliver the plugin-supplied authenticated URL",
            authUrl,
            readyUrl,
        )
        assertTrue("process exit was never reported", reported.await(5, TimeUnit.SECONDS))
        Thread.sleep(300)

        assertEquals(1, calls.get())
        assertEquals(3, code.get())
        assertTrue(
            "output tail should carry process output, was '$tail'",
            tail.contains("shutting down"),
        )
        assertFalse(
            "retained output must never carry the token: '$tail'",
            tail.contains("SECRETTOKEN"),
        )
        assertTrue(tail.contains("<redacted-token>"))
        assertFalse(tail.contains("access_token"))
        assertFalse("handle must report dead after exit", handle.isAlive)
    }

    private fun command(port: Int, exitCode: Int): GeneralCommandLine =
        javaProcess(ServeThenExitProcess::class.java.name, port.toString(), exitCode.toString())
}
