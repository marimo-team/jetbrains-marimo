/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

/** A session lifecycle change that editor-owned resources need to observe. */
internal sealed interface NotebookSessionEvent {
    val sessionId: SessionId

    /** The session left the manager and any resources retained for it must be disposed. */
    data class Ended(override val sessionId: SessionId) : NotebookSessionEvent

    /** The session started a fresh server and retained resources must reconnect to it. */
    data class Restarted(override val sessionId: SessionId) : NotebookSessionEvent
}
