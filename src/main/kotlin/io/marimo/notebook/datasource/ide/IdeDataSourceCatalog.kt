/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource.ide

import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.openapi.project.Project
import io.marimo.notebook.datasource.CandidateDataSource
import io.marimo.notebook.datasource.Candidates
import io.marimo.notebook.datasource.IdeDataSourceFacts

/**
 * Reads Database Tools state. Call this object only from pooled threads because credential storage
 * can block. This package loads only through `marimo-database.xml`.
 */
object IdeDataSourceCatalog {

    fun candidates(project: Project): List<CandidateDataSource> =
        DataSourceStorage.getProjectStorage(project).dataSources.map { dataSource ->
            Candidates.from(
                IdeDataSourceFacts(
                    id = dataSource.uniqueId,
                    displayName = dataSource.name,
                    url = dataSource.url,
                    username = dataSource.username,
                    authProviderId = dataSource.authProviderId,
                )
            )
        }

    /** Returns the stored password for one data source. */
    fun password(project: Project, dataSourceId: String): String? {
        val dataSource =
            DataSourceStorage.getProjectStorage(project).dataSources.firstOrNull {
                it.uniqueId == dataSourceId
            } ?: return null
        return DatabaseCredentials.getInstance().loadPassword(dataSource)?.toString()
    }
}
