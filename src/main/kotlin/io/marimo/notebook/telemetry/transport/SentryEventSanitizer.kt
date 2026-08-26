/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import io.sentry.SentryEvent

internal object SentryEventSanitizer {
    private const val MARIMO_PACKAGE = "io.marimo.notebook"

    fun isMarimoOrigin(throwable: Throwable?): Boolean {
        var current = throwable
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current.stackTrace.any { it.className.startsWith("$MARIMO_PACKAGE.") }) return true
            current = current.cause
        }
        return false
    }

    fun beforeSend(event: SentryEvent): SentryEvent? {
        if (!isMarimoOrigin(event.throwable)) return null
        event.serverName = null
        return event
    }
}
