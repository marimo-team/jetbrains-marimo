/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import io.marimo.notebook.launch.redactAccessTokens
import io.marimo.notebook.telemetry.MarimoTelemetry
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Reads settings out of the page marimo serves.
 *
 * marimo resolves a notebook's effective config from several layers — the user's config file, a
 * project's `pyproject.toml`, the notebook's own PEP 723 metadata, environment overrides — and
 * serves the base config and notebook overrides in its mount JSON. Older versions use a
 * `<marimo-user-config>` tag. Read the server's config instead of duplicating its resolution rules.
 */
object PageConfigReader {

    /**
     * The effective `display.theme` ("light", "dark", "system", …) for the notebook served at
     * [url], or null when the page or the field can't be read. Blocking; call off the EDT.
     *
     * The two failure modes are not equally interesting. An unreachable or unreadable server is
     * environmental and expected (the process can die between readiness and this request), so it is
     * only logged. A page that *was* fetched but carries no theme means this code no longer
     * understands what marimo serves — the theme silently stops following the IDE — so that one is
     * reported.
     */
    fun fetchDisplayTheme(url: String): String? {
        val page =
            try {
                readPage(url)
            } catch (e: IOException) {
                thisLogger().warn(readFailureSummary(url, e))
                return null
            }
        return displayTheme(page)
            ?: run {
                thisLogger()
                    .warn("no display.theme in the page served at ${redactAccessTokens(url)}")
                MarimoTelemetry.getInstance().captureException(UnreadableMarimoConfigException())
                null
            }
    }

    private fun readPage(url: String): String {
        val query = URI(url).rawQuery?.split('&').orEmpty()
        val token =
            query
                .firstOrNull { it.substringBefore('=') == "access_token" }
                ?.substringAfter('=', "")
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
        val remaining =
            query.filterNot { it.substringBefore('=') == "access_token" }.joinToString("&")
        val cleanUrl =
            url.substringBefore('?').substringBefore('#') +
                if (remaining.isEmpty()) "" else "?$remaining"
        // Query-token authentication redirects through a session cookie. Bearer auth avoids that
        // browser-only exchange. Do not forward credentials to a redirect destination.
        return HttpRequests.request(cleanUrl)
            .followRedirects(false)
            .tuner { connection ->
                if (!token.isNullOrEmpty())
                    connection.setRequestProperty("Authorization", "Bearer $token")
            }
            .readString()
    }

    internal fun readFailureSummary(url: String, error: IOException): String {
        val exceptionMessage = redactAccessTokens(error.message.orEmpty())
        return "could not read marimo config from ${redactAccessTokens(url)}: " +
            "${error.javaClass.name}: $exceptionMessage"
    }

    /**
     * Carries no URL or page content: the failure is that marimo's served config could not be
     * located or parsed, and the stack trace is the actionable part.
     */
    private class UnreadableMarimoConfigException :
        RuntimeException("could not read display.theme from the marimo page")

    internal fun displayTheme(html: String): String? {
        mountTheme(html)?.let {
            return it
        }
        val escapedConfig = CONFIG_ATTRIBUTE.find(html)?.groupValues?.get(1) ?: return null
        return try {
            theme(JsonParser.parseString(unescapeHtml(escapedConfig)).asJsonObject)
        } catch (_: RuntimeException) {
            thisLogger().warn("could not parse marimo config")
            null
        }
    }

    private fun mountTheme(html: String): String? {
        val start = MOUNT_CONFIG.find(html)?.range?.last?.plus(1) ?: return null
        return try {
            // Parse one JSON value. Gson handles nested objects and escaped strings, and stops
            // before the JavaScript suffix. No page script is executed.
            val config =
                JsonParser.parseReader(JsonReader(StringReader(html.substring(start)))).asJsonObject
            theme(config.getAsJsonObject("configOverrides"))
                ?: theme(config.getAsJsonObject("config"))
        } catch (_: RuntimeException) {
            thisLogger().warn("could not parse marimo mount config")
            null
        }
    }

    private fun theme(config: JsonObject?): String? =
        config?.getAsJsonObject("display")?.get("theme")?.takeUnless { it.isJsonNull }?.asString

    private val MOUNT_CONFIG =
        Regex(
            """Object\.defineProperty\(window,\s*["']__MARIMO_MOUNT_CONFIG__["'],\s*\{\s*value:\s*Object\.freeze\("""
        )

    /** The attribute value is HTML-escaped, so a bare `"` can only be the closing quote. */
    private val CONFIG_ATTRIBUTE = Regex("""<marimo-user-config[^>]*\sdata-config="([^"]*)"""")

    private fun unescapeHtml(text: String): String =
        text
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
