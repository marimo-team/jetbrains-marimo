/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import io.marimo.notebook.telemetry.PostHogSink
import io.marimo.notebook.telemetry.SentrySink

/** Drops usage events. Used for local and CI builds so they do not pollute production analytics. */
internal object NoOpPostHogSink : PostHogSink {
    override fun capture(distinctId: String, event: String, properties: Map<String, Any>) = Unit

    override fun close() = Unit
}

internal object NoOpSentrySink : SentrySink {
    override fun captureException(throwable: Throwable) = Unit

    override fun startSession() = Unit

    override fun endSession() = Unit

    override fun close() = Unit
}

/**
 * Live backends are on for production artifacts, or when a development build sets `live=true`.
 * Missing or any other value stays no-op.
 */
internal fun liveTelemetryEnabled(liveProperty: String?): Boolean =
    liveProperty.equals("true", ignoreCase = true)

internal fun postHogSink(live: Boolean, apiKey: String, host: String): PostHogSink =
    if (live) PostHogTransport(apiKey, host) else NoOpPostHogSink

internal fun sentrySink(
    live: Boolean,
    dsn: String,
    release: String,
    environment: String,
    ideName: String,
    ideVersion: String,
    pluginVersion: String,
    anonymousId: String,
): SentrySink {
    if (!live) return NoOpSentrySink
    return SentryTransport(
        dsn = dsn,
        release = release,
        environment = environment,
        ideName = ideName,
        ideVersion = ideVersion,
        pluginVersion = pluginVersion,
        anonymousId = anonymousId,
    )
}
