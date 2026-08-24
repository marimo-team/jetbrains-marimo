/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MarimoPairSessionTest : BasePlatformTestCase() {

    private lateinit var sdk: FakeLauncher
    private lateinit var uv: FakeLauncher

    private val manager: NotebookSessionManager
        get() = project.service<NotebookSessionManager>()

    override fun setUp() {
        super.setUp()
        sdk = FakeLauncher("fake-sdk").also { it.canLaunch = false }
        uv = FakeLauncher("fake-uv")
        manager.planner = LaunchPlanner(sdk, uv)
    }

    override fun tearDown() {
        try {
            manager.sessions().forEach { manager.stopUrl(it.fileUrl) }
        } finally {
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

    private fun notebook(): VirtualFile =
        myFixture.addFileToProject("pair_prompt.py", "import marimo\n").virtualFile
}
