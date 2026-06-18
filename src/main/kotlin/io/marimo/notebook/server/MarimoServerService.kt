/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import io.marimo.notebook.launch.LaunchDecision
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoServerHandle
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.SdkLauncher
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

    private val planner = LaunchPlanner(SdkLauncher())
    private val handles = ConcurrentHashMap<String, MarimoServerHandle>()

    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        val existing = handles[file.url]
        if (existing != null) return existing.awaitReady()

        val request = LaunchRequest(
            project = project,
            notebook = file,
            port = NetUtils.findAvailableSocketPort(),
        )
        val launcher = when (val decision = planner.plan(request)) {
            is LaunchDecision.Launch -> decision.launcher
            is LaunchDecision.NoInterpreter ->
                return CompletableFuture.failedFuture(NoInterpreterException(decision.message))
        }
        val handle = launcher.launch(request)
        handles[file.url] = handle
        Disposer.register(this, handle)
        return handle.awaitReady()
    }

    /** marimo CLI prefix for [file] on the planned launcher. Null when no interpreter is configured. */
    fun marimoCliPrefixFor(file: VirtualFile): List<String>? {
        val request = LaunchRequest(project = project, notebook = file, port = 0)
        val launcher = (planner.plan(request) as? LaunchDecision.Launch)?.launcher ?: return null
        return launcher.marimoCliPrefix(request)
    }

    fun release(file: VirtualFile) {
        handles.remove(file.url)?.let(Disposer::dispose)
    }

    override fun dispose() {
        handles.values.forEach(Disposer::dispose)
        handles.clear()
    }
}
