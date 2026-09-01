/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JdbcUrlTest {
    @Test
    fun parsesAFullPostgresUrl() {
        assertEquals(
            JdbcEndpoint("postgresql", "db.internal", "5432", "orders"),
            JdbcUrl.parse("jdbc:postgresql://db.internal:5432/orders"),
        )
    }

    @Test
    fun portAndDatabaseAreOptional() {
        assertEquals(
            JdbcEndpoint("postgresql", "localhost", null, null),
            JdbcUrl.parse("jdbc:postgresql://localhost"),
        )
        assertEquals(
            JdbcEndpoint("postgresql", "localhost", null, null),
            JdbcUrl.parse("jdbc:postgresql://localhost/"),
        )
    }

    @Test
    fun queryParametersAreDropped() {
        assertEquals(
            JdbcEndpoint("mysql", "db", "3306", "shop"),
            JdbcUrl.parse("jdbc:mysql://db:3306/shop?useSSL=true"),
        )
    }

    @Test
    fun trinoCatalogRidesTheDatabaseSlot() {
        assertEquals(
            JdbcEndpoint("trino", "trino.internal", "8080", "hive"),
            JdbcUrl.parse("jdbc:trino://trino.internal:8080/hive"),
        )
    }

    @Test
    fun unknownSchemesStillParse() {
        assertEquals(
            JdbcEndpoint("snowflake", "acme.snowflakecomputing.com", null, null),
            JdbcUrl.parse("jdbc:snowflake://acme.snowflakecomputing.com"),
        )
    }

    @Test
    fun nonNetworkFormsReturnNull() {
        assertNull(JdbcUrl.parse("jdbc:oracle:thin:@//db:1521/svc"))
        assertNull(JdbcUrl.parse("jdbc:sqlite:/tmp/x.db"))
        assertNull(JdbcUrl.parse(""))
        assertNull(JdbcUrl.parse(null))
    }

    @Test
    fun ipv6HostsAreNotMappedYet() {
        assertNull(JdbcUrl.parse("jdbc:postgresql://[::1]:5432/db"))
    }
}
