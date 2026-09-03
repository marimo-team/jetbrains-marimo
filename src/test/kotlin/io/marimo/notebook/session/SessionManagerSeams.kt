/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

/**
 * Captures injectable manager seams so a later test on a reused light project cannot inherit fakes.
 */
internal class SessionManagerSeams(private val manager: NotebookSessionManager) {
    private val planner = manager.planner
    private val tokenPasswordFileWriter = manager.tokenPasswordFileWriter
    private val launchEnvCollector = manager.launchEnvCollector
    private val ttlScheduler = manager.ttlScheduler
    private val clock = manager.clock

    fun restore() {
        manager.planner = planner
        manager.tokenPasswordFileWriter = tokenPasswordFileWriter
        manager.launchEnvCollector = launchEnvCollector
        manager.ttlScheduler = ttlScheduler
        manager.clock = clock
    }
}
