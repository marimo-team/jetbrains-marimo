/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Environment entries for one marimo server launch and user-visible labels for the Sessions tool
 * window. Labels carry names only. Environment values can hold credentials, so never log or render
 * this type.
 */
data class LaunchEnvContribution(
    val env: Map<String, String>,
    val labels: List<String> = emptyList(),
)

/** Contributes environment entries to each marimo server launch for a project. */
interface LaunchEnvContributor {
    /**
     * Runs on a pooled thread during launch. Implementations can read slow storage, such as the IDE
     * credential store, but must not use the EDT.
     */
    fun contribute(project: Project, notebook: VirtualFile): LaunchEnvContribution?

    companion object {
        val EP_NAME: ExtensionPointName<LaunchEnvContributor> =
            ExtensionPointName.create("io.marimo.notebook.launchEnvContributor")

        /** Merges all contributions. The first contributor wins for duplicate variable names. */
        fun collect(project: Project, notebook: VirtualFile): LaunchEnvContribution {
            val env = LinkedHashMap<String, String>()
            val labels = mutableListOf<String>()
            for (contributor in EP_NAME.extensionList) {
                val contribution = contributor.contribute(project, notebook) ?: continue
                contribution.env.forEach { (name, value) -> env.putIfAbsent(name, value) }
                labels += contribution.labels
            }
            return LaunchEnvContribution(env, labels)
        }
    }
}
