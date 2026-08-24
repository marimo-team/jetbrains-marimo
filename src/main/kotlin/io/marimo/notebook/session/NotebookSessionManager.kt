/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

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
import io.marimo.notebook.MarimoLocalhost
import io.marimo.notebook.editor.MarimoNotebookView
import io.marimo.notebook.launch.LaunchDecision
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoLauncher
import io.marimo.notebook.launch.MarimoNotebookLifecycle
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.NotebookWorkDir
import io.marimo.notebook.launch.SdkLauncher
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.launch.UvUnavailableException
import io.marimo.notebook.launch.generateAccessToken
import io.marimo.notebook.launch.writeTokenPasswordFile
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The project's notebook session manager. One [NotebookSession] per file owns that notebook's
 * marimo process, JCEF view, launch mode, and owner leases. Editor tabs attach to and detach from
 * sessions; they never own the process. Status reads are side-effect-free, so painting an icon or
 * updating an action can never start a server.
 */
@Service(Service.Level.PROJECT)
class NotebookSessionManager(private val project: Project) : Disposable {

    internal var planner = LaunchPlanner(SdkLauncher(), UvLauncher())

    internal var tokenPasswordFileWriter: (String) -> File = ::writeTokenPasswordFile

    internal var ttlScheduler = TtlScheduler { delayMillis, task ->
        val future =
            AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(task, delayMillis, TimeUnit.MILLISECONDS)
        TtlCancellable { future.cancel(false) }
    }

    internal var clock: () -> Long = System::currentTimeMillis

    private val sessions = ConcurrentHashMap<SessionId, NotebookSession>()
    private val sessionIds = AtomicLong()
    private val sessionRegistryLock = Any()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val projectViewRefreshQueued = AtomicBoolean(false)

    /** Acquires one ownership lease for [file]'s shared notebook session. */
    fun acquire(file: VirtualFile, owner: LeaseOwner): NotebookSessionLease {
        while (true) {
            val session = sessionFor(file)
            val lease =
                synchronized(session) {
                    if (sessions[session.id] !== session) {
                        null
                    } else {
                        session.acquireLease(owner)
                        if (owner.suppressesTtl) cancelTtlLocked(session)
                        SessionLease(session.id, owner)
                    }
                }
            if (lease != null) {
                notifySessionsChanged()
                return lease
            }
        }
    }

    /**
     * The per-file view (browser + panel) that editor tabs render. One per notebook, shared across
     * every tab showing it, so moving a notebook between splits reuses the live view instead of
     * tearing down and relaunching marimo.
     */
    fun viewFor(file: VirtualFile): MarimoNotebookView {
        val session = sessionFor(file)
        synchronized(session) {
            session.view?.let {
                return it
            }
            val view = MarimoNotebookView(project, file)
            session.view = view
            Disposer.register(session, view)
            return view
        }
    }

    /**
     * Atomically gets/creates the per-file view and attaches one tab lease to the same session.
     * This prevents a TTL race where view creation and attach target different entries.
     */
    fun attachView(file: VirtualFile): Pair<SessionId, MarimoNotebookView> {
        while (true) {
            val session = sessionFor(file)
            val attachment =
                synchronized(session) {
                    if (sessions[session.id] !== session) {
                        null
                    } else {
                        val existing = session.view
                        val resolved =
                            existing
                                ?: MarimoNotebookView(project, file).also {
                                    session.view = it
                                    Disposer.register(session, it)
                                }
                        acquireOwnerLocked(session, LeaseOwner.EDITOR_TAB)
                        session.id to resolved
                    }
                }
            if (attachment != null) {
                notifySessionsChanged()
                return attachment
            }
        }
    }

    /**
     * The server lifecycle retained for this notebook across editor reopenings. Creates a session.
     */
    fun lifecycleFor(file: VirtualFile): MarimoNotebookLifecycle = sessionFor(file).lifecycle

    /**
     * The URL the notebook editor must load, launching a server when none is alive. The value is
     * marimo's authenticated startup URL and carries the access token: it may reach only the
     * browser, the page-config fetch, and the pair harness — never status objects or logs.
     */
    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        while (true) {
            val session = sessionFor(file)
            val readyUrl =
                synchronized(session) {
                    if (sessions[session.id] === session) urlForSessionLocked(session, file)
                    else null
                }
            if (readyUrl != null) return readyUrl
        }
    }

    private fun urlForSessionLocked(
        session: NotebookSession,
        file: VirtualFile,
    ): CompletableFuture<String> {
        session.lifecycle.liveHandle()?.let {
            return it.awaitReady()
        }

        val port = NetUtils.findAvailableSocketPort()
        val host = MarimoLocalhost.HOST
        val workDir = NotebookWorkDir.resolve(project, file)
        val baseRequest =
            LaunchRequest(
                project = project,
                notebook = file,
                port = port,
                host = host,
                sandbox = session.sandboxEnabled.get(),
                workDir = workDir,
            )
        val launcher =
            try {
                when (val decision = planner.plan(baseRequest)) {
                    is LaunchDecision.Launch -> decision.launcher
                    is LaunchDecision.NoInterpreter ->
                        return launchPlanFailure(
                            session,
                            NoInterpreterException(decision.message),
                        )

                    is LaunchDecision.NeedsUv ->
                        return launchPlanFailure(
                            session,
                            UvUnavailableException(decision.message),
                        )
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                return launchPlanFailure(session, e)
            }

        return launchSessionLocked(session, baseRequest, launcher)
    }

    /**
     * marimo CLI prefix for [file] on the planned launcher. Null when no interpreter is configured.
     */
    fun marimoCliPrefixFor(file: VirtualFile): List<String>? {
        val request = LaunchRequest(project = project, notebook = file, port = 0)
        val launcher = (planner.plan(request) as? LaunchDecision.Launch)?.launcher ?: return null
        return launcher.marimoCliPrefix(request)
    }

    /** An editor tab now shows [file]. EDT. */
    fun attach(file: VirtualFile) {
        val session = sessionForUrl(file.url) ?: return
        synchronized(session) {
            acquireOwnerLocked(session, LeaseOwner.EDITOR_TAB)
        }
        notifySessionsChanged()
    }

    /**
     * An editor tab showing [file] closed. The final detach arms the background TTL: the session
     * (process, browser, kernel state) stays alive so a reopen within the window reconnects
     * instantly, and the timer bounds how long an abandoned notebook holds a Python process.
     */
    fun detach(file: VirtualFile) {
        val session = sessionForUrl(file.url) ?: return
        detach(session.id)
    }

    fun detachUrl(url: String) {
        val session = sessionForUrl(url) ?: return
        detach(session.id)
    }

    internal fun detach(sessionId: SessionId) {
        releaseOwner(sessionId, LeaseOwner.EDITOR_TAB)
    }

    /** Side-effect-free status: null when the notebook has no session. Never creates one. */
    fun statusFor(file: VirtualFile): SessionSnapshot? = statusForUrl(file.url)

    fun statusForUrl(url: String): SessionSnapshot? =
        sessionForUrl(url)?.let { synchronized(it) { it.snapshot() } }

    /** All sessions, for the Sessions tool window. */
    fun sessions(): List<SessionSnapshot> =
        sessions.values.map { synchronized(it) { it.snapshot() } }

    /**
     * Stops [file]'s server now. With an attached tab the session entry stays so the tab renders a
     * stopped panel with a Restart offer; with no tab the whole entry is removed and disposed.
     */
    fun stop(file: VirtualFile) = stopUrl(file.url)

    fun stopUrl(url: String) {
        val session = sessionForUrl(url) ?: return
        stopSession(session)
    }

    private fun stopSession(session: NotebookSession) {
        val removed =
            synchronized(session) {
                if (sessions[session.id] !== session) return
                cancelTtlLocked(session)
                if (session.shouldArmTtl && sessions.remove(session.id, session)) {
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
     * Stops the old server and starts a fresh one. The relaunch builds a new [LaunchRequest], so
     * the port, the interpreter decision, the sandbox mode, and the working directory are
     * recomputed, and marimo generates a new token. A background session keeps its background
     * nature: the TTL is re-armed for the fresh process.
     */
    fun restart(file: VirtualFile) {
        val session = sessionForUrl(file.url) ?: return
        restartSession(session)
    }

    private fun restartSession(session: NotebookSession) {
        val view =
            synchronized(session) {
                if (sessions[session.id] !== session) return
                session.view
            }
        if (view != null) {
            onEdt { view.reload() }
        } else {
            session.lifecycle.release()
            ApplicationManager.getApplication().executeOnPooledThread {
                synchronized(session) {
                    if (sessions[session.id] === session) {
                        urlForSessionLocked(session, session.notebook)
                    }
                }
            }
        }
        synchronized(session) {
            if (sessions[session.id] === session && session.shouldArmTtl) {
                armTtlLocked(session.id, session)
            }
        }
        notifySessionsChanged()
    }

    /** Route this notebook through marimo's sandbox (uv) on its next launch and thereafter. */
    fun enableSandbox(file: VirtualFile) {
        if (sessionFor(file).sandboxEnabled.compareAndSet(false, true)) {
            MarimoTelemetry.getInstance().capture(TelemetryEvent.SandboxStarted)
        }
    }

    /**
     * Whether [file] is currently routed through marimo's sandbox (uv). Never creates a session.
     */
    fun isSandbox(file: VirtualFile): Boolean =
        sessionForUrl(file.url)?.sandboxEnabled?.get() ?: false

    /** Internal teardown used by the view's relaunch path: kill the process, keep the session. */
    fun release(file: VirtualFile) {
        sessionForUrl(file.url)?.lifecycle?.release()
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
        session: NotebookSession,
        error: Exception,
    ): CompletableFuture<String> {
        session.lifecycle.onLaunchPlanFailed(error)
        return CompletableFuture.failedFuture(error)
    }

    private fun launchSessionLocked(
        session: NotebookSession,
        baseRequest: LaunchRequest,
        launcher: MarimoLauncher,
    ): CompletableFuture<String> {
        var tokenFile: File? = null
        try {
            val tokenAuthEnabled = SessionSettings.getInstance().state.tokenAuthEnabled
            val token = tokenAuthEnabled.takeIf { it }?.let { generateAccessToken() }
            tokenFile = token?.let(tokenPasswordFileWriter)
            val request =
                baseRequest.copy(
                    tokenPasswordFile = tokenFile?.absolutePath,
                    authenticatedUrl =
                        token?.let {
                            MarimoLocalhost.authenticatedUrl(baseRequest.host, baseRequest.port, it)
                        },
                )
            val handle = launcher.launch(request)
            session.launchContext =
                MarimoLaunchContext(
                    port = request.port,
                    workDir = requireNotNull(request.workDir),
                    launcherId = launcher.id,
                    sandbox = request.sandbox,
                    tokenAuthEnabled = tokenAuthEnabled,
                )
            Disposer.register(session.lifecycle, handle)
            session.lifecycle.attach(handle)
            return handle.awaitReady().whenComplete { _, error ->
                if (error != null) Disposer.dispose(handle)
            }
        } catch (e: ProcessCanceledException) {
            tokenFile?.delete()
            throw e
        } catch (e: Exception) {
            tokenFile?.delete()
            return launchPlanFailure(session, e)
        }
    }

    private fun cancelTtlLocked(session: NotebookSession) {
        session.ttlGeneration++
        session.ttl?.cancel()
        session.ttl = null
        session.expiresAtMillis = null
    }

    private fun armTtlLocked(sessionId: SessionId, session: NotebookSession) {
        val ttlMillis = SessionSettings.getInstance().backgroundTtlMillis()
        cancelTtlLocked(session)
        val generation = ++session.ttlGeneration
        session.expiresAtMillis = clock() + ttlMillis
        session.ttl =
            ttlScheduler.schedule(ttlMillis) { onTtlExpired(sessionId, session, generation) }
    }

    /**
     * Runs on the scheduler thread. Everything is re-validated under the session lock: the timer
     * may have been cancelled after firing, a tab may have reattached, or Stop may have removed the
     * session already. `remove(sessionId, session)` makes the disposal single-shot.
     */
    private fun onTtlExpired(sessionId: SessionId, session: NotebookSession, generation: Long) {
        val expired =
            synchronized(session) {
                session.ttlGeneration == generation &&
                        session.shouldArmTtl &&
                        sessions.remove(sessionId, session)
            }
        if (!expired) return
        session.lifecycle.release()
        disposeSessionOnEdt(session)
        notifySessionsChanged()
    }

    private fun disposeSessionOnEdt(session: NotebookSession) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread || application.isUnitTestMode) {
            Disposer.dispose(session)
        } else {
            application.invokeLater { Disposer.dispose(session) }
        }
    }

    private fun sessionFor(file: VirtualFile): NotebookSession =
        synchronized(sessionRegistryLock) {
            sessions.values.firstOrNull { session -> session.matches(file) }
                ?: NotebookSession(SessionId(sessionIds.incrementAndGet()), file).also { session ->
                    sessions[session.id] = session
                    Disposer.register(this, session)
                    session.lifecycle.addListener { update ->
                        synchronized(session) {
                            if (
                                update.state !is MarimoNotebookState.Starting &&
                                update.state !is MarimoNotebookState.Running
                            ) {
                                session.launchContext = null
                            }
                        }
                        notifySessionsChanged()
                    }
                }
        }

    private fun sessionForUrl(url: String): NotebookSession? =
        sessions.values.firstOrNull { session -> session.fileUrl == url }

    private fun acquireOwnerLocked(session: NotebookSession, owner: LeaseOwner) {
        session.acquireLease(owner)
        if (owner.suppressesTtl) cancelTtlLocked(session)
    }

    private fun releaseOwner(sessionId: SessionId, owner: LeaseOwner) {
        val session = sessions[sessionId] ?: return
        val released =
            synchronized(session) {
                if (!session.releaseLease(owner)) {
                    false
                } else {
                    if (session.shouldArmTtl) armTtlLocked(session.id, session)
                    true
                }
            }
        if (released) notifySessionsChanged()
    }

    private inner class SessionLease(
        override val sessionId: SessionId,
        private val owner: LeaseOwner,
    ) : NotebookSessionLease {
        private val closed = AtomicBoolean(false)

        override val notebook: VirtualFile
            get() = sessionForId(sessionId).notebook

        override fun readyUrl(): CompletableFuture<String> {
            val session = sessions[sessionId] ?: return expiredLeaseFuture()
            return synchronized(session) {
                if (sessions[sessionId] !== session) expiredLeaseFuture()
                else urlForSessionLocked(session, session.notebook)
            }
        }

        override fun status(): SessionSnapshot {
            val session = sessionForId(sessionId)
            return synchronized(session) { session.snapshot() }
        }

        override fun launcherInfo(): LauncherInfo? = null

        override fun restart() {
            sessions[sessionId]?.let(::restartSession)
        }

        override fun stop() {
            sessions[sessionId]?.let(::stopSession)
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) releaseOwner(sessionId, owner)
        }

        private fun expiredLeaseFuture(): CompletableFuture<String> =
            CompletableFuture.failedFuture(
                IllegalStateException("Notebook session is no longer available")
            )
    }

    private fun sessionForId(sessionId: SessionId): NotebookSession =
        requireNotNull(sessions[sessionId]) { "Notebook session is no longer available" }

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
        if (application.isDispatchThread) block()
        else
            application.invokeLater {
                if (!project.isDisposed) block()
            }
    }
}
