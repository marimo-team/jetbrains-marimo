/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource.ide

import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import io.marimo.notebook.datasource.DataSourceEvent
import io.marimo.notebook.datasource.DataSourceStaleness

/** Forwards Database Tools storage events to notebook launch-environment staleness. */
class DataSourceStorageListener(private val project: Project) : DataSourceStorage.Listener {
    override fun dataSourceAdded(dataSource: LocalDataSource) {
        DataSourceStaleness.apply(project, DataSourceEvent.Added(dataSource.uniqueId))
    }

    override fun dataSourceRemoved(dataSource: LocalDataSource) {
        DataSourceStaleness.apply(project, DataSourceEvent.Removed(dataSource.uniqueId))
    }

    override fun dataSourceChanged(dataSource: LocalDataSource?, isModelChanged: Boolean) {
        DataSourceStaleness.apply(project, DataSourceEvent.Changed(dataSource?.uniqueId))
    }
}
