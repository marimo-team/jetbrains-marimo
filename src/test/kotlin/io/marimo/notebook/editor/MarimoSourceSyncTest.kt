/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class MarimoSourceSyncTest : BasePlatformTestCase() {
    private lateinit var ioFile: File

    override fun tearDown() {
        try {
            if (::ioFile.isInitialized) FileUtil.delete(ioFile.parentFile)
        } finally {
            super.tearDown()
        }
    }

    /**
     * The marimo server autosaves cell deletions to disk from within the same IDE window, so no
     * frame-activation event fires to trigger the platform's default refresh. Selecting the Source
     * tab must reconcile the in-memory document with the new on-disk content.
     */
    fun testRefreshReloadsDocumentAfterExternalWrite() {
        val original = "import marimo\napp = marimo.App()\n\n\n@app.cell\ndef _():\n    x = 1\n    return\n"
        val updated = "import marimo\napp = marimo.App()\n"

        ioFile = File(FileUtil.createTempDirectory("marimo-sync", null), "nb.py")
        ioFile.writeText(original)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertEquals(original, document.text)

        ioFile.writeText(updated)

        refreshMarimoSourceFromDisk(file)

        assertEquals(updated, document.text)
    }
}
