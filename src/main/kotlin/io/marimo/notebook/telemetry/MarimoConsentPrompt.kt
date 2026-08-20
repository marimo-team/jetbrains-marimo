/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.marimo.notebook.MarimoBundle

internal class ConsentNotificationTracker {
    private var liveNotification: Notification? = null

    fun register(notification: Notification): Boolean =
        synchronized(this) {
            if (liveNotification?.isExpired == true) liveNotification = null
            if (liveNotification != null) return@synchronized false

            liveNotification = notification
            notification.whenExpired { clear(notification) }
            true
        }

    fun expire() {
        val notification = synchronized(this) { liveNotification.also { liveNotification = null } }
        notification?.expire()
    }

    private fun clear(notification: Notification) {
        synchronized(this) {
            if (liveNotification === notification) liveNotification = null
        }
    }
}

object MarimoConsentPrompt {
    private val notifications = ConsentNotificationTracker()

    fun maybePrompt(project: Project) {
        val telemetry = MarimoTelemetry.getInstance()
        if (telemetry.consent != Consent.UNSET) {
            expire()
            return
        }

        val notification =
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Marimo")
                .createNotification(
                    MarimoBundle.message("telemetry.consent.title"),
                    MarimoBundle.message("telemetry.consent.body"),
                    NotificationType.INFORMATION,
                )
        notification.isImportant = true
        notification.addAction(
            NotificationAction.createSimpleExpiring(
                MarimoBundle.message("telemetry.consent.allow")
            ) {
                telemetry.allow()
            }
        )
        notification.addAction(
            NotificationAction.createSimpleExpiring(
                MarimoBundle.message("telemetry.consent.deny")
            ) {
                telemetry.deny()
            }
        )
        notification.addAction(
            BrowseNotificationAction(
                MarimoBundle.message("telemetry.consent.privacy"),
                MarimoTelemetry.PRIVACY_URL,
            )
        )
        if (!notifications.register(notification)) return
        notification.notify(project)
    }

    internal fun expire() {
        notifications.expire()
    }
}
