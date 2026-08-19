/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAware
import com.intellij.packageDependencies.ui.PackageDependenciesNode
import com.intellij.ui.ColoredTreeCellRenderer
import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.detect.MarimoDetector
import io.marimo.notebook.server.MarimoServerService

/**
 * Adds marimo-specific presentation in the project tree:
 * - marimo icon for detected notebooks
 * - right-side green dot while a session is active
 */
class MarimoProjectViewNodeDecorator : ProjectViewNodeDecorator, DumbAware {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (!MarimoDetector.looksLikeMarimo(file)) return

        val service = node.project.serviceIfCreated<MarimoServerService>()
        val isLive = service?.statusFor(file)?.state?.isLive == true
        data.setIcon(if (isLive) ExecutionUtil.getLiveIndicator(MarimoIcons.FILE) else MarimoIcons.FILE)
        data.locationString = null
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun decorate(node: PackageDependenciesNode?, cellRenderer: ColoredTreeCellRenderer?) {
        // We use PresentationData decoration only.
    }
}
