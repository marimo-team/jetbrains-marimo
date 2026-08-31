/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.source

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class MarimoSourceSyncTest : BasePlatformTestCase() {
    private lateinit var ioFile: File

    override fun tearDown() {
        try {
            beforeSourceRefreshApply = null
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
        val original =
            "import marimo\napp = marimo.App()\n\n\n@app.cell\ndef _():\n    x = 1\n    return\n"
        val updated = "import marimo\napp = marimo.App()\n"

        ioFile = File(FileUtil.createTempDirectory("marimo-sync", null), "nb.py")
        ioFile.writeText(original)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertEquals(original, document.text)

        ioFile.writeText(updated)

        val refreshed = CompletableFuture<Boolean>()
        refreshMarimoSourceFromDisk(file) { refreshed.complete(true) }
        assertTrue(PlatformTestUtil.waitForFuture(refreshed, 5_000))

        assertEquals(updated, document.text)
    }

    fun testQueuedRefreshAfterPriorReloadStillReadsLatestDiskContent() {
        val original = "import marimo\napp = marimo.App()\n"
        val first = "$original# first autosave\n"
        val second = "$original# second autosave\n"

        ioFile = File(FileUtil.createTempDirectory("marimo-sync", null), "nb.py")
        ioFile.writeText(original)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        ioFile.writeText(first)
        val firstRefresh = CompletableFuture<Boolean>()
        refreshMarimoSourceFromDisk(file) { firstRefresh.complete(true) }
        assertTrue(PlatformTestUtil.waitForFuture(firstRefresh, 5_000))
        assertEquals(first, document.text)

        ioFile.writeText(second)
        val secondRefresh = CompletableFuture<Boolean>()
        refreshMarimoSourceFromDisk(file) { secondRefresh.complete(true) }
        assertTrue(PlatformTestUtil.waitForFuture(secondRefresh, 5_000))

        assertEquals(second, document.text)
    }

    /**
     * marimo only learns about source edits by watching the file's modification time, and the
     * platform does not write an edited document to disk when the user merely switches editor tabs
     * (idle autosave is off by default, and moving to the notebook tab never deactivates the IDE
     * frame). Selecting the notebook tab must therefore flush the document itself.
     */
    fun testFlushDefersEditedDocumentSaveToWriteSafeContext() {
        val original = "import marimo\napp = marimo.App()\n"
        val edited =
            "import marimo\napp = marimo.App()\n\n\n@app.cell\ndef _():\n    x = 1\n    return\n"

        ioFile = File(FileUtil.createTempDirectory("marimo-sync", null), "nb.py")
        ioFile.writeText(original)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(edited) }
        assertEquals(original, ioFile.readText())

        flushMarimoSourceToDisk(file)

        assertEquals(
            "the save must be queued outside the selection callback",
            original,
            ioFile.readText(),
        )
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(edited, ioFile.readText())
    }

    /**
     * Selecting Source queues a background disk refresh. If the user types before that refresh
     * runs, the queued reload must not overwrite the in-flight edit.
     */
    fun testRefreshDoesNotOverwriteEditStartedBeforeRefreshCompletes() {
        val original = "import marimo\napp = marimo.App()\n"
        val autosaved = "import marimo\napp = marimo.App()\n# autosaved\n"
        val userEdit = "import marimo\napp = marimo.App()\n# user typed\n"

        ioFile = File(FileUtil.createTempDirectory("marimo-sync", null), "nb.py")
        ioFile.writeText(original)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertEquals(original, document.text)

        ioFile.writeText(autosaved)
        val refreshStarted = CountDownLatch(1)
        val allowRefresh = CountDownLatch(1)
        val refreshThread = Thread {
            refreshStarted.countDown()
            assertTrue(allowRefresh.await(5, TimeUnit.SECONDS))
            refreshMarimoSourceFromDisk(file)
        }
        refreshThread.start()
        assertTrue(refreshStarted.await(5, TimeUnit.SECONDS))

        WriteCommandAction.runWriteCommandAction(project) { document.setText(userEdit) }
        allowRefresh.countDown()
        refreshThread.join(5_000)

        assertEquals(userEdit, document.text)
    }

    /**
     * An edit that begins after the off-EDT stamp pre-check but before the EDT apply gate must not
     * be overwritten by the queued refresh.
     */
    fun testRefreshDoesNotOverwriteEditThatStartsDuringRefreshApply() {
        val original = "import marimo\napp = marimo.App()\n"
        val autosaved = "import marimo\napp = marimo.App()\n# autosaved\n"
        val userEdit = "import marimo\napp = marimo.App()\n# user typed during apply\n"

        ioFile = File(FileUtil.createTempDirectory("marimo-sync", null), "nb.py")
        ioFile.writeText(original)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertEquals(original, document.text)

        ioFile.writeText(autosaved)
        val applyGateEntered = CountDownLatch(1)
        val releaseApply = CountDownLatch(1)
        beforeSourceRefreshApply = Runnable {
            applyGateEntered.countDown()
            assertTrue(releaseApply.await(5, TimeUnit.SECONDS))
        }

        val refreshThread = Thread {
            refreshMarimoSourceFromDisk(file)
        }
        refreshThread.start()
        assertTrue(applyGateEntered.await(5, TimeUnit.SECONDS))

        WriteCommandAction.runWriteCommandAction(project) { document.setText(userEdit) }
        releaseApply.countDown()
        refreshThread.join(5_000)

        assertEquals(userEdit, document.text)
        assertFalse(
            "refresh must skip once the EDT apply gate sees a newer modification stamp",
            applySourceRefreshIfCurrent(file),
        )
    }
}
