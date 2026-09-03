/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceEnvBuilderTest {
    private val ordersDb =
        ExposedDataSource(
            displayName = "Orders DB",
            family = DbFamily.POSTGRES,
            dialect = "postgresql",
            host = "db.internal",
            port = "5432",
            username = "app",
            database = "orders",
            password = "s3cret",
            familyPrimary = true,
        )

    @Test
    fun familyDefaultGetsOnlyVendorVariables() {
        val env = DataSourceEnvBuilder.build(listOf(ordersDb)).env
        assertEquals(
            mapOf(
                "PGHOST" to "db.internal",
                "PGPORT" to "5432",
                "PGUSER" to "app",
                "PGDATABASE" to "orders",
                "PGPASSWORD" to "s3cret",
            ),
            env,
        )
        assertFalse(env.keys.any { it.startsWith("JB_") })
    }

    @Test
    fun nonDefaultSourceDoesNotEnterTheLaunchEnvironment() {
        val second =
            ordersDb.copy(
                displayName = "Orders Replica",
                host = "replica.internal",
                familyPrimary = false,
            )
        val result = DataSourceEnvBuilder.build(listOf(ordersDb, second))
        val env = result.env
        assertEquals("only the primary owns PGHOST", "db.internal", env["PGHOST"])
        assertFalse(env.values.contains("replica.internal"))
        assertEquals(listOf("Orders DB (postgresql, default)"), result.labels)
    }

    @Test
    fun sourcesWithoutVendorVariablesDoNotEnterTheLaunchEnvironment() {
        val snowflake =
            ordersDb.copy(
                displayName = "Warehouse",
                family = null,
                dialect = "snowflake",
                familyPrimary = false,
            )
        val result = DataSourceEnvBuilder.build(listOf(snowflake))
        assertTrue(result.env.isEmpty())
        assertTrue(result.labels.isEmpty())
    }

    @Test
    fun missingPasswordEmitsNoVendorPasswordVariable() {
        val env = DataSourceEnvBuilder.build(listOf(ordersDb.copy(password = null))).env
        assertNull(env["PGPASSWORD"])
    }

    @Test
    fun labelsNameTheSourceAndItsRole() {
        val labels = DataSourceEnvBuilder.build(listOf(ordersDb)).labels
        assertEquals(listOf("Orders DB (postgresql, default)"), labels)
    }
}
