/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryOriginFilterTest {
    @Test
    fun keepsMarimoOriginException() {
        val e =
            RuntimeException("boom").apply {
                stackTrace = arrayOf(StackTraceElement("io.marimo.notebook.launch.SdkLauncher", "start", "SdkLauncher.kt", 12))
            }
        assertTrue(SentryOriginFilter.isMarimoOrigin(e))
    }

    @Test
    fun dropsForeignException() {
        val e =
            RuntimeException("boom").apply {
                stackTrace = arrayOf(StackTraceElement("com.other.plugin.Foo", "bar", "Foo.kt", 3))
            }
        assertFalse(SentryOriginFilter.isMarimoOrigin(e))
    }

    @Test
    fun findsMarimoFrameInCauseChain() {
        val root =
            RuntimeException("root").apply {
                stackTrace = arrayOf(StackTraceElement("io.marimo.notebook.launch.SdkLauncher", "start", "SdkLauncher.kt", 12))
            }
        val wrapper =
            RuntimeException("wrapper", root).apply {
                stackTrace = arrayOf(StackTraceElement("com.other.plugin.Foo", "bar", "Foo.kt", 3))
            }
        assertTrue(SentryOriginFilter.isMarimoOrigin(wrapper))
    }
}
