/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarimoConsentPromptTest : BasePlatformTestCase() {
    fun testTracksOnlyOneLiveNotification() {
        val tracker = ConsentNotificationTracker()
        val first = notification("first")
        val second = notification("second")

        assertTrue(tracker.register(first))
        assertFalse(tracker.register(second))

        tracker.expire()
        assertTrue(first.isExpired)
        assertTrue(tracker.register(second))

        tracker.expire()
        assertTrue(second.isExpired)
    }

    private fun notification(title: String) =
        Notification("Marimo", title, "body", NotificationType.INFORMATION)
}
