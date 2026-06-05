/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

class LauncherRegistry(private val launchers: List<MarimoLauncher>) {
    fun resolve(request: LaunchRequest): MarimoLauncher =
        launchers.firstOrNull { it.canLaunch(request) }
            ?: throw NoApplicableLauncherException(request)
}
