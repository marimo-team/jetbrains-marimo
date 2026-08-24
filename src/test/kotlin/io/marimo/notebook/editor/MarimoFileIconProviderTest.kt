/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher
import io.marimo.notebook.session.SessionSettings
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

class MarimoFileIconProviderTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            val service = project.service<NotebookSessionManager>()
            service.sessions().forEach { service.stopUrl(it.fileUrl) }
        } finally {
            super.tearDown()
        }
    }

    fun testLiveSessionBadgesTheIconAndProbingCreatesNothing() {
        val service = project.service<NotebookSessionManager>()
        val sdk = FakeLauncher()
        service.planner = LaunchPlanner(sdk, sdk)
        val file =
            myFixture
                .addFileToProject("icon_nb.py", "import marimo\napp = marimo.App()\n")
                .virtualFile
        val provider = MarimoFileIconProvider()
        val settings = SessionSettings.getInstance()
        val priorTokenAuth = settings.state.tokenAuthEnabled

        settings.state.tokenAuthEnabled = false
        try {
            assertSame(
                "no session: the plain file icon",
                MarimoIcons.FILE,
                provider.getIcon(file, 0, project),
            )
            assertNull("painting an icon must never create a session", service.statusFor(file))

            service.urlFor(file)
            val launched = CompletableFuture.supplyAsync {
                sdk.firstLaunch.await(5, TimeUnit.SECONDS)
            }
            assertTrue("launch did not begin", PlatformTestUtil.waitForFuture(launched, 5_000))
            sdk.handles.single().becomeReady()
            assertNotSame(
                "a live session must show a badge",
                MarimoIcons.FILE,
                provider.getIcon(file, 0, project),
            )

            service.stop(file)
            assertSame(
                "a removed session goes back to the plain icon",
                MarimoIcons.FILE,
                provider.getIcon(file, 0, project),
            )
        } finally {
            settings.state.tokenAuthEnabled = priorTokenAuth
        }
    }
}
