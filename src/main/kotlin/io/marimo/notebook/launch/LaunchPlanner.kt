/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

/** Thrown when a launch is requested but no interpreter is configured to run marimo on. */
class NoInterpreterException(message: String) : RuntimeException(message)

/** Thrown when sandbox mode is requested but uv (which marimo's sandbox needs) isn't available. */
class UvUnavailableException(message: String) : RuntimeException(message)

/** The outcome of deciding how to launch marimo for a request. */
sealed interface LaunchDecision {
    /** Launch marimo with the chosen launcher. */
    data class Launch(val launcher: MarimoLauncher) : LaunchDecision

    /** No Python interpreter is configured, so there is nothing to launch on. */
    data class NoInterpreter(val message: String) : LaunchDecision

    /** Sandbox mode was requested but uv isn't available to run it. */
    data class NeedsUv(val message: String) : LaunchDecision
}

/**
 * Chooses how to launch marimo for a request. A sandbox request routes to uv (marimo's isolated
 * environment); otherwise the configured interpreter is the single source of truth — when one
 * resolves, marimo runs on it, else the decision is [LaunchDecision.NoInterpreter] rather than a
 * silent throwaway environment.
 */
class LaunchPlanner(
    private val sdkLauncher: MarimoLauncher,
    private val uvLauncher: MarimoLauncher,
) {
    fun plan(request: LaunchRequest): LaunchDecision =
        when {
            request.sandbox && uvLauncher.canLaunch(request) -> LaunchDecision.Launch(uvLauncher)
            request.sandbox -> LaunchDecision.NeedsUv("marimo sandbox mode requires uv, which wasn't found.")
            sdkLauncher.canLaunch(request) -> LaunchDecision.Launch(sdkLauncher)
            else -> LaunchDecision.NoInterpreter("No Python interpreter is configured for this project.")
        }
}
