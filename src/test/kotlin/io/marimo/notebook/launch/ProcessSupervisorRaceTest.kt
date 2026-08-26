/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/** Writes its PID to a marker file, then blocks until the process is destroyed. */
object LongRunningFallbackProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val markerPath = args[0]
        require(markerPath.isNotBlank()) { "marker path is required" }
        File(markerPath).writeText(ProcessHandle.current().pid().toString())
        Thread.sleep(Long.MAX_VALUE)
    }
}

class ProcessSupervisorRaceTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    fun testDisposeDuringBlockedFallbackPreventsSpawningTheSecondProcess() {
        val port = ServerSocket(0).use { it.localPort }
        val markerDir = FileUtil.createTempDirectory("marimo-fallback-race-", null)
        val marker = File(markerDir, "fallback.pid")
        assertFalse("marker must not exist before the fallback starts", marker.exists())
        val fallbackEntered = CountDownLatch(1)
        val releaseFallback = CountDownLatch(1)

        val handle =
            startMarimoServer(
                watchCommand(port),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 5,
                watchFallbackCmd = {
                    fallbackEntered.countDown()
                    assertTrue(
                        "fallback must block until the test releases it",
                        releaseFallback.await(5, TimeUnit.SECONDS),
                    )
                    fallbackCommand(marker.absolutePath)
                },
                authenticatedUrl = "http://127.0.0.1:$port?access_token=race",
            )

        assertTrue(
            "watch fallback must start before dispose",
            fallbackEntered.await(5, TimeUnit.SECONDS),
        )
        handle.dispose()
        releaseFallback.countDown()

        assertFalse(
            "dispose during blocked fallback must prevent spawning a second process",
            marker.exists(),
        )
    }

    fun testFallbackSpawnFailureCompletesReadinessExceptionally() {
        val port = ServerSocket(0).use { it.localPort }
        val failure = IllegalStateException("fallback spawn failed")
        val handle =
            startMarimoServer(
                watchCommand(port),
                "127.0.0.1",
                port,
                readinessTimeoutSeconds = 5,
                watchFallbackCmd = { throw failure },
            )

        try {
            handle.awaitReady().get(5, TimeUnit.SECONDS)
            fail("readiness must fail when the fallback command cannot be built")
        } catch (e: ExecutionException) {
            assertTrue(e.cause === failure)
        } finally {
            handle.dispose()
        }
    }

    private fun watchCommand(port: Int): GeneralCommandLine =
        javaProcess(WatchPickyProcess::class.java.name, port.toString(), "watch")

    private fun fallbackCommand(markerPath: String): GeneralCommandLine =
        javaProcess(LongRunningFallbackProcess::class.java.name, markerPath)
}
