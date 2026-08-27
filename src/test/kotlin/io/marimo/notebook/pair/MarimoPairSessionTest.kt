/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher
import io.marimo.notebook.session.SessionManagerSeams
import io.marimo.notebook.session.SessionSettings
import io.marimo.notebook.session.TtlCancellable
import io.marimo.notebook.session.TtlScheduler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class MarimoPairSessionTest : BasePlatformTestCase() {

    private class RecordingTtl : TtlScheduler {
        val armed = CompletableFuture<Long>()

        override fun schedule(delayMillis: Long, task: Runnable): TtlCancellable {
            armed.complete(delayMillis)
            return TtlCancellable {}
        }
    }

    private lateinit var sdk: FakeLauncher
    private lateinit var uv: FakeLauncher
    private lateinit var seams: SessionManagerSeams

    private val manager: NotebookSessionManager
        get() = project.service<NotebookSessionManager>()

    override fun setUp() {
        super.setUp()
        seams = SessionManagerSeams(manager)
        sdk = FakeLauncher("fake-sdk").also { it.canLaunch = false }
        uv = FakeLauncher("fake-uv")
        manager.planner = LaunchPlanner(sdk, uv)
    }

    override fun tearDown() {
        try {
            manager.sessions().forEach { manager.stopUrl(it.fileUrl) }
        } finally {
            seams.restore()
            super.tearDown()
        }
    }

    fun testPromptUsesTheActiveSandboxLauncherWithoutReplanning() {
        val file = notebook()
        manager.enableSandbox(file)
        val prefix = CompletableFuture<List<String>>()

        MarimoPairSession.resolvePrompt(project, file) { _, activePrefix, closeLease ->
            try {
                prefix.complete(activePrefix)
            } finally {
                closeLease()
            }
        }

        val launched = CompletableFuture.supplyAsync { uv.firstLaunch.await(5, TimeUnit.SECONDS) }
        assertTrue("sandbox launch did not begin", PlatformTestUtil.waitForFuture(launched, 5_000))
        uv.canLaunch = false
        uv.handles.single().becomeReady()

        assertEquals(listOf("fake-uv", "marimo"), PlatformTestUtil.waitForFuture(prefix, 5_000))
    }

    fun testTerminalUsesTheActiveSandboxLauncherWithoutReplanning() {
        val file = notebook()
        manager.enableSandbox(file)
        val prefix = CompletableFuture<List<String>>()

        MarimoPairSession.resolveTerminal(project, file) { _, activePrefix, closeLease ->
            try {
                prefix.complete(activePrefix)
            } finally {
                closeLease()
            }
        }

        val launched = CompletableFuture.supplyAsync { uv.firstLaunch.await(5, TimeUnit.SECONDS) }
        assertTrue("sandbox launch did not begin", PlatformTestUtil.waitForFuture(launched, 5_000))
        uv.canLaunch = false
        uv.handles.single().becomeReady()

        assertEquals(listOf("fake-uv", "marimo"), PlatformTestUtil.waitForFuture(prefix, 5_000))
    }

    fun testPromptLaunchFailureClosesItsLeaseAndArmsTheTtl() {
        val file = notebook()
        val ttl = RecordingTtl()
        manager.ttlScheduler = ttl
        sdk.canLaunch = false
        uv.canLaunch = false

        MarimoPairSession.resolvePrompt(project, file) { _, _, _ ->
            error("prompt must not be delivered")
        }

        assertEquals(
            "a failed prompt must release its non-suppressing lease",
            SessionSettings.getInstance().backgroundTtlMillis(),
            PlatformTestUtil.waitForFuture(ttl.armed, 5_000),
        )
        assertNotNull(manager.peek(file)!!.expiresAtMillis)
    }

    fun testTerminalLaunchFailureClosesItsLeaseAndArmsTheTtl() {
        val file = notebook()
        val ttl = RecordingTtl()
        manager.ttlScheduler = ttl
        sdk.canLaunch = false
        uv.canLaunch = false

        MarimoPairSession.resolveTerminal(project, file) { _, _, _ ->
            error("terminal must not be delivered")
        }

        assertEquals(
            "a failed terminal must release its suppressing lease",
            SessionSettings.getInstance().backgroundTtlMillis(),
            PlatformTestUtil.waitForFuture(ttl.armed, 5_000),
        )
        assertNotNull(manager.peek(file)!!.expiresAtMillis)
    }

    private fun notebook(): VirtualFile =
        myFixture.addFileToProject("pair_prompt.py", "import marimo\n").virtualFile
}
