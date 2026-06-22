/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.detect

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Cheap, PSI-free check that a file is a marimo notebook. Runs inside FileEditorProvider.accept(),
 * where PSI is not guaranteed available, so we sniff the file header text only.
 */
object MarimoDetector {
    private const val SNIFF_BYTES = 4096

    /**
     * The file-icon provider asks this for every file the IDE renders (project tree, tabs, nav bar),
     * so the per-file header read is cached and only repeated when the file's content changes.
     */
    private val DETECTION = Key.create<Detection>("marimo.detector.detection")

    private data class Detection(val modificationStamp: Long, val isMarimo: Boolean)

    /** Pure text check — used by tests and by the VirtualFile overload. */
    fun looksLikeMarimo(text: String): Boolean {
        val importAlias = Regex("""import\s+marimo(\s+as\s+(\w+))?""").find(text) ?: return false
        val alias = importAlias.groupValues[2].ifEmpty { "marimo" }
        return text.contains("$alias.App")
    }

    fun looksLikeMarimo(file: VirtualFile): Boolean {
        if (file.extension != "py") return false
        val stamp = file.modificationStamp
        file.getUserData(DETECTION)?.let { if (it.modificationStamp == stamp) return it.isMarimo }
        val head = runCatching { VfsUtilCore.loadText(file, SNIFF_BYTES) }.getOrNull() ?: return false
        val result = looksLikeMarimo(head)
        file.putUserData(DETECTION, Detection(stamp, result))
        return result
    }
}
