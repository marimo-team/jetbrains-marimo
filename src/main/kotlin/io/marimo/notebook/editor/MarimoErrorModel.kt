/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.session.environment.MarimoPresence
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.StopCause
import io.marimo.notebook.launch.UvUnavailableException

/** An action the error panel can offer; the editor supplies the behaviour for each. */
enum class MarimoErrorAction {
    RETRY,
    INSTALL,
    START_IN_SANDBOX,
    OPEN_AS_PYTHON,
    CLOSE,
}

/** Why the marimo editor could not be shown. */
sealed interface MarimoFailure {
    /** The server never produced a URL: marimo missing, no interpreter, or the process crashed. */
    data class ServerNotStarted(val cause: Throwable?) : MarimoFailure

    /** The server started but the embedded browser failed to load it. */
    data class EditorLoadFailed(val detail: String?) : MarimoFailure

    /** The server served this notebook and then stopped: shut down from the page, or died. */
    data class ServerStopped(val cause: StopCause) : MarimoFailure
}

/**
 * What the error panel renders: a headline, optional secondary detail, and the actions that apply.
 * Derived from the failure and the interpreter's marimo presence so the message and buttons match
 * the actual cause — e.g. an Install button only when marimo is known to be missing.
 */
data class MarimoErrorModel(
    val message: String,
    val detail: String?,
    val actions: List<MarimoErrorAction>,
    /** Whether the Start-in-Sandbox action is usable (uv present); false renders it disabled. */
    val sandboxEnabled: Boolean = true,
) {
    companion object {
        fun of(
            failure: MarimoFailure,
            presence: MarimoPresence,
            uvAvailable: Boolean,
        ): MarimoErrorModel =
            when (failure) {
                is MarimoFailure.ServerNotStarted ->
                    serverNotStarted(failure.cause, presence, uvAvailable)
                is MarimoFailure.EditorLoadFailed ->
                    MarimoErrorModel(
                        message = "marimo started, but the editor failed to load.",
                        detail = failure.detail.nullIfBlank(),
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
                is MarimoFailure.ServerStopped -> serverStopped(failure.cause)
            }

        // A failed launch carries the process's stderr tail (a Python traceback) as its message.
        // The headline already names the cause, so that raw text is kept out of the panel and left
        // to the IDE log; the panel stays a clean message + actions.
        private fun serverNotStarted(
            cause: Throwable?,
            presence: MarimoPresence,
            uvAvailable: Boolean,
        ): MarimoErrorModel {
            val (message, actions) =
                when {
                    cause is UvUnavailableException ->
                        "marimo sandbox mode needs uv. Install uv to run in an isolated environment." to
                            listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON)
                    cause is NoInterpreterException ->
                        "No Python interpreter is configured. Configure one to run marimo on it." to
                            listOf(
                                MarimoErrorAction.RETRY,
                                MarimoErrorAction.START_IN_SANDBOX,
                                MarimoErrorAction.OPEN_AS_PYTHON,
                            )
                    presence is MarimoPresence.Missing ->
                        "marimo isn't installed in the project interpreter." to
                            listOf(
                                MarimoErrorAction.INSTALL,
                                MarimoErrorAction.RETRY,
                                MarimoErrorAction.START_IN_SANDBOX,
                                MarimoErrorAction.OPEN_AS_PYTHON,
                            )
                    else ->
                        "marimo couldn't be started." to
                            listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON)
                }
            return MarimoErrorModel(
                message = message,
                detail = null,
                actions = actions,
                sandboxEnabled = MarimoErrorAction.START_IN_SANDBOX !in actions || uvAvailable,
            )
        }

        // The process output tail is deliberately dropped: it is a Python traceback that belongs in
        // the IDE log, and the headline already names the cause. Close is offered only for a
        // deliberate stop, where being finished with the notebook is the likely intent.
        private fun serverStopped(cause: StopCause): MarimoErrorModel =
            when (cause) {
                is StopCause.Deliberate ->
                    MarimoErrorModel(
                        message = "marimo was shut down for this notebook.",
                        detail = "Restart it to keep working, or close the tab.",
                        actions =
                            listOf(
                                MarimoErrorAction.RETRY,
                                MarimoErrorAction.CLOSE,
                                MarimoErrorAction.OPEN_AS_PYTHON,
                            ),
                    )
                is StopCause.Unexpected ->
                    MarimoErrorModel(
                        message = "marimo stopped unexpectedly.",
                        detail = "See the IDE log for the process output.",
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
            }

        private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
    }
}
