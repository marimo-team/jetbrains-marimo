/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Reconcile [file] with its on-disk content so an editor showing it is not left stale.
 *
 * The marimo server autosaves edits (e.g. a deleted cell) from within the same IDE window, which
 * never fires the frame-activation event that the platform relies on to refresh externally-changed
 * files. Forcing the refresh reloads the document behind the Source tab.
 *
 * When [modificationStamp] is set, the refresh is skipped if the in-memory document changed after
 * the caller captured the stamp (for example, the user began typing while a queued refresh was
 * still waiting to run).
 *
 * The refresh is synchronous, so callers must invoke it off the EDT; the VFS events it produces are
 * still applied on the EDT, but the (potentially slow, on remote filesystems) disk scan is not.
 */
internal fun refreshMarimoSourceFromDisk(file: VirtualFile, modificationStamp: Long? = null) {
    val document = FileDocumentManager.getInstance().getCachedDocument(file)
    if (
        document != null &&
            modificationStamp != null &&
            document.modificationStamp != modificationStamp
    ) {
        return
    }
    file.refresh(/* asynchronous= */ false, /* recursive= */ false)
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
 * Does nothing when the file has no loaded document (the Source tab was never opened) or when the
 * document has no unsaved changes. Must be called on the EDT.
 */
internal fun flushMarimoSourceToDisk(file: VirtualFile) {
    val documents = FileDocumentManager.getInstance()
    val document = documents.getCachedDocument(file) ?: return
    documents.saveDocument(document)
}
