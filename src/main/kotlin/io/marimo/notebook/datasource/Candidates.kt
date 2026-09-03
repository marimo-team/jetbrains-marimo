/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import java.util.Locale

/** Connection facts copied from one Database Tools data source. Never holds a credential. */
data class IdeDataSourceFacts(
    val id: String,
    val displayName: String,
    val url: String?,
    val username: String?,
    val authProviderId: String?,
)

/** One IDE data source for the consent dialog and environment contributor. */
data class CandidateDataSource(
    val id: String,
    val displayName: String,
    val endpoint: JdbcEndpoint?,
    val family: DbFamily?,
    val username: String?,
    /** Explains why the plugin cannot expose this source. */
    val unsupportedReason: String?,
) {
    val supported: Boolean
        get() = unsupportedReason == null

    val dialect: String
        get() = family?.dialect ?: endpoint?.scheme ?: "unknown"
}

object Candidates {
    private val SUPPORTED_AUTH = setOf(null, "user-pass", "no-auth")
    private val FALLBACK_ORDER =
        compareBy<CandidateDataSource>({ it.displayName.lowercase(Locale.ROOT) }, { it.id })

    fun from(facts: IdeDataSourceFacts): CandidateDataSource {
        val endpoint = JdbcUrl.parse(facts.url)
        val family = endpoint?.let { DbFamily.fromScheme(it.scheme) }
        val username = facts.username?.trim()?.takeIf { it.isNotEmpty() }
        val reason =
            when {
                facts.authProviderId !in SUPPORTED_AUTH ->
                    "uses IDE auth '${facts.authProviderId}'. " +
                        "Choose user/password or no authentication to share this source"
                endpoint == null -> "the JDBC URL has no host/port form that the plugin can map"
                family == null -> "the database family has no vendor variables for Quick Add"
                username == null -> "${family.displayName} Quick Add requires a username"
                endpoint.database == null ->
                    "${family.displayName} Quick Add requires ${family.databaseName}"
                else -> null
            }
        return CandidateDataSource(
            id = facts.id,
            displayName = facts.displayName,
            endpoint = endpoint,
            family = family,
            username = username,
            unsupportedReason = reason,
        )
    }

    /** Selects one exposed source to own each family's vendor variables. */
    fun effectiveDefaults(
        exposed: List<CandidateDataSource>,
        storedDefaults: Set<String>,
    ): Set<String> =
        exposed
            .filter { it.family != null }
            .groupBy { it.family }
            .values
            .mapNotNull { members ->
                members.filter { it.id in storedDefaults }.minWithOrNull(FALLBACK_ORDER)?.id
                    ?: members.minWithOrNull(FALLBACK_ORDER)?.id
            }
            .toSet()
}
