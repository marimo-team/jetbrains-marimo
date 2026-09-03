/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import java.util.Locale

/** Host, port, database or catalog, and optional schema parsed from a network JDBC URL. */
data class JdbcEndpoint(
    val scheme: String,
    val host: String,
    val port: String?,
    val database: String?,
    val schema: String? = null,
)

object JdbcUrl {
    // This feature maps only JDBC network URLs with host, optional port, and optional database.
    // Non-network forms and IPv6 literals return null and appear as unavailable in the dialog.
    private val NETWORK_URL =
        Regex("""^jdbc:([A-Za-z0-9]+)://([^/:?;,\[\]]+)(?::(\d+))?(?:/([^?;]*))?([?;].*)?$""")

    fun parse(url: String?): JdbcEndpoint? {
        val match = NETWORK_URL.find(url?.trim().orEmpty()) ?: return null
        val (rawScheme, host, port, rawDatabase) = match.destructured
        val scheme = rawScheme.lowercase(Locale.ROOT)
        val pathParts = rawDatabase.takeIf { it.isNotEmpty() }?.split('/') ?: emptyList()
        if (scheme == "trino" && (pathParts.size > 2 || pathParts.any { it.isEmpty() })) {
            return null
        }
        return JdbcEndpoint(
            scheme = scheme,
            host = host,
            port = port.ifEmpty { null },
            database =
                if (scheme == "trino") pathParts.firstOrNull() else rawDatabase.ifEmpty { null },
            schema = if (scheme == "trino") pathParts.getOrNull(1) else null,
        )
    }
}
