/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.datasource.ide.IdeDataSourceCatalog
import io.marimo.notebook.launch.LaunchEnvContribution
import io.marimo.notebook.launch.LaunchEnvContributor

/** Feeds consented IDE data sources into each marimo launch for this project. */
class DataSourceEnvContributor : LaunchEnvContributor {
    override fun contribute(project: Project, notebook: VirtualFile): LaunchEnvContribution? {
        val store = DataSourceExposureStore.getInstance(project)
        if (store.neverForThisProject()) return null
        val exposedIds = store.exposedIds()
        if (exposedIds.isEmpty()) return null

        val exposed =
            IdeDataSourceCatalog.candidates(project).filter { it.supported && it.id in exposedIds }
        if (exposed.isEmpty()) return null

        val primaries = Candidates.effectivePrimaries(exposed, store.primaryIds())
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
                password = IdeDataSourceCatalog.password(project, candidate.id),
                familyPrimary = candidate.id in primaries,
            )
        }
        val built = DataSourceEnvBuilder.build(sources)
        return LaunchEnvContribution(built.env, built.labels)
    }
}
