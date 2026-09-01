/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.project.Project

/** Applies staleness after exposure edits. Task 8 wires the session flag and the notification. */
object DataSourceStaleness {
    fun exposureEdited(project: Project, notebookKey: String) = Unit
}
