/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoPresence
import io.marimo.notebook.launch.NoInterpreterException

/** An action the error panel can offer; the editor supplies the behaviour for each. */
enum class MarimoErrorAction { RETRY, INSTALL, OPEN_AS_PYTHON }

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
) {
    companion object {
        fun of(failure: MarimoFailure, presence: MarimoPresence): MarimoErrorModel =
            when (failure) {
                is MarimoFailure.ServerNotStarted -> serverNotStarted(failure.cause, presence)
                is MarimoFailure.EditorLoadFailed ->
                    MarimoErrorModel(
                        message = "marimo started, but the editor failed to load.",
                        detail = failure.detail.nullIfBlank(),
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
            }

        private fun serverNotStarted(cause: Throwable?, presence: MarimoPresence): MarimoErrorModel {
            val detail = cause?.message.nullIfBlank()
            return when {
                cause is NoInterpreterException ->
                    MarimoErrorModel(
                        message = "No Python interpreter is configured. Configure one to run marimo on it.",
                        detail = detail,
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
                presence is MarimoPresence.Missing ->
                    MarimoErrorModel(
                        message = "marimo isn't installed in the project interpreter.",
                        detail = detail,
                        actions = listOf(
                            MarimoErrorAction.INSTALL,
                            MarimoErrorAction.RETRY,
                            MarimoErrorAction.OPEN_AS_PYTHON,
                        ),
                    )
                else ->
                    MarimoErrorModel(
                        message = "marimo couldn't be started.",
                        detail = detail,
                        actions = listOf(MarimoErrorAction.RETRY, MarimoErrorAction.OPEN_AS_PYTHON),
                    )
            }
        }

        private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
    }
}
