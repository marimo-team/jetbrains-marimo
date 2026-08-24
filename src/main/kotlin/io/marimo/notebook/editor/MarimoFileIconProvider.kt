/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.detect.MarimoDetector
import io.marimo.notebook.session.NotebookSessionManager
import javax.swing.Icon

/**
 * Marks marimo notebooks in the project tree. The green live badge shows a live session. Icon reads
 * never create sessions because the platform requests icons for every visible file.
 */
class MarimoFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (!MarimoDetector.looksLikeMarimo(file)) return null
        val status = project?.serviceIfCreated<NotebookSessionManager>()?.peek(file)
        return if (status?.state?.isLive == true) ExecutionUtil.getLiveIndicator(MarimoIcons.FILE)
        else MarimoIcons.FILE
    }
}
