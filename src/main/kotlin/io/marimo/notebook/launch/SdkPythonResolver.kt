/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.PythonSdkUtil

/**
 * Resolves the Python interpreter PyCharm has configured for [notebook] — the module SDK when the file
 * belongs to a module, else the project SDK. Returns the interpreter executable path (the home path of a
 * Python SDK), or null when no Python interpreter is configured, in which case the registry falls through
 * to the uv launcher.
 */
object SdkPythonResolver {
    fun resolvePythonPath(project: Project, notebook: VirtualFile): String? {
        val sdk = moduleSdk(project, notebook)
            ?: ProjectRootManager.getInstance(project).projectSdk
        if (sdk == null || !PythonSdkUtil.isPythonSdk(sdk)) return null
        return sdk.homePath
    }

    private fun moduleSdk(project: Project, notebook: VirtualFile): Sdk? {
        val module = ModuleUtilCore.findModuleForFile(notebook, project) ?: return null
        return ModuleRootManager.getInstance(module).sdk
    }
}
