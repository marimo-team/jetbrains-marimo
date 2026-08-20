/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import io.marimo.notebook.editor.MarimoNotebookView
import io.marimo.notebook.launch.MarimoNotebookLifecycle
import io.marimo.notebook.launch.MarimoNotebookState
import com.intellij.openapi.Disposable
import java.util.concurrent.atomic.AtomicBoolean

/** What one notebook's server process is doing, reduced to the states the UI presents. */
enum class MarimoSessionState {
    STARTING, RUNNING, STOPPING, STOPPED, FAILED;

    /** True while a server process exists for the session. */
    val isLive: Boolean get() = this == STARTING || this == RUNNING || this == STOPPING
}

/** The launch settings one server process was started with. Carries no credentials. */
data class MarimoLaunchContext(
    val port: Int,
    val workDir: String,
    val launcherId: String,
    val sandbox: Boolean,
    /** True when this launched server requires an access token. */
    val tokenAuthEnabled: Boolean,
)

/**
 * A read-only description of one session for status consumers: the file icon, the context actions,
 * and the Sessions panel. This type must never gain a URL or token field — everything here may be
 * rendered, logged, or copied by UI code.
 */
data class MarimoSessionSnapshot(
    val fileUrl: String,
    val fileName: String,
    val state: MarimoSessionState,
    val attachedTabs: Int,
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
 * launch mode, and the editor-attachment bookkeeping that drives the background TTL. Owned by
 * [MarimoServerService]; mutable fields are guarded by `synchronized(session)` there.
 */
internal class MarimoNotebookSession(
    val fileUrl: String,
    val fileName: String,
) : Disposable {
    val lifecycle = MarimoNotebookLifecycle()
    val sandboxEnabled = AtomicBoolean(false)
    var view: MarimoNotebookView? = null
    var launchContext: MarimoLaunchContext? = null
    var attachedTabs: Int = 0
    var ttl: TtlCancellable? = null
    var ttlGeneration: Long = 0
    var expiresAtMillis: Long? = null

    fun snapshot(): MarimoSessionSnapshot =
        MarimoSessionSnapshot(
            fileUrl = fileUrl,
            fileName = fileName,
            state = when (lifecycle.state) {
                is MarimoNotebookState.Starting -> MarimoSessionState.STARTING
                is MarimoNotebookState.Running -> MarimoSessionState.RUNNING
                is MarimoNotebookState.Stopping -> MarimoSessionState.STOPPING
                is MarimoNotebookState.Stopped -> MarimoSessionState.STOPPED
                is MarimoNotebookState.Failed -> MarimoSessionState.FAILED
            },
            attachedTabs = attachedTabs,
            expiresAtMillis = expiresAtMillis,
            launch = launchContext,
            sandbox = sandboxEnabled.get(),
        )

    override fun dispose() = lifecycle.release()
}
