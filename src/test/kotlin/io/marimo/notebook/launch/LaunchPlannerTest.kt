/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LaunchPlannerTest : BasePlatformTestCase() {
    private fun launcher(can: Boolean) = object : MarimoLauncher {
        override val id = "sdk"
        override fun canLaunch(request: LaunchRequest) = can
        override fun launch(request: LaunchRequest) = throw UnsupportedOperationException()
        override fun marimoCliPrefix(request: LaunchRequest): List<String>? = null
    }

    private val request: LaunchRequest
        get() = LaunchRequest(project = project, notebook = LightVirtualFile("nb.py"), port = 0)

    fun testLaunchesOnConfiguredInterpreter() {
        val decision = LaunchPlanner(launcher(can = true)).plan(request)
        val launch = assertInstanceOf(decision, LaunchDecision.Launch::class.java)
        assertEquals("sdk", launch.launcher.id)
    }

    fun testNoInterpreterWhenNoneResolves() {
        val decision = LaunchPlanner(launcher(can = false)).plan(request)
        assertInstanceOf(decision, LaunchDecision.NoInterpreter::class.java)
    }
}
