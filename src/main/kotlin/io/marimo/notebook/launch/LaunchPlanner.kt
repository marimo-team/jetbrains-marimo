/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

/** The outcome of deciding how to launch marimo for a request. */
sealed interface LaunchDecision {
    /** Launch marimo with the chosen launcher. */
    data class Launch(val launcher: MarimoLauncher) : LaunchDecision

    /** No Python interpreter is configured, so there is nothing to launch on. */
    data class NoInterpreter(val message: String) : LaunchDecision
}

/**
 * Chooses how to launch marimo for a request. The configured interpreter is the single source of
 * truth: when one resolves, marimo runs on it; otherwise the decision is [LaunchDecision.NoInterpreter]
 * rather than a silent throwaway environment.
 */
class LaunchPlanner(private val sdkLauncher: MarimoLauncher) {
    fun plan(request: LaunchRequest): LaunchDecision =
        if (sdkLauncher.canLaunch(request)) {
            LaunchDecision.Launch(sdkLauncher)
        } else {
            LaunchDecision.NoInterpreter("No Python interpreter is configured for this project.")
        }
}
