/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import io.marimo.notebook.editor.MarimoNotebookView
import io.marimo.notebook.launch.MarimoNotebookLifecycle
import io.marimo.notebook.launch.MarimoNotebookState
import java.util.EnumMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** What one notebook's server process is doing, reduced to the states the UI presents. */
enum class MarimoSessionState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    /** True while a server process exists for the session. */
    val isLive: Boolean
        get() = this == STARTING || this == RUNNING || this == STOPPING
}

/** The launch settings one server process was started with. Carries no credentials. */
data class MarimoLaunchContext(
    val port: Int,
    val workDir: String,
    val launcherId: String,
    val launcherInfo: LauncherInfo?,
    val sandbox: Boolean,
    /** True when this launched server requires an access token. */
    val tokenAuthEnabled: Boolean,
)

/**
 * A read-only description of one session for status consumers: the file icon, the context actions,
 * and the Sessions panel. This type must never gain a URL or token field — everything here may be
 * rendered, logged, or copied by UI code.
 */
data class SessionSnapshot(
    val fileUrl: String,
    val fileName: String,
    val state: MarimoSessionState,
    /** Epoch millis when the background TTL stops the session, or null while no TTL is armed. */
    val expiresAtMillis: Long?,
    val launch: MarimoLaunchContext?,
    val sandbox: Boolean,
)

/** Cancels a scheduled TTL task. */
internal fun interface TtlCancellable {
    fun cancel()
}

/** Schedules the background TTL. Injectable so tests fire expiry on demand. */
internal fun interface TtlScheduler {
    fun schedule(delayMillis: Long, task: Runnable): TtlCancellable
}

/**
 * Everything the project keeps for one notebook: the server lifecycle, the shared JCEF view, the
 * launch mode, and the owner leases that drive the background TTL. Owned by
 * [NotebookSessionManager]; mutable fields are guarded by `synchronized(session)` there.
 */
internal class NotebookSession(
    val id: SessionId,
    file: VirtualFile,
) : Disposable {
    private val filePointer: VirtualFilePointer =
        VirtualFilePointerManager.getInstance().create(file, this, null)

    val fileUrl: String
        get() = filePointer.file?.url ?: filePointer.url

    val fileName: String
        get() = filePointer.file?.name ?: filePointer.fileName

    val notebook: VirtualFile
        get() = requireNotNull(filePointer.file) { "Notebook file is no longer available" }

    fun matches(file: VirtualFile): Boolean = filePointer.file === file || fileUrl == file.url

    val lifecycle = MarimoNotebookLifecycle()
    val sandboxEnabled = AtomicBoolean(false)
    var view: MarimoNotebookView? = null
    var launchContext: MarimoLaunchContext? = null
    var inFlightReadyUrl: CompletableFuture<String>? = null
    private val leaseCounts = EnumMap<LeaseOwner, Int>(LeaseOwner::class.java)
    var ttl: TtlCancellable? = null
    var ttlGeneration: Long = 0
    var expiresAtMillis: Long? = null

    val hasTtlSuppressingLease: Boolean
        get() = LeaseOwner.entries.any { owner -> owner.suppressesTtl && leaseCount(owner) > 0 }

    val shouldArmTtl: Boolean
        get() = !hasTtlSuppressingLease

    fun acquireLease(owner: LeaseOwner) {
        leaseCounts[owner] = leaseCount(owner) + 1
    }

    fun releaseLease(owner: LeaseOwner): Boolean {
        val count = leaseCount(owner)
        if (count == 0) return false
        if (count == 1) leaseCounts.remove(owner) else leaseCounts[owner] = count - 1
        return true
    }

    private fun leaseCount(owner: LeaseOwner): Int = leaseCounts[owner] ?: 0

    fun snapshot(): SessionSnapshot =
        SessionSnapshot(
            fileUrl = fileUrl,
            fileName = fileName,
            state =
                when (lifecycle.state) {
                    is MarimoNotebookState.Starting -> MarimoSessionState.STARTING
                    is MarimoNotebookState.Running -> MarimoSessionState.RUNNING
                    is MarimoNotebookState.Stopping -> MarimoSessionState.STOPPING
                    is MarimoNotebookState.Stopped -> MarimoSessionState.STOPPED
                    is MarimoNotebookState.Failed -> MarimoSessionState.FAILED
                },
            expiresAtMillis = expiresAtMillis,
            launch = launchContext,
            sandbox = sandboxEnabled.get(),
        )

    override fun dispose() = lifecycle.release()
}
