package com.github.kirangadhave.marimopycharm.launch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class UvLauncher : MarimoLauncher {
    override val id = "uv"

    override fun canLaunch(request: LaunchRequest): Boolean = findUv() != null

    override fun launch(request: LaunchRequest): MarimoServerHandle {
        val uv = findUv() ?: throw NoApplicableLauncherException(request)
        val workDir = request.notebook.parent?.path ?: System.getProperty("user.dir")
        val cmd = buildCommandLine(uv, request.notebook.path, workDir, request.host, request.port)
        val handler = OSProcessHandler(cmd)
        val url = expectedUrl(request.host, request.port)
        val ready = CompletableFuture<String>()

        // Race two readiness signals: marimo's stdout banner and an HTTP poll. Whichever wins resolves.
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (event.text.contains(url) || event.text.contains("URL:")) ready.complete(url)
            }
        })
        handler.startNotify()
        pollUntilUp(url, ready)
        return UvServerHandle(handler, ready)
    }

    private fun pollUntilUp(url: String, ready: CompletableFuture<String>) {
        Thread {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
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

    /**
     * GUI-launched IDEs on macOS inherit a minimal PATH that excludes Homebrew (/opt/homebrew/bin),
     * the uv installer dir (~/.local/bin), and /usr/local/bin — so a PATH-only lookup misses uv that
     * the user's shell can see. Fall back to the well-known install locations before giving up.
     */
    private fun findUv(): String? {
        com.intellij.execution.configurations.PathEnvironmentVariableUtil
            .findExecutableInPathOnAnyOS("uv")?.let { return it.absolutePath }

        val home = System.getProperty("user.home")
        return FALLBACK_UV_PATHS
            .map { it.replaceFirst("~", home) }
            .map(::File)
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    companion object {
        fun buildCommandLine(
            uvPath: String, notebookPath: String, workDir: String, host: String, port: Int,
        ): GeneralCommandLine = GeneralCommandLine(uvPath)
            .withWorkDirectory(workDir)
            .withParameters(
                "run", "--with", "marimo", "marimo", "edit", notebookPath,
                "--headless", "--host", host, "--port", port.toString(), "--no-token",
            )

        fun expectedUrl(host: String, port: Int) = "http://$host:$port"

        private val FALLBACK_UV_PATHS = listOf(
            "~/.local/bin/uv",
            "/opt/homebrew/bin/uv",
            "/usr/local/bin/uv",
        )
    }
}

private class UvServerHandle(
    override val processHandle: OSProcessHandler,
    private val ready: CompletableFuture<String>,
) : MarimoServerHandle {
    override fun awaitReady(): CompletableFuture<String> = ready
    override fun dispose() {
        processHandle.destroyProcess()
    }
}
