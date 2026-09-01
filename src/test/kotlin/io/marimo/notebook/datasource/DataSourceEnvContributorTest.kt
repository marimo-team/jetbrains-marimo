/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DataSourceEnvContributorTest : BasePlatformTestCase() {
    fun testContributesOnlyToTheSharedNotebook() {
        val orders =
            myFixture.addFileToProject("notebooks/orders.py", "print('orders')").virtualFile
        val report =
            myFixture.addFileToProject("notebooks/report.py", "print('report')").virtualFile
        val candidate =
            Candidates.from(
                IdeDataSourceFacts(
                    id = "pg-1",
                    displayName = "Orders",
                    url = "jdbc:postgresql://127.0.0.1:5432/orders",
                    username = "orders_app",
                    authProviderId = "user-pass",
                )
            )
        val entry =
            DataSourceExposureStore.ExposureEntry().apply {
                dataSourceId = candidate.id
                exposed = true
                familyPrimary = true
            }
        val store = DataSourceExposureStore.getInstance(project)
        val ordersKey = "notebooks/orders.py"
        store.recordExposures(ordersKey, listOf(entry))
        val contributor =
            DataSourceEnvContributor(
                candidates = { listOf(candidate) },
                password = { _, _ -> "secret" },
                notebookKey = { _, file ->
                    when (file) {
                        orders -> ordersKey
                        report -> "notebooks/report.py"
                        else -> null
                    }
                },
            )

        val ordersEnv = requireNotNull(contributor.contribute(project, orders))

        assertEquals("127.0.0.1", ordersEnv.env["PGHOST"])
        assertNull(contributor.contribute(project, report))
    }
}
