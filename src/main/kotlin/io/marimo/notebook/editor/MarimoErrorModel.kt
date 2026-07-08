/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoPresence
import io.marimo.notebook.launch.NoInterpreterException
import io.marimo.notebook.launch.UvUnavailableException

/** An action the error panel can offer; the editor supplies the behaviour for each. */
enum class MarimoErrorAction { RETRY, INSTALL, START_IN_SANDBOX, OPEN_AS_PYTHON }

/** Why the marimo editor could not be shown. */
sealed interface MarimoFailure {
    /** The server never produced a URL: marimo missing, no interpreter, or the process crashed. */
    data class ServerNotStarted(val cause: Throwable?) : MarimoFailure

    /** The server started but the embedded browser failed to load it. */
    data class EditorLoadFailed(val detail: String?) : MarimoFailure
}

/**
 * What the error panel renders: a headline, optional secondary detail, and the actions that apply.
 * Derived from the failure and the interpreter's marimo presence so the message and buttons match the
 * actual cause — e.g. an Install button only when marimo is known to be missing.
 */
data class MarimoErrorModel(
    val message: String,
    val detail: String?,
    val actions: List<MarimoErrorAction>,
    /** Whether the Start-in-Sandbox action is usable (uv present); false renders it disabled. */
    val sandboxEnabled: Boolean = true,
) {
    companion object {
        fun of(failure: MarimoFailure, presence: MarimoPresence, uvAvailable: Boolean): MarimoErrorModel =
            when (failure) {
                is MarimoFailure.ServerNotStarted -> serverNotStarted(failure.cause, presence, uvAvailable)
                is MarimoFailure.EditorLoadFailed ->
                    MarimoErrorModel(
                        message = "marimo started, but the editor failed to load.",
                        detail = failure.detail.nullIfBlank(),
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
            }

        // A failed launch carries the process's stderr tail (a Python traceback) as its message.
        // The headline already names the cause, so that raw text is kept out of the panel and left
        // to the IDE log; the panel stays a clean message + actions.
        private fun serverNotStarted(
            cause: Throwable?,
            presence: MarimoPresence,
            uvAvailable: Boolean,
        ): MarimoErrorModel =
            when {
                cause is UvUnavailableException ->
                    MarimoErrorModel(
                        message = "marimo sandbox mode needs uv. Install uv to run in an isolated environment.",
                        detail = null,
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
                cause is NoInterpreterException ->
                    MarimoErrorModel(
                        message = "No Python interpreter is configured. Configure one to run marimo on it.",
                        detail = null,
                        actions = listOf(
                            MarimoErrorAction.RETRY,
                            MarimoErrorAction.START_IN_SANDBOX,
                            MarimoErrorAction.OPEN_AS_PYTHON,
                        ),
                        sandboxEnabled = uvAvailable,
                    )
                presence is MarimoPresence.Missing ->
                    MarimoErrorModel(
                        message = "marimo isn't installed in the project interpreter.",
                        detail = null,
                        actions = listOf(
                            MarimoErrorAction.INSTALL,
                            MarimoErrorAction.RETRY,
                            MarimoErrorAction.START_IN_SANDBOX,
                            MarimoErrorAction.OPEN_AS_PYTHON,
                        ),
                        sandboxEnabled = uvAvailable,
                    )
                else ->
                    MarimoErrorModel(
                        message = "marimo couldn't be started.",
                        detail = null,
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
            }

        private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
    }
}
