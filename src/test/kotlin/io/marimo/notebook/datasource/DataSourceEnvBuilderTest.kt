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
            familyDefault = true,
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
                familyDefault = false,
            )
        val result = DataSourceEnvBuilder.build(listOf(ordersDb, second))
        val env = result.env
        assertEquals("only the default owns PGHOST", "db.internal", env["PGHOST"])
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
                familyDefault = false,
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
    fun trinoCatalogAndSchemaUseSeparateVendorVariables() {
        val trino =
            ordersDb.copy(
                displayName = "Warehouse",
                family = DbFamily.TRINO,
                dialect = "trino",
                username = "analyst",
                database = "hive",
                schema = "sales",
            )

        val env = DataSourceEnvBuilder.build(listOf(trino)).env

        assertEquals("hive", env["TRINO_CATALOG"])
        assertEquals("sales", env["TRINO_SCHEMA"])
    }

    @Test
    fun labelsNameTheSourceAndItsRole() {
        val labels = DataSourceEnvBuilder.build(listOf(ordersDb)).labels
        assertEquals(listOf("Orders DB (postgresql, default)"), labels)
    }

    @Test
    fun exposedDataSourceStringDoesNotRevealItsPassword() {
        val rendered = ordersDb.toString()

        assertFalse(rendered.contains("s3cret"))
        assertTrue(rendered.contains("password=<redacted>"))
    }
}
