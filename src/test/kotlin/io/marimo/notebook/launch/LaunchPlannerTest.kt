/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertThrows

class LaunchPlannerTest : BasePlatformTestCase() {
    private fun launcher(id: String, can: Boolean) =
        object : MarimoLauncher {
            override val id = id

            override fun canLaunch(request: LaunchRequest) = can

            override fun launch(request: LaunchRequest) = throw UnsupportedOperationException()

            override fun marimoCliPrefix(request: LaunchRequest): List<String>? = null
        }

    private fun planner(sdkCan: Boolean, uvCan: Boolean) =
        LaunchPlanner(launcher("sdk", sdkCan), launcher("uv", uvCan))

    private fun request(sandbox: Boolean = false): LaunchRequest =
        LaunchRequest(
            project = project,
            notebook = LightVirtualFile("nb.py"),
            port = 0,
            sandbox = sandbox,
        )

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

    fun testLaunchRequestStringDoesNotRevealSecretValues() {
        val request =
            LaunchRequest(
                project = project,
                notebook = LightVirtualFile("nb.py"),
                port = 2718,
                authenticatedUrl = "http://127.0.0.1:2718/?access_token=url-secret",
                extraEnv = mapOf("PGPASSWORD" to "env-secret"),
            )

        val rendered = request.toString()

        assertFalse(rendered.contains("url-secret"))
        assertFalse(rendered.contains("env-secret"))
        assertTrue(rendered.contains("extraEnvKeys=[PGPASSWORD]"))
    }

    fun testLaunchEnvContributionStringDoesNotRevealSecretValues() {
        val contribution =
            LaunchEnvContribution(
                env = mapOf("PGPASSWORD" to "env-secret"),
                labels = listOf("Orders DB"),
            )

        val rendered = contribution.toString()

        assertFalse(rendered.contains("env-secret"))
        assertTrue(rendered.contains("envKeys=[PGPASSWORD]"))
        assertTrue(rendered.contains("labels=[Orders DB]"))
    }

    fun testLaunchEnvContributionSnapshotIsDefensiveAndUnmodifiable() {
        val sourceEnv = linkedMapOf("PGPASSWORD" to "secret")
        val sourceLabels = mutableListOf("Orders DB")
        val contribution = immutableLaunchEnvContribution(sourceEnv, sourceLabels)

        sourceEnv["PGHOST"] = "db.internal"
        sourceLabels += "Reporting DB"

        assertEquals(setOf("PGPASSWORD"), contribution.env.keys)
        assertEquals(listOf("Orders DB"), contribution.labels)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (contribution.env as MutableMap<String, String>)["PGHOST"] = "other"
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (contribution.labels as MutableList<String>) += "Other DB"
        }
    }
}
