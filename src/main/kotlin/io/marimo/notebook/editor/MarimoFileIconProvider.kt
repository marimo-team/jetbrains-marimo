/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.detect.MarimoDetector
import io.marimo.notebook.server.MarimoServerService
import javax.swing.Icon

/**
 * Marks marimo notebooks in the project tree, with the platform's green live badge while a session
 * is running. Icon painting happens for every visible file, so the status probe must stay
 * side-effect-free: `serviceIfCreated` never instantiates the session manager, and `statusFor`
 * never creates a session.
 */
class MarimoFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (!MarimoDetector.looksLikeMarimo(file)) return null
        val status = project?.serviceIfCreated<MarimoServerService>()?.statusFor(file)
        return if (status?.state?.isLive == true) ExecutionUtil.getLiveIndicator(MarimoIcons.FILE)
        else MarimoIcons.FILE
    }
}
