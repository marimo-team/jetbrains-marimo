/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

/** Host, port, and database parsed from a network JDBC URL. */
data class JdbcEndpoint(
    val scheme: String,
    val host: String,
    val port: String?,
    val database: String?,
)

object JdbcUrl {
    // This feature maps only JDBC network URLs with host, optional port, and optional database.
    // Non-network forms and IPv6 literals return null and appear as unavailable in the dialog.
    private val NETWORK_URL =
        Regex("""^jdbc:([A-Za-z0-9]+)://([^/:?;,\[\]]+)(?::(\d+))?(?:/([^?;]*))?([?;].*)?$""")

    fun parse(url: String?): JdbcEndpoint? {
        val match = NETWORK_URL.find(url?.trim().orEmpty()) ?: return null
        val (scheme, host, port, database) = match.destructured
        return JdbcEndpoint(
            scheme = scheme.lowercase(),
            host = host,
            port = port.ifEmpty { null },
            database = database.ifEmpty { null },
        )
    }
}
