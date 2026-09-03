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
                        dataSourceConsentBody(notebookKey, mappableCount),
                        NotificationType.INFORMATION,
                    )
            notification.isImportant = true
            notification.addAction(
                NotificationAction.createSimpleExpiring(
                    MarimoBundle.message("datasource.consent.configure")
                ) {
                    DataSourceToolWindowTabProvider.show(project, notebookKey)
                }
            )
            notification.addAction(
                NotificationAction.createSimpleExpiring(
                    MarimoBundle.message("datasource.consent.never")
                ) {
                    never(project)
                }
            )
            notification.notify(project)
        }
    }

    internal fun never(project: Project) {
        val store = DataSourceExposureStore.getInstance(project)
        DataSourceStaleness.apply(project, DataSourceEvent.Changed(id = null))
        store.recordNever()
    }
}

internal fun dataSourceConsentBody(notebookKey: String, mappableCount: Int): String =
    MarimoBundle.message(
        if (mappableCount == 1) "datasource.consent.body.one" else "datasource.consent.body.many",
        notebookKey,
        mappableCount,
    )
