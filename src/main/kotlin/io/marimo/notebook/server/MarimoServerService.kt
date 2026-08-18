/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import io.marimo.notebook.editor.MarimoNotebookView
import io.marimo.notebook.launch.LaunchDecision
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoNotebookLifecycle
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.SdkLauncher
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.launch.UvUnavailableException
import io.marimo.notebook.launch.authenticatedMarimoUrl
import io.marimo.notebook.launch.generateAccessToken
import io.marimo.notebook.launch.writeTokenPasswordFile
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.net.NetUtils
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The project's notebook session manager. One [MarimoNotebookSession] per file owns that notebook's
 * marimo process, JCEF view, launch mode, and editor-attachment count. Editor tabs attach to and
 * detach from sessions; they never own the process. Status reads are side-effect-free, so painting
 * an icon or updating an action can never start a server.
 */
@Service(Service.Level.PROJECT)
class MarimoServerService(private val project: Project) : Disposable {

    internal var planner = LaunchPlanner(SdkLauncher(), UvLauncher())

    internal var tokenPasswordFileWriter: (String) -> File = ::writeTokenPasswordFile

    internal var ttlScheduler = TtlScheduler { delayMillis, task ->
        val future = AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(task, delayMillis, TimeUnit.MILLISECONDS)
        TtlCancellable { future.cancel(false) }
    }

    internal var clock: () -> Long = System::currentTimeMillis

    private val sessions = ConcurrentHashMap<String, MarimoNotebookSession>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val projectViewRefreshQueued = AtomicBoolean(false)

    /**
     * The per-file view (browser + panel) that editor tabs render. One per notebook, shared across
     * every tab showing it, so moving a notebook between splits reuses the live view instead of
     * tearing down and relaunching marimo.
     */
    fun viewFor(file: VirtualFile): MarimoNotebookView {
        val session = sessionFor(file)
        synchronized(session) {
            session.view?.let { return it }
            val view = MarimoNotebookView(project, file)
            session.view = view
            Disposer.register(session, view)
            return view
        }
    }

    /** The server lifecycle retained for this notebook across editor reopenings. Creates a session. */
    fun lifecycleFor(file: VirtualFile): MarimoNotebookLifecycle = sessionFor(file).lifecycle

    /**
     * The URL the notebook editor must load, launching a server when none is alive. The value is
     * marimo's authenticated startup URL and carries the access token: it may reach only the
     * browser, the page-config fetch, and the pair harness — never status objects or logs.
     */
    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        val session = sessionFor(file)
        synchronized(session) {
            session.lifecycle.liveHandle()?.let { return it.awaitReady() }

            val port = NetUtils.findAvailableSocketPort()
            val host = "127.0.0.1"
            val baseRequest = LaunchRequest(
                project = project,
                notebook = file,
                port = port,
                host = host,
                sandbox = session.sandboxEnabled.get(),
            )
            val launcher = try {
                when (val decision = planner.plan(baseRequest)) {
                    is LaunchDecision.Launch -> decision.launcher
                    is LaunchDecision.NoInterpreter ->
                        return launchPlanFailure(session, NoInterpreterException(decision.message))
                    is LaunchDecision.NeedsUv ->
                        return launchPlanFailure(session, UvUnavailableException(decision.message))
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                return launchPlanFailure(session, e)
            }

            var tokenFile: File? = null
            try {
                val token = MarimoSessionSettings.getInstance().state.tokenAuthEnabled
                    .takeIf { it }
                    ?.let { generateAccessToken() }
                tokenFile = token?.let(tokenPasswordFileWriter)
                val request = baseRequest.copy(
                    tokenPasswordFile = tokenFile?.absolutePath,
                    authenticatedUrl = token?.let { authenticatedMarimoUrl(host, port, it) },
                )
                val handle = launcher.launch(request)
                session.launchContext = MarimoLaunchContext(
                    port = request.port,
                    launcherId = launcher.id,
                    sandbox = request.sandbox,
                )
                Disposer.register(session.lifecycle, handle)
                session.lifecycle.attach(handle)
                return handle.awaitReady()
            } catch (e: ProcessCanceledException) {
                tokenFile?.delete()
                throw e
            } catch (e: Exception) {
                tokenFile?.delete()
                return launchPlanFailure(session, e)
            }
        }
    }

    /** marimo CLI prefix for [file] on the planned launcher. Null when no interpreter is configured. */
    fun marimoCliPrefixFor(file: VirtualFile): List<String>? {
        val request = LaunchRequest(project = project, notebook = file, port = 0)
        val launcher = (planner.plan(request) as? LaunchDecision.Launch)?.launcher ?: return null
        return launcher.marimoCliPrefix(request)
    }

    /** An editor tab now shows [file]. EDT. */
    fun attach(file: VirtualFile) {
        val session = sessions[file.url] ?: return
        synchronized(session) {
            session.attachedTabs++
            cancelTtlLocked(session)
        }
        notifySessionsChanged()
    }

    /** An editor tab showing [file] closed. The final detach arms the background TTL: the session
     * (process, browser, kernel state) stays alive so a reopen within the window reconnects
     * instantly, and the timer bounds how long an abandoned notebook holds a Python process.
     */
    fun detach(file: VirtualFile) {
        val session = sessions[file.url] ?: return
        synchronized(session) {
            if (session.attachedTabs > 0) session.attachedTabs--
            if (session.attachedTabs == 0) armTtlLocked(file.url, session)
        }
        notifySessionsChanged()
    }

    /** Side-effect-free status: null when the notebook has no session. Never creates one. */
    fun statusFor(file: VirtualFile): MarimoSessionSnapshot? = statusForUrl(file.url)

    fun statusForUrl(url: String): MarimoSessionSnapshot? =
        sessions[url]?.let { synchronized(it) { it.snapshot() } }

    /** All sessions, for the Sessions tool window. */
    fun sessions(): List<MarimoSessionSnapshot> =
        sessions.values.map { synchronized(it) { it.snapshot() } }

    /**
     * Stops [file]'s server now. With an attached tab the session entry stays so the tab renders a
     * stopped panel with a Restart offer; with no tab the whole entry is removed and disposed.
     */
    fun stop(file: VirtualFile) = stopUrl(file.url)

    fun stopUrl(url: String) {
        val session = sessions[url] ?: return
        val removed = synchronized(session) {
            cancelTtlLocked(session)
            if (session.attachedTabs == 0 && sessions.remove(url, session)) {
                session.lifecycle.release()
                true
            } else {
                session.lifecycle.stop()
                false
            }
        }
        if (removed) disposeSessionOnEdt(session)
        notifySessionsChanged()
    }

    /**
     * Stops the old server and starts a fresh one. The relaunch builds a new [LaunchRequest], so the
     * port, the interpreter decision, the sandbox mode, and the working directory are recomputed,
     * and marimo generates a new token. A background session keeps its background nature: the TTL
     * is re-armed for the fresh process.
     */
    fun restart(file: VirtualFile) {
        val session = sessions[file.url] ?: return
        val view = synchronized(session) { session.view }
        if (view != null) {
            onEdt { view.reload() }
        } else {
            session.lifecycle.release()
            ApplicationManager.getApplication().executeOnPooledThread { urlFor(file) }
        }
        synchronized(session) {
            if (session.attachedTabs == 0) armTtlLocked(file.url, session)
        }
        notifySessionsChanged()
    }

    /** Route this notebook through marimo's sandbox (uv) on its next launch and thereafter. */
    fun enableSandbox(file: VirtualFile) {
        if (sessionFor(file).sandboxEnabled.compareAndSet(false, true)) {
            MarimoTelemetry.getInstance().capture(TelemetryEvent.SandboxStarted)
        }
    }

    /** Whether [file] is currently routed through marimo's sandbox (uv). Never creates a session. */
    fun isSandbox(file: VirtualFile): Boolean =
        sessions[file.url]?.sandboxEnabled?.get() ?: false

    /** Internal teardown used by the view's relaunch path: kill the process, keep the session. */
    fun release(file: VirtualFile) {
        sessions[file.url]?.lifecycle?.release()
    }

    /** [listener] runs after any session change. It must only schedule work, not block. */
    fun addSessionsListener(parent: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    override fun dispose() {
        sessions.values.forEach { session ->
            synchronized(session) { cancelTtlLocked(session) }
            Disposer.dispose(session)
        }
        sessions.clear()
    }

    private fun launchPlanFailure(
        session: MarimoNotebookSession,
        error: Exception,
    ): CompletableFuture<String> {
        session.lifecycle.onLaunchPlanFailed(error)
        return CompletableFuture.failedFuture(error)
    }

    private fun cancelTtlLocked(session: MarimoNotebookSession) {
        session.ttlGeneration++
        session.ttl?.cancel()
        session.ttl = null
        session.expiresAtMillis = null
    }

    private fun armTtlLocked(url: String, session: MarimoNotebookSession) {
        cancelTtlLocked(session)
        val generation = ++session.ttlGeneration
        session.expiresAtMillis = clock() + BACKGROUND_TTL_MILLIS
        session.ttl = ttlScheduler.schedule(BACKGROUND_TTL_MILLIS) { onTtlExpired(url, session, generation) }
    }

    /**
     * Runs on the scheduler thread. Everything is re-validated under the session lock: the timer
     * may have been cancelled after firing, a tab may have reattached, or Stop may have removed the
     * session already. `remove(url, session)` makes the disposal single-shot.
     */
    private fun onTtlExpired(url: String, session: MarimoNotebookSession, generation: Long) {
        val expired = synchronized(session) {
            session.ttlGeneration == generation &&
                session.attachedTabs == 0 &&
                sessions.remove(url, session)
        }
        if (!expired) return
        session.lifecycle.release()
        disposeSessionOnEdt(session)
        notifySessionsChanged()
    }

    private fun disposeSessionOnEdt(session: MarimoNotebookSession) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread || application.isUnitTestMode) {
            Disposer.dispose(session)
        } else {
            application.invokeLater { Disposer.dispose(session) }
        }
    }

    private fun sessionFor(file: VirtualFile): MarimoNotebookSession =
        sessions.computeIfAbsent(file.url) {
            MarimoNotebookSession(file.url, file.name).also { session ->
                Disposer.register(this, session)
                session.lifecycle.addListener { notifySessionsChanged() }
            }
        }

    private fun notifySessionsChanged() {
        listeners.forEach { it() }
        if (ApplicationManager.getApplication().isUnitTestMode) return
        if (!projectViewRefreshQueued.compareAndSet(false, true)) return
        onEdt {
            projectViewRefreshQueued.set(false)
            if (!project.isDisposed) ProjectView.getInstance(project).refresh()
        }
    }

    /** Runs [block] now when already on the EDT, else schedules it there. */
    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) block() else application.invokeLater {
            if (!project.isDisposed) block()
        }
    }

    companion object {
        /** How long a session survives after its final editor tab closes. */
        const val BACKGROUND_TTL_MILLIS: Long = 30L * 60 * 1000
    }
}
