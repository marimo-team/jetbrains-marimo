/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

/**
 * Retains only the tail of a long-lived process stream so noisy notebooks cannot grow IDE heap
 * without bound. When older bytes are dropped, a short overlap is kept so markers that straddle a
 * truncation boundary — access tokens and the unsupported-`--watch` message — remain detectable.
 */
internal class BoundedProcessOutput(
    private val capacityChars: Int = CAPACITY_CHARS,
    private val markerOverlapChars: Int = MARKER_OVERLAP_CHARS,
) {
    private val buffer = StringBuilder()

    fun append(text: String) {
        if (text.isEmpty()) return
        synchronized(buffer) {
            buffer.append(text)
            trimIfNeeded()
        }
    }

    fun snapshot(): String = synchronized(buffer) { buffer.toString() }

    private fun trimIfNeeded() {
        val maxStored = capacityChars + markerOverlapChars
        if (buffer.length <= maxStored) return
        val dropEnd = buffer.length - capacityChars
        val dropStart = (dropEnd - markerOverlapChars).coerceAtLeast(0)
        val overlap = buffer.substring(dropStart, dropEnd)
        buffer.delete(0, dropEnd)
        buffer.insert(0, overlap)
    }

    companion object {
        const val CAPACITY_CHARS = 16 * 1024
        const val MARKER_OVERLAP_CHARS = 256
    }
}
