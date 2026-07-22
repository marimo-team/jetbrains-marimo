/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

/**
 * The v1 telemetry event set. Each variant fixes its exact property set — this is the
 * property allowlist: file paths, code, and notebook contents cannot leak because there is
 * no variant that carries them. The service appends `plugin_version` to every event.
 */
sealed class TelemetryEvent(val name: String, val properties: Map<String, Any>) {
    class PluginActivated(ideName: String, ideVersion: String) :
        TelemetryEvent("plugin_activated", mapOf("ide_name" to ideName, "ide_version" to ideVersion))

    class NotebookOpened(launcher: String) :
        TelemetryEvent("notebook_opened", mapOf("launcher" to launcher))

    class NotebookLaunchFailed(reason: String) :
        TelemetryEvent("notebook_launch_failed", mapOf("reason" to reason))

    class MarimoInstallResult(success: Boolean) :
        TelemetryEvent("marimo_install_result", mapOf("success" to success))

    class PairStarted(method: String, harness: String) :
        TelemetryEvent("pair_started", mapOf("method" to method, "harness" to harness))

    object SandboxStarted : TelemetryEvent("sandbox_started", emptyMap())
}
