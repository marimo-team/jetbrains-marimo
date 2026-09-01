/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

/** Ambient variable names that marimo data source discovery reads. */
data class AmbientVariables(
    val host: String,
    val port: String?,
    val user: String,
    val database: String,
    val password: String?,
)

/** A database family that this plugin maps to vendor-standard environment variables. */
enum class DbFamily(
    val dialect: String,
    val jdbcSchemes: Set<String>,
    val ambient: AmbientVariables?,
) {
    POSTGRES(
        "postgresql",
        setOf("postgresql"),
        AmbientVariables("PGHOST", "PGPORT", "PGUSER", "PGDATABASE", "PGPASSWORD"),
    ),
    MYSQL(
        "mysql",
        setOf("mysql", "mariadb"),
        AmbientVariables(
            "MYSQL_HOST",
            "MYSQL_TCP_PORT",
            "MYSQL_USER",
            "MYSQL_DATABASE",
            "MYSQL_PWD",
        ),
    ),
    TRINO(
        "trino",
        setOf("trino"),
        AmbientVariables(
            "TRINO_HOST",
            "TRINO_PORT",
            "TRINO_USER",
            "TRINO_CATALOG",
            "TRINO_PASSWORD",
        ),
    );

    companion object {
        fun fromScheme(scheme: String): DbFamily? = entries.firstOrNull {
            scheme.lowercase() in it.jdbcSchemes
        }
    }
}
