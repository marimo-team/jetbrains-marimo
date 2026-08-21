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

    private val MARIMO_IMPORT =
        Regex(
            """(?m)^[\t ]*import[\t ]+marimo(?:[\t ]+as[\t ]+([A-Za-z_][A-Za-z0-9_]*))?[\t ]*(?=,|;|$)"""
        )

    private val LEGACY_MARIMO_IMPORT = Regex("""import\s+marimo(\s+as\s+(\w+))?""")

    /**
     * The file-icon provider asks this for every file the IDE renders (project tree, tabs, nav
     * bar), so the per-file header read is cached and only repeated when the file's content
     * changes.
     */
    private val DETECTION = Key.create<Detection>("marimo.detector.detection")

    private data class Detection(val modificationStamp: Long, val isMarimo: Boolean)

    /** Pure text check — used by tests and by the VirtualFile overload. */
    fun looksLikeMarimo(text: String): Boolean {
        val source = text.take(SNIFF_BYTES)
        return try {
            looksLikeMarimoLexically(source)
        } catch (_: RuntimeException) {
            looksLikeMarimoLegacy(source)
        }
    }

    private fun looksLikeMarimoLexically(text: String): Boolean {
        val source = maskCommentsAndStrings(text)
        return MARIMO_IMPORT.findAll(source).any { match ->
            val alias = match.groupValues[1].ifEmpty { "marimo" }
            val appReference =
                Regex(
                    """(?<![A-Za-z0-9_.])${Regex.escape(alias)}[\t ]*\.[\t ]*App(?![A-Za-z0-9_])"""
                )
            appReference.containsMatchIn(source)
        }
    }

    private fun looksLikeMarimoLegacy(text: String): Boolean {
        val importAlias = LEGACY_MARIMO_IMPORT.find(text) ?: return false
        val alias = importAlias.groupValues[2].ifEmpty { "marimo" }
        return text.contains("$alias.App")
    }

    private fun maskCommentsAndStrings(text: String): String = PythonHeaderMasker(text).mask()

    private enum class MaskState(val delimiter: String) {
        CODE(""),
        COMMENT(""),
        SINGLE("'"),
        DOUBLE("\""),
        TRIPLE_SINGLE("'''"),
        TRIPLE_DOUBLE("\"\"\""),
    }

    private class PythonHeaderMasker(private val source: String) {
        private val masked = StringBuilder(source.length)
        private var index = 0
        private var state = MaskState.CODE
        private var escaped = false

        fun mask(): String {
            while (index < source.length) {
                when (state) {
                    MaskState.CODE -> maskCode()
                    MaskState.COMMENT -> maskComment()
                    else -> maskQuoted()
                }
            }
            return masked.toString()
        }

        private fun maskCode() {
            when {
                source[index] == '#' -> enter(MaskState.COMMENT, 1)
                source.startsWith("'''", index) -> enter(MaskState.TRIPLE_SINGLE, 3)
                source.startsWith("\"\"\"", index) -> enter(MaskState.TRIPLE_DOUBLE, 3)
                source[index] == '\'' -> enter(MaskState.SINGLE, 1)
                source[index] == '"' -> enter(MaskState.DOUBLE, 1)
                else -> masked.append(source[index++])
            }
        }

        private fun maskComment() {
            val char = source[index++]
            if (char == '\n' || char == '\r') {
                masked.append(char)
                state = MaskState.CODE
            } else {
                masked.append(' ')
            }
        }

        private fun maskQuoted() {
            val char = source[index]
            when {
                escaped -> {
                    appendMasked(char)
                    escaped = false
                }
                char == '\\' -> {
                    masked.append(' ')
                    escaped = true
                }
                source.startsWith(state.delimiter, index) -> {
                    maskCharacters(state.delimiter.length)
                    state = MaskState.CODE
                    return
                }
                else -> appendMasked(char)
            }
            index++
        }

        private fun enter(nextState: MaskState, delimiterLength: Int) {
            maskCharacters(delimiterLength)
            state = nextState
        }

        private fun maskCharacters(count: Int) {
            repeat(count) { masked.append(' ') }
            index += count
        }

        private fun appendMasked(char: Char) {
            masked.append(if (char == '\n' || char == '\r') char else ' ')
        }
    }

    fun looksLikeMarimo(file: VirtualFile): Boolean {
        if (file.extension != "py") return false
        val stamp = file.modificationStamp
        file.getUserData(DETECTION)?.let { if (it.modificationStamp == stamp) return it.isMarimo }
        val head =
            runCatching { VfsUtilCore.loadText(file, SNIFF_BYTES) }.getOrNull() ?: return false
        val result = looksLikeMarimo(head)
        file.putUserData(DETECTION, Detection(stamp, result))
        return result
    }
}
