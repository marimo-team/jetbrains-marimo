package com.github.kirangadhave.marimopycharm.launch

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertThrows

class LauncherRegistryTest : BasePlatformTestCase() {
    private fun fake(id: String, can: Boolean) = object : MarimoLauncher {
        override val id = id
        override fun canLaunch(request: LaunchRequest) = can
        override fun launch(request: LaunchRequest) = throw UnsupportedOperationException()
    }

    private val request: LaunchRequest
        get() = LaunchRequest(project = project, notebook = LightVirtualFile("nb.py"), port = 0)

    fun testPicksFirstApplicableInOrder() {
        val registry = LauncherRegistry(listOf(fake("a", false), fake("b", true), fake("c", true)))
        assertEquals("b", registry.resolve(request).id)
    }

    fun testThrowsWhenNoneApplicable() {
        val registry = LauncherRegistry(listOf(fake("a", false)))
        assertThrows(NoApplicableLauncherException::class.java) { registry.resolve(request) }
    }
}
