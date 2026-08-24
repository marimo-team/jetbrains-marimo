/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CompletableFuture

/** One owner's handle on a shared notebook session. */
interface NotebookSessionLease : AutoCloseable {
    val sessionId: SessionId
    val notebook: VirtualFile

    fun readyUrl(): CompletableFuture<String>

    fun status(): SessionSnapshot

    fun launcherInfo(): LauncherInfo?

    fun restart()

    fun stop()

    override fun close()
}

/** The subsystem that currently owns a notebook session. */
enum class LeaseOwner {
    EDITOR_TAB,
    /**
     * Transient prompt generation: it closes after delivery or failure and does not hold the TTL.
     */
    PAIR_PROMPT,
    PAIR_TERMINAL;

    internal val suppressesTtl: Boolean
        get() = this == EDITOR_TAB || this == PAIR_TERMINAL
}
