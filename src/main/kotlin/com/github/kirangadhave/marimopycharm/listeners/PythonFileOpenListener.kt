package com.github.kirangadhave.marimopycharm.listeners

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class PythonFileOpenListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!isMarimoNotebook(file)) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification("Hello", "marimo notebook: ${file.name}", NotificationType.INFORMATION)
            .notify(source.project)
    }

    private fun isMarimoNotebook(file: VirtualFile): Boolean {
        if (file.extension != "py") return false
        val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: return false
        return text.contains("import marimo") && text.contains("marimo.App")
    }

    private companion object {
        const val NOTIFICATION_GROUP_ID = "marimo.notifications"
    }
}
