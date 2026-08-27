/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.sdk.PythonSdkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class SdkPythonResolverTest : BasePlatformTestCase() {
    private val installedSdks = mutableListOf<Sdk>()

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = null
                val table = ProjectJdkTable.getInstance()
                installedSdks.forEach(table::removeJdk)
            }
        } finally {
            super.tearDown()
        }
    }

    fun testResolvedPathUsesTheProjectPythonSdk() {
        installProjectSdk(pythonSdk("/opt/venvs/nb/bin/python"))
        val notebook = LightVirtualFile("nb.py", "import marimo\n")
        assertEquals(
            "/opt/venvs/nb/bin/python",
            SdkPythonResolver.resolvePythonPath(project, notebook),
        )
    }

    fun testMissingSdkReturnsNull() {
        val notebook = LightVirtualFile("nb.py", "import marimo\n")
        assertNull(SdkPythonResolver.resolvePythonPath(project, notebook))
        assertNull(SdkPythonResolver.resolveSdk(project, notebook))
    }

    fun testNonPythonSdkReturnsNull() {
        installProjectSdk(nonPythonSdk("/Library/Java/Home"))
        val notebook = LightVirtualFile("nb.py", "import marimo\n")
        assertNull(SdkPythonResolver.resolvePythonPath(project, notebook))
        assertNull(SdkPythonResolver.resolveSdk(project, notebook))
    }

    private fun pythonSdk(homePath: String): Sdk =
        createSdk("marimo-test-python", PythonSdkType.getInstance(), homePath)

    private fun nonPythonSdk(homePath: String): Sdk =
        createSdk("marimo-test-jdk", UnknownSdkType.getInstance("MarimoTestNonPython"), homePath)

    private fun createSdk(name: String, type: SdkType, homePath: String): Sdk {
        val sdk = ProjectJdkTable.getInstance().createSdk(name, type)
        val modificator = sdk.sdkModificator
        modificator.homePath = homePath
        ApplicationManager.getApplication().runWriteAction { modificator.commitChanges() }
        return sdk
    }

    private fun installProjectSdk(sdk: Sdk) {
        ApplicationManager.getApplication().runWriteAction {
            ProjectJdkTable.getInstance().addJdk(sdk)
            installedSdks.add(sdk)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
    }
}
