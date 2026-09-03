/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

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

    fun from(facts: IdeDataSourceFacts): CandidateDataSource {
        val endpoint = JdbcUrl.parse(facts.url)
        val family = endpoint?.let { DbFamily.fromScheme(it.scheme) }
        val username = facts.username?.trim()?.takeIf { it.isNotEmpty() }
        val reason =
            when {
                facts.authProviderId !in SUPPORTED_AUTH ->
                    "uses IDE auth '${facts.authProviderId}'. " +
                        "Only user/password sources map to environment variables"
                endpoint == null -> "the JDBC URL has no host/port form the plugin can map"
                family == null -> "the database family has no vendor variables for Quick add"
                family == DbFamily.POSTGRES && username == null ->
                    "PostgreSQL Quick add requires a username"
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

    /** Selects one exposed source to own each family's ambient variables. */
    fun effectivePrimaries(
        exposed: List<CandidateDataSource>,
        storedPrimaries: Set<String>,
    ): Set<String> =
        exposed
            .filter { it.family != null }
            .groupBy { it.family }
            .values
            .mapNotNull { members ->
                members.firstOrNull { it.id in storedPrimaries }?.id
                    ?: members.minByOrNull { it.displayName.lowercase() }?.id
            }
            .toSet()
}
