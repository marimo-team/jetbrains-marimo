/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import java.util.Locale

/** Vendor variable names that marimo data source discovery reads. */
data class VendorVariables(
    val host: String,
    val port: String?,
    val user: String,
    val database: String,
    val schema: String?,
    val password: String?,
)

/** A database family that this plugin maps to vendor-standard environment variables. */
enum class DbFamily(
    val displayName: String,
    val dialect: String,
    val databaseName: String,
    val jdbcSchemes: Set<String>,
    val vendorVariables: VendorVariables?,
) {
    POSTGRES(
        "PostgreSQL",
        "postgresql",
        "a database",
        setOf("postgresql"),
        VendorVariables("PGHOST", "PGPORT", "PGUSER", "PGDATABASE", null, "PGPASSWORD"),
    ),
    MYSQL(
        "MySQL",
        "mysql",
        "a database",
        setOf("mysql", "mariadb"),
        VendorVariables(
            "MYSQL_HOST",
            "MYSQL_TCP_PORT",
            "MYSQL_USER",
            "MYSQL_DATABASE",
            null,
            "MYSQL_PWD",
        ),
    ),
    TRINO(
        "Trino",
        "trino",
        "a catalog",
        setOf("trino"),
        VendorVariables(
            "TRINO_HOST",
            "TRINO_PORT",
            "TRINO_USER",
            "TRINO_CATALOG",
            "TRINO_SCHEMA",
            "TRINO_PASSWORD",
        ),
    );

    companion object {
        fun fromScheme(scheme: String): DbFamily? = entries.firstOrNull {
            scheme.lowercase(Locale.ROOT) in it.jdbcSchemes
        }
    }
}
