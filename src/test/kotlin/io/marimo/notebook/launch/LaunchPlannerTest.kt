/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LaunchPlannerTest : BasePlatformTestCase() {
    private fun launcher(id: String, can: Boolean) = object : MarimoLauncher {
        override val id = id
        override fun canLaunch(request: LaunchRequest) = can
        override fun launch(request: LaunchRequest) = throw UnsupportedOperationException()
        override fun marimoCliPrefix(request: LaunchRequest): List<String>? = null
    }

    private fun planner(sdkCan: Boolean, uvCan: Boolean) =
        LaunchPlanner(launcher("sdk", sdkCan), launcher("uv", uvCan))

    private fun request(sandbox: Boolean = false): LaunchRequest =
        LaunchRequest(project = project, notebook = LightVirtualFile("nb.py"), port = 0, sandbox = sandbox)

    fun testLaunchesOnConfiguredInterpreter() {
        val decision = planner(sdkCan = true, uvCan = false).plan(request())
        val launch = assertInstanceOf(decision, LaunchDecision.Launch::class.java)
        assertEquals("sdk", launch.launcher.id)
    }

    fun testNoInterpreterWhenNoneResolves() {
        val decision = planner(sdkCan = false, uvCan = true).plan(request())
        assertInstanceOf(decision, LaunchDecision.NoInterpreter::class.java)
    }

    fun testSandboxRoutesToUvWhenAvailable() {
        val decision = planner(sdkCan = true, uvCan = true).plan(request(sandbox = true))
        val launch = assertInstanceOf(decision, LaunchDecision.Launch::class.java)
        assertEquals("uv", launch.launcher.id)
    }

    fun testSandboxNeedsUvWhenUvMissing() {
        val decision = planner(sdkCan = true, uvCan = false).plan(request(sandbox = true))
        assertInstanceOf(decision, LaunchDecision.NeedsUv::class.java)
    }
}
