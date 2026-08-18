/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import io.marimo.notebook.launch.LaunchDecision
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoNotebookLifecycle
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.SdkLauncher
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.launch.UvUnavailableException
import io.marimo.notebook.editor.MarimoNotebookView
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.net.NetUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class MarimoServerService(private val project: Project) : Disposable {

    /** State that affects how one notebook's marimo server is launched and reused. */
    private class NotebookServerState {
        val lifecycle = MarimoNotebookLifecycle()
        val sandboxEnabled = AtomicBoolean(false)
        val launchLock = Any()
    }

    internal var planner = LaunchPlanner(SdkLauncher(), UvLauncher())
    private val notebookServers = ConcurrentHashMap<String, NotebookServerState>()
    private val views = ConcurrentHashMap<String, MarimoNotebookView>()

    /**
     * The per-file view (browser + panel + server) that editor tabs render. One per notebook,
     * shared across every tab showing it, so moving a notebook between splits reuses the live view
     * instead of tearing down and relaunching marimo. Disposed with the project.
     */
    fun viewFor(file: VirtualFile): MarimoNotebookView =
        views.computeIfAbsent(file.url) { MarimoNotebookView(project, file).also { Disposer.register(this, it) } }

    /** The server lifecycle retained for this notebook across editor reopenings. */
    fun lifecycleFor(file: VirtualFile): MarimoNotebookLifecycle =
        stateFor(file).lifecycle

    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        val state = stateFor(file)
        return synchronized(state.launchLock) {
            state.lifecycle.liveHandle()?.let { return@synchronized it.awaitReady() }

            val request = LaunchRequest(
                project = project,
                notebook = file,
                port = NetUtils.findAvailableSocketPort(),
                sandbox = state.sandboxEnabled.get(),
            )
            val launcher = when (val decision = planner.plan(request)) {
                is LaunchDecision.Launch -> decision.launcher
                is LaunchDecision.NoInterpreter ->
                    return@synchronized CompletableFuture.failedFuture(NoInterpreterException(decision.message))
                is LaunchDecision.NeedsUv ->
                    return@synchronized CompletableFuture.failedFuture(UvUnavailableException(decision.message))
            }
            // A launcher can fail synchronously (e.g. the process can't be spawned). Turn that into a
            // failed future so it reaches the editor's error panel instead of escaping as an IDE
            // internal-error balloon with a raw stack trace.
            val handle = try {
                launcher.launch(request)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                return@synchronized CompletableFuture.failedFuture(e)
            }
            Disposer.register(state.lifecycle, handle)
            state.lifecycle.attach(handle)
            handle.awaitReady()
        }
    }

    /** marimo CLI prefix for [file] on the planned launcher. Null when no interpreter is configured. */
    fun marimoCliPrefixFor(file: VirtualFile): List<String>? {
        val request = LaunchRequest(project = project, notebook = file, port = 0)
        val launcher = (planner.plan(request) as? LaunchDecision.Launch)?.launcher ?: return null
        return launcher.marimoCliPrefix(request)
    }

    /** Route this notebook through marimo's sandbox (uv) on its next launch and thereafter. */
    fun enableSandbox(file: VirtualFile) {
        if (stateFor(file).sandboxEnabled.compareAndSet(false, true)) {
            MarimoTelemetry.getInstance().capture(TelemetryEvent.SandboxStarted)
        }
    }

    /** Whether [file] is currently routed through marimo's sandbox (uv). */
    fun isSandbox(file: VirtualFile): Boolean = notebookServers[file.url]?.sandboxEnabled?.get() ?: false

    fun release(file: VirtualFile) {
        notebookServers[file.url]?.let { state ->
            synchronized(state.launchLock) { state.lifecycle.release() }
        }
    }

    override fun dispose() {
        notebookServers.values.forEach { Disposer.dispose(it.lifecycle) }
        notebookServers.clear()
        views.clear()
    }

    private fun stateFor(file: VirtualFile): NotebookServerState =
        notebookServers.computeIfAbsent(file.url) {
            NotebookServerState().also { Disposer.register(this, it.lifecycle) }
        }
}
