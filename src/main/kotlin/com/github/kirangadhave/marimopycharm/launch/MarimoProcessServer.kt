package com.github.kirangadhave.marimopycharm.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun expectedMarimoUrl(host: String, port: Int): String = "http://$host:$port"

/**
 * Spawns a marimo process and completes [MarimoServerHandle.awaitReady] once the server accepts
 * connections. Readiness races two signals — marimo's stdout banner and an HTTP poll — so a missed
 * banner line never hangs the tab. Shared by every process-based launcher (uv, sdk).
 */
fun startMarimoServer(
    cmd: GeneralCommandLine,
    host: String,
    port: Int,
    readinessTimeoutSeconds: Long = 30,
): MarimoServerHandle {
    val handler = OSProcessHandler(cmd)
    val url = expectedMarimoUrl(host, port)
    val ready = CompletableFuture<String>()

    handler.addProcessListener(object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            if (event.text.contains(url) || event.text.contains("URL:")) ready.complete(url)
        }
    })
    handler.startNotify()
    pollUntilUp(url, ready, readinessTimeoutSeconds)
    return ProcessMarimoServerHandle(handler, ready)
}

private fun pollUntilUp(url: String, ready: CompletableFuture<String>, timeoutSeconds: Long) {
    Thread {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!ready.isDone && System.nanoTime() < deadline) {
            try {
                HttpRequests.head(url).tryConnect()
                ready.complete(url); return@Thread
            } catch (_: IOException) {
                Thread.sleep(200)
            }
        }
        if (!ready.isDone) ready.completeExceptionally(IOException("marimo server did not start: $url"))
    }.apply { isDaemon = true }.start()
}

private class ProcessMarimoServerHandle(
    override val processHandle: OSProcessHandler,
    private val ready: CompletableFuture<String>,
) : MarimoServerHandle {
    override fun awaitReady(): CompletableFuture<String> = ready
    override fun dispose() {
        processHandle.destroyProcess()
    }
}
