/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

/**
 * The directory the marimo server process starts in. The kernel inherits it, and relative paths in
 * notebook code resolve against it, so the choice must match what `marimo edit` sees when run from
 * the project root: users' `sys.path.append("src")` style code works in a terminal and must work
 * here too (MO-7022). Resolution order: the content root that contains the notebook, the project
 * base path, the notebook's own directory, the IDE process working directory.
 */
object NotebookWorkDir {
    fun resolve(project: Project, notebook: VirtualFile): String =
        ReadAction.computeBlocking<String?, RuntimeException> {
            ProjectFileIndex.getInstance(project).getContentRootForFile(notebook)?.path
                ?: project.basePath
                ?: notebook.parent?.path
        } ?: System.getProperty("user.dir")
}
