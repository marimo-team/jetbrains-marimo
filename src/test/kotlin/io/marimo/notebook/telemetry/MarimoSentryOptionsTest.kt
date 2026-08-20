/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import io.sentry.Hint
import io.sentry.SentryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MarimoSentryOptionsTest {
    @Test
    fun disablesHostnameAndDefaultPiiCollection() {
        val options = options()

        assertFalse(options.isAttachServerName)
        assertFalse(options.isSendDefaultPii)
        assertEquals("jetbrains-marimo@1.2.3", options.release)
        assertEquals("test", options.environment)
    }

    @Test
    fun beforeSendRemovesServerNameFromPluginEvents() {
        val throwable =
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
        val event = SentryEvent(throwable).apply { serverName = "private-hostname" }

        val beforeSend = requireNotNull(options().beforeSend)
        val result = requireNotNull(beforeSend.execute(event, Hint()))

        assertSame(event, result)
        assertNull(result.serverName)
    }

    private fun options() =
        createSentryOptions(
            dsn = "https://public@example.invalid/1",
            release = "jetbrains-marimo@1.2.3",
            environment = "test",
        )
}
