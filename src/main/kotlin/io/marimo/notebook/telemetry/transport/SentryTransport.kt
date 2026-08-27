/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import io.marimo.notebook.telemetry.SentrySink
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.User

internal fun createSentryOptions(
    dsn: String,
    release: String,
    environment: String,
): SentryOptions = SentryOptions().also { configureSentryOptions(it, dsn, release, environment) }

internal fun configureSentryOptions(
    options: SentryOptions,
    dsn: String,
    release: String,
    environment: String,
) {
    options.apply {
        this.dsn = dsn
        this.release = release
        this.environment = environment
        isAttachServerName = false
        isSendDefaultPii = false
        isEnableUncaughtExceptionHandler = false
        // Sessions are driven from the consent lifecycle (allow/revoke/dispose), not the SDK's
        // process hooks, so a session maps to one consented run rather than JVM start.
        isEnableAutoSessionTracking = false
        setBeforeSend { event, _ -> SentryEventSanitizer.beforeSend(event) }
    }
}

internal class SentryTransport(
    dsn: String,
    release: String,
    environment: String,
    ideName: String,
    ideVersion: String,
    pluginVersion: String,
    anonymousId: String,
) : SentrySink {
    init {
        Sentry.init { options -> configureSentryOptions(options, dsn, release, environment) }
        Sentry.configureScope { scope ->
            scope.setTag("ide_name", ideName)
            scope.setTag("ide_version", ideVersion)
            scope.setTag("plugin_version", pluginVersion)
            scope.user = User().apply { id = anonymousId }
        }
    }

    override fun captureException(throwable: Throwable) {
        Sentry.captureException(throwable)
    }

    override fun startSession() {
        Sentry.startSession()
    }

    override fun endSession() {
        Sentry.endSession()
    }

    override fun close() {
        Sentry.close()
    }
}
