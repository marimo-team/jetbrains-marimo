/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.server.MarimoServerService
import io.marimo.notebook.server.MarimoSessionManagerTest.FakeLauncher
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame

class MarimoFileIconProviderTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            val service = project.service<MarimoServerService>()
            service.sessions().forEach { service.stopUrl(it.fileUrl) }
        } finally {
            super.tearDown()
        }
    }

    fun testLiveSessionBadgesTheIconAndProbingCreatesNothing() {
        val service = project.service<MarimoServerService>()
        val sdk = FakeLauncher()
        service.planner = LaunchPlanner(sdk, sdk)
        val file = myFixture.addFileToProject("icon_nb.py", "import marimo\napp = marimo.App()\n").virtualFile
        val provider = MarimoFileIconProvider()

        assertSame("no session: the plain file icon", MarimoIcons.FILE, provider.getIcon(file, 0, project))
        assertNull("painting an icon must never create a session", service.statusFor(file))

        service.urlFor(file)
        sdk.handles.single().becomeReady()
        assertNotSame("a live session must show a badge", MarimoIcons.FILE, provider.getIcon(file, 0, project))

        service.stop(file)
        assertSame("a removed session goes back to the plain icon", MarimoIcons.FILE, provider.getIcon(file, 0, project))
    }
}
