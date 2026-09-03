/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JLabel
import org.junit.Assert.assertEquals

class MarimoToolWindowTabProviderTest : BasePlatformTestCase() {
    fun testRegisteredProvidersBecomeToolWindowTabs() {
        val provider =
            object : MarimoToolWindowTabProvider {
                override val id = "test-tab"
                override val title = "Test tab"

                override fun createComponent(
                    project: Project,
                    selectedNotebook: () -> VirtualFile?,
                ) = JLabel("test component")
            }
        ExtensionTestUtil.maskExtensions(
            MarimoToolWindowTabProvider.EP_NAME,
            listOf(provider),
            testRootDisposable,
        )

        val tab = MarimoToolWindowTabProvider.registeredTabs(project) { null }.single()

        assertEquals("test-tab", tab.id)
        assertEquals("Test tab", tab.title)
        assertEquals("test component", (tab.component as JLabel).text)
    }
}
