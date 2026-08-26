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
import io.marimo.notebook.launch.LaunchDecision
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.launch.LaunchRequest
import io.marimo.notebook.launch.MarimoLauncher
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.NotebookLifecycle
import io.marimo.notebook.launch.NotebookWorkDir
import io.marimo.notebook.launch.SdkLauncher
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.launch.UvUnavailableException
import io.marimo.notebook.launch.generateAccessToken
import io.marimo.notebook.launch.writeTokenPasswordFile
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The project's notebook session manager. One [NotebookSession] per file owns that notebook's
 * marimo process, launch mode, and owner leases. Owners retain the session without owning its
 * process. Status reads are side-effect-free, so painting an icon or updating an action can never
 * start a server.
 */
@Service(Service.Level.PROJECT)
class NotebookSessionManager(private val project: Project) : Disposable {

    private data class PlannedLaunch(
        val request: LaunchRequest,
        val launcher: MarimoLauncher,
    )

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
    private val sessionEventListeners = CopyOnWriteArrayList<(NotebookSessionEvent) -> Unit>()
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

    /** Returns a non-owning handle only when [file] already has a session. Never creates one. */
    internal fun leaseIfPresent(file: VirtualFile): NotebookSessionLease? {
        while (true) {
            val session = sessionForUrl(file.url) ?: return null
            val lease =
                synchronized(session) {
                    if (sessions[session.id] !== session) {
                        null
                    } else {
                        SessionLease(session.id, owner = null)
                    }
                }
            if (lease != null) {
                return lease
            }
        }
    }

    /**
     * The server lifecycle retained for this notebook across editor reopenings. Creates a session.
     */
    fun lifecycleFor(file: VirtualFile): NotebookLifecycle = sessionFor(file).lifecycle

    /**
     * Returns the authenticated startup URL for [file]. If no server is live, this call starts one.
     * The URL contains an access token. Only the browser, the page-config fetch, and the pair
     * harness receive it. Status objects and logs never receive it.
     */
    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        while (true) {
            val session = sessionFor(file)
            val readyUrl =
                synchronized(session) {
                    if (sessions[session.id] === session) readyUrlForSessionLocked(session, file)
                    else null
                }
            if (readyUrl != null) return readyUrl
        }
    }

    private fun readyUrlForSessionLocked(
        session: NotebookSession,
        file: VirtualFile,
    ): CompletableFuture<String> {
        session.inFlightReadyUrl?.let {
            return it
        }
        session.lifecycle.liveHandle()?.let {
            return it.awaitReady()
        }

        val readyUrl = CompletableFuture<String>()
        session.inFlightReadyUrl = readyUrl
        readyUrl.whenComplete { _, _ ->
            synchronized(session) {
                if (session.inFlightReadyUrl === readyUrl) session.inFlightReadyUrl = null
            }
        }
        AppExecutorUtil.getAppExecutorService().execute {
            launchSessionAsync(session, file, readyUrl)
        }
        return readyUrl
    }

    private fun launchSessionAsync(
        session: NotebookSession,
        file: VirtualFile,
        readyUrl: CompletableFuture<String>,
    ) {
        var tokenFile: File? = null
        try {
            val planned = planLaunch(session, file)
            val tokenAuthEnabled = SessionSettings.getInstance().state.tokenAuthEnabled
            val token = tokenAuthEnabled.takeIf { it }?.let { generateAccessToken() }
            tokenFile = token?.let(tokenPasswordFileWriter)
            val request =
                planned.request.copy(
                    tokenPasswordFile = tokenFile?.absolutePath,
                    authenticatedUrl =
                        token?.let {
                            MarimoLocalhost.authenticatedUrl(
                                planned.request.host,
                                planned.request.port,
                                it,
                            )
                        },
                )
            val launcherInfo = launcherInfoFor(planned.launcher, request)
            val handle = planned.launcher.launch(request)
            val attachTransition =
                synchronized(session) {
                    if (sessions[session.id] !== session || session.inFlightReadyUrl !== readyUrl) {
                        null
                    } else {
                        session.launchContext =
                            MarimoLaunchContext(
                                port = request.port,
                                workDir = requireNotNull(request.workDir),
                                launcherId = planned.launcher.id,
                                launcherInfo = launcherInfo,
                                sandbox = request.sandbox,
                                tokenAuthEnabled = tokenAuthEnabled,
                            )
                        Disposer.register(session.lifecycle, handle)
                        session.lifecycle.prepareAttach(handle)
                    }
                }
            if (attachTransition == null) {
                tokenFile?.delete()
                Disposer.dispose(handle)
                readyUrl.completeExceptionally(
                    CancellationException("Notebook session is no longer available")
                )
                return
            }
            attachTransition.publish()
            completeReadyUrlFrom(handle, readyUrl)
        } catch (e: ProcessCanceledException) {
            tokenFile?.delete()
            readyUrl.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            tokenFile?.delete()
            completeLaunchFailure(session, readyUrl, e)
        }
    }

    private fun planLaunch(session: NotebookSession, file: VirtualFile): PlannedLaunch {
        val baseRequest =
            LaunchRequest(
                project = project,
                notebook = file,
                port = NetUtils.findAvailableSocketPort(),
                host = MarimoLocalhost.HOST,
                sandbox = session.sandboxEnabled.get(),
                workDir = NotebookWorkDir.resolve(project, file),
            )
        val launcher =
            when (val decision = planner.plan(baseRequest)) {
                is LaunchDecision.Launch -> decision.launcher
                is LaunchDecision.NoInterpreter -> throw NoInterpreterException(decision.message)
                is LaunchDecision.NeedsUv -> throw UvUnavailableException(decision.message)
            }
        return PlannedLaunch(baseRequest, launcher)
    }

    private fun launcherInfoFor(launcher: MarimoLauncher, request: LaunchRequest): LauncherInfo? =
        launcher.marimoCliPrefix(request)?.let { prefix -> LauncherInfo(prefix, request.sandbox) }

    private fun completeReadyUrlFrom(
        handle: io.marimo.notebook.launch.MarimoServerHandle,
        readyUrl: CompletableFuture<String>,
    ) {
        handle.awaitReady().whenComplete { url, error ->
            if (error != null) {
                Disposer.dispose(handle)
                readyUrl.completeExceptionally(error)
            } else {
                readyUrl.complete(url)
            }
        }
    }

    /** Side-effect-free lookup: null when the notebook has no session. Never creates one. */
    fun peek(file: VirtualFile): SessionSnapshot? = statusForUrl(file.url)

    internal fun statusFor(file: VirtualFile): SessionSnapshot? = peek(file)

    fun statusForUrl(url: String): SessionSnapshot? =
        sessionForUrl(url)?.let { synchronized(it) { it.snapshot() } }

    fun sessions(): List<SessionSnapshot> =
        sessions.values.map { synchronized(it) { it.snapshot() } }

    /**
     * Stops [file]'s server. An active ownership lease retains the session so its UI displays a
     * stopped panel with a Restart offer. Otherwise, the manager removes and disposes the session.
     */
    fun stop(file: VirtualFile) = stopUrl(file.url)

    fun stopUrl(url: String) {
        val session = sessionForUrl(url) ?: return
        stopSession(session)
    }

    private fun stopSession(session: NotebookSession) {
        val result =
            synchronized(session) {
                if (sessions[session.id] !== session) return
                cancelTtlLocked(session)
                cancelInFlightLaunchLocked(session)
                session.launchContext = null
                val removed = session.shouldArmTtl && sessions.remove(session.id, session)
                val transition =
                    if (removed) session.lifecycle.prepareRelease()
                    else session.lifecycle.prepareStop()
                removed to transition
            }
        val (removed, transition) = result
        transition.publish()
        if (removed) {
            notifySessionEnded(session.id)
            disposeSessionOnEdt(session)
        }
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
        val releaseTransition =
            synchronized(session) {
                if (sessions[session.id] !== session) return
                cancelInFlightLaunchLocked(session)
                session.launchContext = null
                session.lifecycle.prepareRelease()
            }
        releaseTransition.publish()
        val restarted =
            synchronized(session) {
                if (sessions[session.id] !== session) {
                    false
                } else {
                    readyUrlForSessionLocked(session, session.notebook)
                    if (session.shouldArmTtl) armTtlLocked(session.id, session)
                    true
                }
            }
        if (restarted) notifySessionRestarted(session.id)
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

    /** Stops the process but retains the session. */
    fun release(file: VirtualFile) {
        val releaseTransition =
            sessionForUrl(file.url)?.let { session ->
                synchronized(session) {
                    if (sessions[session.id] !== session) return@let null
                    session.launchContext = null
                    cancelInFlightLaunchLocked(session)
                    session.lifecycle.prepareRelease()
                }
            }
        releaseTransition?.publish()
    }

    /** [listener] runs after any session change. It must only schedule work, not block. */
    fun addSessionsListener(parent: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    /** [listener] observes session removal and restart after the manager updates its state. */
    internal fun addSessionEventListener(
        parent: Disposable,
        listener: (NotebookSessionEvent) -> Unit,
    ) {
        sessionEventListeners.add(listener)
        Disposer.register(parent) { sessionEventListeners.remove(listener) }
    }

    override fun dispose() {
        sessions.values.forEach { session ->
            synchronized(session) {
                cancelTtlLocked(session)
                cancelInFlightLaunchLocked(session)
            }
            if (sessions.remove(session.id, session)) {
                notifySessionEnded(session.id)
                Disposer.dispose(session)
            }
        }
    }

    private fun completeLaunchFailure(
        session: NotebookSession,
        readyUrl: CompletableFuture<String>,
        error: Exception,
    ) {
        val failureTransition =
            synchronized(session) {
                if (sessions[session.id] === session && session.inFlightReadyUrl === readyUrl) {
                    session.lifecycle.prepareLaunchPlanFailure(error)
                } else {
                    null
                }
            }
        failureTransition?.publish()
        readyUrl.completeExceptionally(error)
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
     * Ownership can return after expiry fires. An expiry task can run after cancellation or session
     * removal. The session lock protects each state check. The matching session identity keeps
     * disposal single-shot.
     */
    private fun onTtlExpired(sessionId: SessionId, session: NotebookSession, generation: Long) {
        val expired =
            synchronized(session) {
                session.ttlGeneration == generation &&
                    session.shouldArmTtl &&
                    sessions.remove(sessionId, session).also {
                        if (it) cancelInFlightLaunchLocked(session)
                    }
            }
        if (!expired) return
        session.lifecycle.release()
        notifySessionEnded(session.id)
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
                        val current =
                            synchronized(session) {
                                if (!session.lifecycle.isCurrent(update)) {
                                    false
                                } else {
                                    if (
                                        update.state !is MarimoNotebookState.Starting &&
                                            update.state !is MarimoNotebookState.Running
                                    ) {
                                        session.launchContext = null
                                    }
                                    true
                                }
                            }
                        if (current) notifySessionsChanged()
                    }
                }
        }

    private fun sessionForUrl(url: String): NotebookSession? =
        sessions.values.firstOrNull { session -> session.fileUrl == url }

    private fun cancelInFlightLaunchLocked(session: NotebookSession) {
        session.inFlightReadyUrl?.let { readyUrl ->
            session.inFlightReadyUrl = null
            readyUrl.completeExceptionally(
                CancellationException("Notebook session launch was cancelled")
            )
        }
    }

    private fun releaseOwner(sessionId: SessionId, owner: LeaseOwner) {
        val session = sessions[sessionId] ?: return
        val released =
            synchronized(session) {
                if (sessions[session.id] !== session || !session.releaseLease(owner)) {
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
        private val owner: LeaseOwner?,
    ) : NotebookSessionLease {
        private val closed = AtomicBoolean(false)

        override val notebook: VirtualFile
            get() = sessionForId(sessionId).notebook

        override fun readyUrl(): CompletableFuture<String> {
            val session = sessions[sessionId] ?: return expiredLeaseFuture()
            return synchronized(session) {
                val notebook = session.notebookOrNull
                if (sessions[sessionId] !== session || notebook == null) expiredLeaseFuture()
                else readyUrlForSessionLocked(session, notebook)
            }
        }

        override fun status(): SessionSnapshot {
            val session = sessionForId(sessionId)
            return synchronized(session) { session.snapshot() }
        }

        override fun launcherInfo(): LauncherInfo? =
            sessions[sessionId]?.let { session ->
                synchronized(session) {
                    session.launchContext?.launcherInfo
                }
            }

        override fun restart() {
            sessions[sessionId]?.let(::restartSession)
        }

        override fun stop() {
            sessions[sessionId]?.let(::stopSession)
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) owner?.let { releaseOwner(sessionId, it) }
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

    private fun notifySessionEnded(sessionId: SessionId) {
        notifySessionEvent(NotebookSessionEvent.Ended(sessionId))
    }

    private fun notifySessionRestarted(sessionId: SessionId) {
        notifySessionEvent(NotebookSessionEvent.Restarted(sessionId))
    }

    private fun notifySessionEvent(event: NotebookSessionEvent) {
        sessionEventListeners.forEach { it(event) }
    }

    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) block()
        else
            application.invokeLater {
                if (!project.isDisposed) block()
            }
    }
}
