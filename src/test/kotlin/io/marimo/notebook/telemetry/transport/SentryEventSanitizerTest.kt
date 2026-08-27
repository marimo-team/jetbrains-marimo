/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import io.sentry.SentryEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryEventSanitizerTest {
    @Test
    fun keepsMarimoOriginException() {
        assertTrue(SentryEventSanitizer.isMarimoOrigin(marimoException()))
    }

    @Test
    fun dropsForeignException() {
        val e =
            RuntimeException("boom").apply {
                stackTrace = arrayOf(StackTraceElement("com.other.plugin.Foo", "bar", "Foo.kt", 3))
            }
        assertFalse(SentryEventSanitizer.isMarimoOrigin(e))
    }

    @Test
    fun dropsLookalikePackagePrefix() {
        val e =
            RuntimeException("boom").apply {
                stackTrace =
                    arrayOf(
                        StackTraceElement(
                            "io.marimo.notebookevil.ForeignPlugin",
                            "start",
                            "ForeignPlugin.kt",
                            3,
                        )
                    )
            }
        assertFalse(SentryEventSanitizer.isMarimoOrigin(e))
    }

    @Test
    fun findsMarimoFrameInCauseChain() {
        val root = marimoException()
        val wrapper =
            RuntimeException("wrapper", root).apply {
                stackTrace = arrayOf(StackTraceElement("com.other.plugin.Foo", "bar", "Foo.kt", 3))
            }
        assertTrue(SentryEventSanitizer.isMarimoOrigin(wrapper))
    }

    @Test
    fun beforeSendRemovesServerNameFromPluginEvents() {
        val event = SentryEvent(marimoException()).apply { serverName = "private-hostname" }
        val result = requireNotNull(SentryEventSanitizer.beforeSend(event))
        assertSame(event, result)
        assertNull(result.serverName)
    }

    @Test
    fun beforeSendDropsForeignEvents() {
        val foreign =
            RuntimeException("boom").apply {
                stackTrace = arrayOf(StackTraceElement("com.other.plugin.Foo", "bar", "Foo.kt", 3))
            }
        val event = SentryEvent(foreign).apply { serverName = "private-hostname" }
        assertNull(SentryEventSanitizer.beforeSend(event))
    }

    private fun marimoException() =
        RuntimeException("boom").apply {
            stackTrace =
                arrayOf(
                    StackTraceElement(
                        "io.marimo.notebook.launch.SdkLauncher",
                        "start",
                        "SdkLauncher.kt",
                        12,
                    )
                )
        }
}
