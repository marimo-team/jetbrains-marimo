/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

/** One IDE data source resolved for exposure. The password stays in memory only. */
data class ExposedDataSource(
    val displayName: String,
    val family: DbFamily?,
    /** The family dialect, or the raw JDBC scheme when no family maps. */
    val dialect: String,
    val host: String,
    val port: String?,
    val username: String?,
    val database: String?,
    val schema: String? = null,
    val password: String?,
    val familyDefault: Boolean,
) {
    override fun toString(): String =
        "ExposedDataSource(" +
            "displayName=$displayName, family=$family, dialect=$dialect, host=$host, port=$port, " +
            "username=$username, database=$database, schema=$schema, " +
            "password=${if (password == null) "null" else "<redacted>"}, " +
            "familyDefault=$familyDefault)"
}

/** A computed launch environment. Never log its environment values. */
data class DataSourceEnv(val env: Map<String, String>, val labels: List<String>)

object DataSourceEnvBuilder {
    fun build(sources: List<ExposedDataSource>): DataSourceEnv {
        if (sources.isEmpty()) return DataSourceEnv(emptyMap(), emptyList())
        val env = LinkedHashMap<String, String>()
        val labels = mutableListOf<String>()

        sources
            .filter { it.familyDefault && it.family != null }
            .forEach { source ->
                vendorEntries(source).forEach { (name, value) -> env.putIfAbsent(name, value) }
                labels += label(source)
            }

        return DataSourceEnv(env, labels)
    }

    /** Returns the vendor variables for the default source in a family. */
    private fun vendorEntries(source: ExposedDataSource): List<Pair<String, String>> {
        val variables = source.family?.vendorVariables ?: return emptyList()
        val entries = mutableListOf(variables.host to source.host)
        if (variables.port != null && source.port != null) entries += variables.port to source.port
        source.username?.let { entries += variables.user to it }
        source.database?.let { entries += variables.database to it }
        if (variables.schema != null && source.schema != null) {
            entries += variables.schema to source.schema
        }
        if (variables.password != null && source.password != null) {
            entries += variables.password to source.password
        }
        return entries
    }

    private fun label(source: ExposedDataSource): String =
        "${source.displayName} (${source.dialect}, default)"
}
