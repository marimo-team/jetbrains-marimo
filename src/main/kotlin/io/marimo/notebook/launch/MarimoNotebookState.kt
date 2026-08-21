/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

sealed interface MarimoNotebookState {
    object Starting : MarimoNotebookState

    data class Running(val url: String) : MarimoNotebookState

    data class Stopping(val url: String) : MarimoNotebookState

    data class Stopped(val cause: StopCause) : MarimoNotebookState

    data class Failed(val error: Throwable) : MarimoNotebookState
}

sealed interface StopCause {
    object Deliberate : StopCause

    data class Unexpected(
        val exitCode: Int,
        val outputTail: String,
    ) : StopCause
}
