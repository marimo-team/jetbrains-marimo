/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.source

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

/** Test hook: runs before the refresh is queued at the EDT apply gate. */
internal var beforeSourceRefreshApply: Runnable? = null

/**
 * Reconcile [file] with its on-disk content so an editor showing it is not left stale.
 *
 * The marimo server autosaves edits (e.g. a deleted cell) from within the same IDE window, which
 * never fires the frame-activation event that the platform relies on to refresh externally-changed
 * files. Forcing the refresh reloads the document behind the Source tab.
 *
 * The refresh is skipped while the in-memory document has an unsaved edit. Clean modification stamp
 * changes can come from an earlier queued refresh, so they do not block a later request from
 * reading a newer disk update.
 *
 * The disk scan is asynchronous. The EDT only checks document state and starts the refresh.
 */
internal fun refreshMarimoSourceFromDisk(
    file: VirtualFile,
    onComplete: () -> Unit = {},
) {
    beforeSourceRefreshApply?.run()
    ApplicationManager.getApplication().invokeLater {
        if (!applySourceRefreshIfCurrent(file, onComplete)) onComplete()
    }
}

/**
 * Starts an asynchronous disk refresh unless the loaded document has an unsaved edit. Returns false
 * when the document must keep its in-memory content.
 */
internal fun applySourceRefreshIfCurrent(
    file: VirtualFile,
    onComplete: () -> Unit = {},
): Boolean {
    val documents = FileDocumentManager.getInstance()
    val document = documents.getCachedDocument(file)
    if (document != null && documents.isDocumentUnsaved(document)) {
        return false
    }
    file.refresh(/* asynchronous= */ true, /* recursive= */ false, onComplete)
    return true
}

/**
 * Write the in-memory document for [file] to disk so the marimo server can observe source edits.
 *
 * The file on disk is the only channel between the Source tab and the marimo editor: the server is
 * launched with `--watch` and picks up edits by watching the file's modification time. The platform
 * writes an edited document to disk on frame deactivation, but switching from the Source tab to the
 * notebook tab keeps focus inside the same frame, and idle autosave is off by default — so without
 * this the server never learns that anything changed.
 *
 * The save runs on the next EDT turn so callers inside editor-selection callbacks leave that
 * write-unsafe context first. It does nothing when the file has no loaded document or when the
 * document no longer has unsaved changes.
 */
internal fun flushMarimoSourceToDisk(file: VirtualFile) {
    ApplicationManager.getApplication().invokeLater {
        val documents = FileDocumentManager.getInstance()
        val document = documents.getCachedDocument(file) ?: return@invokeLater
        if (documents.isDocumentUnsaved(document)) documents.saveDocument(document)
    }
}
