/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

object SentryOriginFilter {
    private const val MARIMO_PACKAGE = "io.marimo.notebook"

    fun isMarimoOrigin(throwable: Throwable?): Boolean {
        var current = throwable
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current.stackTrace.any { it.className.startsWith(MARIMO_PACKAGE) }) return true
            current = current.cause
        }
        return false
    }
}
