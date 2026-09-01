/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.marimo.notebook.MarimoBundle
import java.util.concurrent.ConcurrentHashMap

/** One-time offer per notebook and IDE run. Fires when that notebook has no exposure decision. */
object DataSourceConsentPrompt {
    private val offered = ConcurrentHashMap.newKeySet<String>()

    fun offer(project: Project, notebookKey: String, mappableCount: Int) {
        if (!offered.add("${project.locationHash}:$notebookKey")) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val store = DataSourceExposureStore.getInstance(project)
            if (store.decisionRecorded(notebookKey)) return@invokeLater
            val notification =
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Marimo")
                    .createNotification(
                        MarimoBundle.message("datasource.consent.title"),
                        MarimoBundle.message(
                            "datasource.consent.body",
                            notebookKey,
                            mappableCount,
                        ),
                        NotificationType.INFORMATION,
                    )
            notification.isImportant = true
            notification.addAction(
                NotificationAction.createSimpleExpiring(
                    MarimoBundle.message("datasource.consent.configure")
                ) {
                    DataSourceToolWindowTabProvider.show(project)
                }
            )
            notification.addAction(
                NotificationAction.createSimpleExpiring(
                    MarimoBundle.message("datasource.consent.never")
                ) {
                    store.recordNever()
                }
            )
            notification.notify(project)
        }
    }
}
