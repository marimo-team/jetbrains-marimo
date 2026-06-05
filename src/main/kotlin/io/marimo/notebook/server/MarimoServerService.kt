/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.LauncherRegistry
import io.marimo.notebook.launch.MarimoServerHandle
import io.marimo.notebook.launch.SdkLauncher
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.launch.expectedMarimoUrl
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.net.NetUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class MarimoServerService(private val project: Project) : Disposable {

    private val registry = LauncherRegistry(listOf(SdkLauncher(), UvLauncher()))
    private val handles = ConcurrentHashMap<String, MarimoServerHandle>()
    private val baseUrls = ConcurrentHashMap<String, String>()

    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        val existing = handles[file.url]
        if (existing != null) return existing.awaitReady()

        val request = LaunchRequest(
            project = project,
            notebook = file,
            port = NetUtils.findAvailableSocketPort(),
        )
        baseUrls[file.url] = expectedMarimoUrl(request.host, request.port)
        val launcher = registry.resolve(request)
        val handle = launcher.launch(request)
        handles[file.url] = handle
        Disposer.register(this, handle)
        return handle.awaitReady()
    }

    /** Base URL of the running server for [file], or null if none has been started. */
    fun baseUrlFor(file: VirtualFile): String? = baseUrls[file.url]

    /** marimo CLI prefix for [file], re-resolving the applicable launcher. Null if none applies. */
    fun marimoCliPrefixFor(file: VirtualFile): List<String>? {
        val request = LaunchRequest(project = project, notebook = file, port = 0)
        val launcher = runCatching { registry.resolve(request) }.getOrNull() ?: return null
        return launcher.marimoCliPrefix(request)
    }

    fun release(file: VirtualFile) {
        handles.remove(file.url)?.let(Disposer::dispose)
        baseUrls.remove(file.url)
    }

    override fun dispose() {
        handles.values.forEach(Disposer::dispose)
        handles.clear()
        baseUrls.clear()
    }
}
