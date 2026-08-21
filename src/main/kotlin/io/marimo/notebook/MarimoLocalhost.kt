/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook

/** Loopback addresses and URL builders used by the local marimo server. */
object MarimoLocalhost {
    const val HOST = "127.0.0.1"

    private const val SCHEME = "http"

    fun origin(port: Int): String = origin(HOST, port)

    fun origin(host: String, port: Int): String = "$SCHEME://${urlHost(host)}:$port"

    fun rootUrl(port: Int): String = "${origin(port)}/"

    fun authenticatedUrl(port: Int, token: String): String = authenticatedUrl(HOST, port, token)

    fun authenticatedUrl(host: String, port: Int, token: String): String =
        "${origin(host, port)}?access_token=$token"

    fun isLoopbackHost(host: String): Boolean {
        val address = bareHost(host)
        return address.equals("localhost", ignoreCase = true) ||
            address == HOST ||
            address == IPV6_LOOPBACK
    }

    private const val IPV6_LOOPBACK = "::1"

    /**
     * An IPv6 literal must carry square brackets inside a URL. Without them the colons of the
     * address read as the port separator and the URL is invalid.
     */
    private fun urlHost(host: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]" else host

    /**
     * A host parsed out of a URL keeps the brackets of an IPv6 literal; a comparison needs the bare
     * address.
     */
    private fun bareHost(host: String): String = host.removeSurrounding("[", "]")
}
