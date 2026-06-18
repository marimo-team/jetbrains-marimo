/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI

/**
 * Installs marimo into the interpreter PyCharm resolves for a notebook, then re-probes presence. The
 * package manager dispatches by SDK flavor (pip / uv / conda), so the same call works for every
 * interpreter type. Relaunching the editor after a successful install is the caller's responsibility.
 */
@Service(Service.Level.PROJECT)
class MarimoInstaller(private val project: Project) {

    /**
     * Show modal install progress for marimo on [notebook]'s interpreter and return the freshly probed
     * presence. Returns [MarimoPresence.Unknown] when no interpreter is configured to install into.
     * Must be called on the EDT, since the install shows a modal progress dialog.
     */
    fun installMarimo(notebook: VirtualFile): MarimoPresence {
        val sdk = SdkPythonResolver.resolveSdk(project, notebook) ?: return MarimoPresence.Unknown
        PythonPackageManagerUI.forSdk(project, sdk).installPackagesWithModalProgressBlocking(MARIMO)
        val probe = project.service<MarimoEnvProbe>()
        probe.invalidate()
        return probe.probe(notebook)
    }

    companion object {
        private const val MARIMO = "marimo"
    }
}
