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
    fun slugsUppercaseAndCollapseNonAlphanumericRuns() {
        assertEquals(listOf("ORDERS_DB"), assignSlugs(listOf("Orders DB")))
        assertEquals(listOf("A_B_C"), assignSlugs(listOf("a - b/(c)")))
        assertEquals(listOf("DATASOURCE"), assignSlugs(listOf("!!!")))
    }

    @Test
    fun slugCollisionsGetDeterministicSuffixes() {
        assertEquals(
            listOf("ORDERS_DB", "ORDERS_DB_2", "ORDERS_DB_3"),
            assignSlugs(listOf("Orders DB", "orders db", "Orders-DB")),
        )
    }

    @Test
    fun primaryGetsAmbientAndJbVariables() {
        val env = DataSourceEnvBuilder.build(listOf(ordersDb)).env
        assertEquals("db.internal", env["PGHOST"])
        assertEquals("5432", env["PGPORT"])
        assertEquals("app", env["PGUSER"])
        assertEquals("orders", env["PGDATABASE"])
        assertEquals("s3cret", env["PGPASSWORD"])
        assertEquals("db.internal", env["JB_ORDERS_DB_HOST"])
        assertEquals("s3cret", env["JB_ORDERS_DB_PASSWORD"])
    }

    @Test
    fun nonPrimaryGetsOnlyJbVariables() {
        val second =
            ordersDb.copy(
                displayName = "Orders Replica",
                host = "replica.internal",
                familyPrimary = false,
            )
        val env = DataSourceEnvBuilder.build(listOf(ordersDb, second)).env
        assertEquals("only the primary owns PGHOST", "db.internal", env["PGHOST"])
        assertEquals("replica.internal", env["JB_ORDERS_REPLICA_HOST"])
    }

    @Test
    fun familiesWithoutAmbientVariablesGetOnlyJbVariables() {
        val snowflake =
            ordersDb.copy(
                displayName = "Warehouse",
                family = null,
                dialect = "snowflake",
                familyPrimary = false,
            )
        val env = DataSourceEnvBuilder.build(listOf(snowflake)).env
        assertNull(env["PGHOST"])
        assertEquals("db.internal", env["JB_WAREHOUSE_HOST"])
    }

    @Test
    fun missingPasswordEmitsNoPasswordVariableAndNoPasswordEnv() {
        val env = DataSourceEnvBuilder.build(listOf(ordersDb.copy(password = null))).env
        assertNull(env["PGPASSWORD"])
        assertNull(env["JB_ORDERS_DB_PASSWORD"])
        assertFalse(env.getValue("JB_DATASOURCES").contains("passwordEnv"))
    }

    @Test
    fun manifestMatchesTheDesignShape() {
        val env = DataSourceEnvBuilder.build(listOf(ordersDb)).env
        assertEquals(
            "{\"version\":1,\"sources\":[{\"id\":\"orders_db\",\"displayName\":\"Orders DB\"," +
                "\"dialect\":\"postgresql\",\"fields\":{\"host\":\"JB_ORDERS_DB_HOST\"," +
                "\"port\":\"JB_ORDERS_DB_PORT\",\"username\":\"JB_ORDERS_DB_USER\"," +
                "\"database\":\"JB_ORDERS_DB_DATABASE\"},\"passwordEnv\":\"JB_ORDERS_DB_PASSWORD\"}]}",
            env["JB_DATASOURCES"],
        )
    }

    @Test
    fun manifestNeverContainsValues() {
        val manifest = DataSourceEnvBuilder.build(listOf(ordersDb)).env.getValue("JB_DATASOURCES")
        assertFalse(manifest.contains("db.internal"))
        assertFalse(manifest.contains("s3cret"))
        assertFalse(manifest.contains("app"))
    }

    @Test
    fun displayNamesAreJsonEscaped() {
        val quoted = ordersDb.copy(displayName = "Orders \"prod\" \\ DB")
        val manifest = DataSourceEnvBuilder.build(listOf(quoted)).env.getValue("JB_DATASOURCES")
        assertTrue(manifest.contains("\"displayName\":\"Orders \\\"prod\\\" \\\\ DB\""))
    }

    @Test
    fun labelsNameTheSourceAndItsRole() {
        val labels = DataSourceEnvBuilder.build(listOf(ordersDb)).labels
        assertEquals(listOf("Orders DB (postgresql, primary)"), labels)
    }
}
