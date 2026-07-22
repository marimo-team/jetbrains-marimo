/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.marimo.notebook.MarimoBundle

object MarimoConsentPrompt {

    fun maybePrompt(project: Project) {
        val telemetry = MarimoTelemetry.getInstance()
        if (telemetry.consent != Consent.UNSET) return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Marimo")
            .createNotification(
                MarimoBundle.message("telemetry.consent.title"),
                MarimoBundle.message("telemetry.consent.body"),
                NotificationType.INFORMATION,
            )
        notification.isImportant = true
        notification.addAction(
            NotificationAction.createSimpleExpiring(MarimoBundle.message("telemetry.consent.allow")) {
                telemetry.allow()
            },
        )
        notification.addAction(
            NotificationAction.createSimpleExpiring(MarimoBundle.message("telemetry.consent.deny")) {
                telemetry.deny()
            },
        )
        notification.addAction(
            BrowseNotificationAction(MarimoBundle.message("telemetry.consent.privacy"), MarimoTelemetry.PRIVACY_URL),
        )
        notification.notify(project)
    }
}
