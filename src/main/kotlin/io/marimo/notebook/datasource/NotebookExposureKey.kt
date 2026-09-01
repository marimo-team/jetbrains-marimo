/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Path

/** Resolves the stable notebook key used by the project exposure store. */
internal object NotebookExposureKey {
    fun from(project: Project, notebook: VirtualFile): String? =
        relativePath(project.basePath, notebook.path)

    fun relativePath(projectPath: String?, notebookPath: String): String? {
        if (projectPath.isNullOrBlank()) return null
        val root = Path.of(projectPath).toAbsolutePath().normalize()
        val notebook = Path.of(notebookPath).toAbsolutePath().normalize()
        if (!notebook.startsWith(root) || notebook == root) return null
        return root.relativize(notebook).toString().replace(File.separatorChar, '/')
    }
}
