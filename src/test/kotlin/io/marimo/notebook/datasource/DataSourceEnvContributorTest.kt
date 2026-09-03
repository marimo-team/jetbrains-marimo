/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CopyOnWriteArrayList

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
                familyDefault = true
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

        assertEquals(
            mapOf(
                "PGHOST" to "127.0.0.1",
                "PGPORT" to "5432",
                "PGUSER" to "orders_app",
                "PGDATABASE" to "orders",
                "PGPASSWORD" to "secret",
            ),
            ordersEnv.env,
        )
        assertEquals(listOf("Orders (postgresql, default)"), ordersEnv.labels)
        assertNull(contributor.contribute(project, report))
    }

    fun testLoadsOnlyTheEffectiveFamilyDefaultPassword() {
        val notebook =
            myFixture.addFileToProject("notebooks/orders.py", "print('orders')").virtualFile
        val default =
            Candidates.from(
                IdeDataSourceFacts(
                    id = "pg-default",
                    displayName = "Orders",
                    url = "jdbc:postgresql://default.internal:5432/orders",
                    username = "orders_app",
                    authProviderId = "user-pass",
                )
            )
        val replica =
            Candidates.from(
                IdeDataSourceFacts(
                    id = "pg-replica",
                    displayName = "Orders replica",
                    url = "jdbc:postgresql://replica.internal:5432/orders",
                    username = "orders_app",
                    authProviderId = "user-pass",
                )
            )
        val entries =
            listOf(default, replica).map { candidate ->
                DataSourceExposureStore.ExposureEntry().apply {
                    dataSourceId = candidate.id
                    exposed = true
                    familyDefault = candidate === default
                }
            }
        val notebookKey = "notebooks/orders.py"
        DataSourceExposureStore.getInstance(project).recordExposures(notebookKey, entries)
        val passwordReads = CopyOnWriteArrayList<String>()
        val contributor =
            DataSourceEnvContributor(
                candidates = { listOf(default, replica) },
                password = { _, id ->
                    passwordReads += id
                    "secret-$id"
                },
                notebookKey = { _, _ -> notebookKey },
            )

        val contribution = requireNotNull(contributor.contribute(project, notebook))

        assertEquals(listOf("pg-default"), passwordReads)
        assertEquals("secret-pg-default", contribution.env["PGPASSWORD"])
        assertFalse(contribution.env.values.any { it == "secret-pg-replica" })
    }
}
