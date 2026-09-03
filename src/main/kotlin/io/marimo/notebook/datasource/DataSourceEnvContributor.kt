/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.datasource.ide.IdeDataSourceCatalog
import io.marimo.notebook.launch.LaunchEnvContribution
import io.marimo.notebook.launch.LaunchEnvContributor

/** Feeds consented IDE data sources into the selected marimo notebook launch. */
class DataSourceEnvContributor(
    private val candidates: (Project) -> List<CandidateDataSource> =
        IdeDataSourceCatalog::candidates,
    private val password: (Project, String) -> String? = IdeDataSourceCatalog::password,
    private val notebookKey: (Project, VirtualFile) -> String? = NotebookExposureKey::from,
) : LaunchEnvContributor {
    override fun contribute(project: Project, notebook: VirtualFile): LaunchEnvContribution? {
        val notebookKey = notebookKey(project, notebook) ?: return null
        val store = DataSourceExposureStore.getInstance(project)
        if (store.neverForThisProject()) return null
        if (!store.decisionRecorded(notebookKey)) {
            val mappable = candidates(project).count { it.supported }
            if (mappable > 0) DataSourceConsentPrompt.offer(project, notebookKey, mappable)
            return null
        }
        val exposedIds = store.exposedIds(notebookKey)
        if (exposedIds.isEmpty()) return null

        val exposed = candidates(project).filter { it.supported && it.id in exposedIds }
        if (exposed.isEmpty()) return null

        val defaults = Candidates.effectiveDefaults(exposed, store.defaultIds(notebookKey))
        val sources = exposed.map { candidate ->
            val endpoint = requireNotNull(candidate.endpoint)
            ExposedDataSource(
                displayName = candidate.displayName,
                family = candidate.family,
                dialect = candidate.dialect,
                host = endpoint.host,
                port = endpoint.port,
                username = candidate.username,
                database = endpoint.database,
                schema = endpoint.schema,
                password = if (candidate.id in defaults) password(project, candidate.id) else null,
                familyDefault = candidate.id in defaults,
            )
        }
        val built = DataSourceEnvBuilder.build(sources)
        return LaunchEnvContribution(built.env, built.labels)
    }
}
