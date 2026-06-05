package com.github.kirangadhave.marimopycharm.detect

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Cheap, PSI-free check that a file is a marimo notebook. Runs inside FileEditorProvider.accept(),
 * where PSI is not guaranteed available, so we sniff the file header text only.
 */
object MarimoDetector {
    private const val SNIFF_BYTES = 4096

    /** Pure text check — used by tests and by the VirtualFile overload. */
    fun looksLikeMarimo(text: String): Boolean {
        val importAlias = Regex("""import\s+marimo(\s+as\s+(\w+))?""").find(text) ?: return false
        val alias = importAlias.groupValues[2].ifEmpty { "marimo" }
        return text.contains("$alias.App")
    }

    fun looksLikeMarimo(file: VirtualFile): Boolean {
        if (file.extension != "py") return false
        val head = runCatching {
            VfsUtilCore.loadText(file, SNIFF_BYTES)
        }.getOrNull() ?: return false
        return looksLikeMarimo(head)
    }
}
