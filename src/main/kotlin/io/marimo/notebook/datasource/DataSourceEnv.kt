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
    val password: String?,
    val familyPrimary: Boolean,
)

/** A computed launch environment. Never log its environment values. */
data class DataSourceEnv(val env: Map<String, String>, val labels: List<String>)

/** Creates unique environment slugs from IntelliJ display names. */
internal fun assignSlugs(displayNames: List<String>): List<String> {
    val taken = mutableSetOf<String>()
    return displayNames.map { name ->
        val base =
            name.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifEmpty { "DATASOURCE" }
        var candidate = base
        var suffix = 2
        while (!taken.add(candidate)) {
            candidate = "${base}_$suffix"
            suffix++
        }
        candidate
    }
}

object DataSourceEnvBuilder {
    const val MANIFEST_VAR = "JB_DATASOURCES"

    fun build(sources: List<ExposedDataSource>): DataSourceEnv {
        if (sources.isEmpty()) return DataSourceEnv(emptyMap(), emptyList())
        val slugs = assignSlugs(sources.map { it.displayName })
        val env = LinkedHashMap<String, String>()
        val manifestEntries = mutableListOf<String>()
        val labels = mutableListOf<String>()

        sources.forEachIndexed { index, source ->
            val slug = slugs[index]
            ambientEntries(source).forEach { (name, value) -> env.putIfAbsent(name, value) }
            val jbVariables = jbVariables(slug, source)
            jbVariables.forEach { env[it.name] = it.value }
            var passwordVariable: String? = null
            if (source.password != null) {
                passwordVariable = "JB_${slug}_PASSWORD"
                env[passwordVariable] = source.password
            }
            manifestEntries += manifestEntry(slug, source, jbVariables, passwordVariable)
            labels += label(source)
        }

        env[MANIFEST_VAR] = """{"version":1,"sources":[${manifestEntries.joinToString(",")}]}"""
        return DataSourceEnv(env, labels)
    }

    private data class JbVariable(val field: String, val name: String, val value: String)

    private fun jbVariables(slug: String, source: ExposedDataSource): List<JbVariable> {
        val variables = mutableListOf(JbVariable("host", "JB_${slug}_HOST", source.host))
        source.port?.let { variables += JbVariable("port", "JB_${slug}_PORT", it) }
        source.username?.let { variables += JbVariable("username", "JB_${slug}_USER", it) }
        source.database?.let { variables += JbVariable("database", "JB_${slug}_DATABASE", it) }
        return variables
    }

    /** Returns the vendor variables for the primary source in a family. */
    private fun ambientEntries(source: ExposedDataSource): List<Pair<String, String>> {
        val ambient = source.family?.ambient ?: return emptyList()
        if (!source.familyPrimary) return emptyList()
        val entries = mutableListOf(ambient.host to source.host)
        if (ambient.port != null && source.port != null) entries += ambient.port to source.port
        source.username?.let { entries += ambient.user to it }
        source.database?.let { entries += ambient.database to it }
        if (ambient.password != null && source.password != null) {
            entries += ambient.password to source.password
        }
        return entries
    }

    private fun manifestEntry(
        slug: String,
        source: ExposedDataSource,
        jbVariables: List<JbVariable>,
        passwordVariable: String?,
    ): String {
        val builder = StringBuilder("{\"id\":")
        appendJsonString(builder, slug.lowercase())
        builder.append(",\"displayName\":")
        appendJsonString(builder, source.displayName)
        builder.append(",\"dialect\":")
        appendJsonString(builder, source.dialect)
        builder.append(",\"fields\":{")
        jbVariables.forEachIndexed { index, variable ->
            if (index > 0) builder.append(',')
            appendJsonString(builder, variable.field)
            builder.append(':')
            appendJsonString(builder, variable.name)
        }
        builder.append('}')
        if (passwordVariable != null) {
            builder.append(",\"passwordEnv\":")
            appendJsonString(builder, passwordVariable)
        }
        builder.append('}')
        return builder.toString()
    }

    private fun label(source: ExposedDataSource): String = buildString {
        append(source.displayName)
        append(" (")
        append(source.dialect)
        if (source.familyPrimary && source.family != null) append(", primary")
        append(")")
    }

    private fun appendJsonString(builder: StringBuilder, value: String) {
        builder.append('"')
        for (character in value) {
            when {
                character == '"' -> builder.append("\\\"")
                character == '\\' -> builder.append("\\\\")
                character == '\n' -> builder.append("\\n")
                character == '\r' -> builder.append("\\r")
                character == '\t' -> builder.append("\\t")
                character < ' ' -> builder.append("\\u%04x".format(character.code))
                else -> builder.append(character)
            }
        }
        builder.append('"')
    }
}
