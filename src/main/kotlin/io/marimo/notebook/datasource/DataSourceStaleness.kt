/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import io.marimo.notebook.MarimoBundle
import io.marimo.notebook.session.NotebookSessionManager

/** Marks only the live notebook sessions affected by data-source configuration drift. */
object DataSourceStaleness {
    private val notificationKey = Key.create<Notification>("marimo.datasource.stale.notification")

    fun exposureEdited(project: Project, notebookKey: String) {
        apply(project, DataSourceEvent.ExposureEdited, notebookKey)
    }

    fun apply(project: Project, event: DataSourceEvent) {
        apply(project, event, targetNotebookKey = null)
    }

    private fun apply(project: Project, event: DataSourceEvent, targetNotebookKey: String?) {
        val store = DataSourceExposureStore.getInstance(project)
        val affectedNotebookKeys =
            when (event) {
                is DataSourceEvent.Added -> emptySet()
                is DataSourceEvent.Removed -> store.notebookPathsExposing(event.id)
                is DataSourceEvent.Changed ->
                    event.id?.let(store::notebookPathsExposing)
                        ?: store.notebookPathsWithExposures()
                DataSourceEvent.ExposureEdited -> setOfNotNull(targetNotebookKey)
            }
        if (affectedNotebookKeys.isEmpty()) return
        val manager = project.getService(NotebookSessionManager::class.java)
        val affected =
            manager.sessions().mapNotNull { snapshot ->
                if (!snapshot.state.isLive) return@mapNotNull null
                val file =
                    VirtualFileManager.getInstance().findFileByUrl(snapshot.fileUrl)
                        ?: return@mapNotNull null
                val notebookKey = NotebookExposureKey.from(project, file) ?: return@mapNotNull null
                if (notebookKey !in affectedNotebookKeys) return@mapNotNull null
                file.takeIf { StalenessPolicy.marksStale(event, store.exposedIds(notebookKey)) }
            }
        if (affected.isEmpty()) return
        affected.forEach(manager::markLaunchEnvStale)
        offerRestart(project, manager)
    }

    private fun offerRestart(project: Project, manager: NotebookSessionManager) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            project
                .getUserData(notificationKey)
                ?.takeIf { !it.isExpired }
                ?.let {
                    return@invokeLater
                }
            val notification =
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Marimo")
                    .createNotification(
                        MarimoBundle.message("datasource.stale.title"),
                        MarimoBundle.message("datasource.stale.body"),
                        NotificationType.INFORMATION,
                    )
            notification.addAction(
                NotificationAction.createSimpleExpiring(
                    MarimoBundle.message("datasource.stale.restart")
                ) {
                    restartStaleSessions(manager)
                }
            )
            project.putUserData(notificationKey, notification)
            notification.notify(project)
        }
    }

    private fun restartStaleSessions(manager: NotebookSessionManager) {
        manager
            .sessions()
            .filter { it.state.isLive && it.launchEnvStale }
            .mapNotNull { findFile(it.fileUrl) }
            .forEach(manager::restart)
    }

    private fun findFile(fileUrl: String): VirtualFile? =
        VirtualFileManager.getInstance().findFileByUrl(fileUrl)
}
