/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.vfs.VirtualFile

/**
 * Reconcile [file] with its on-disk content so an editor showing it is not left stale.
 *
 * The marimo server autosaves edits (e.g. a deleted cell) from within the same IDE window, which
 * never fires the frame-activation event that the platform relies on to refresh externally-changed
 * files. Forcing the refresh reloads the document behind the Source tab.
 *
 * The refresh is synchronous, so callers must invoke it off the EDT; the VFS events it produces are
 * still applied on the EDT, but the (potentially slow, on remote filesystems) disk scan is not.
 */
internal fun refreshMarimoSourceFromDisk(file: VirtualFile) {
    file.refresh(/* asynchronous = */ false, /* recursive = */ false)
}
