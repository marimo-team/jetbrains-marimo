/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import io.marimo.notebook.launch.redactAccessTokens
import io.marimo.notebook.telemetry.MarimoTelemetry
import java.io.IOException

/**
 * Reads settings out of the page marimo serves.
 *
 * marimo resolves a notebook's effective config from several layers — the user's config file, a
 * project's `pyproject.toml`, the notebook's own PEP 723 metadata, environment overrides — and
 * inlines the result into the page as `<marimo-user-config data-config="…">`. Asking the server for
 * that answer keeps the plugin from reimplementing (and drifting from) the resolution rules.
 */
object MarimoPageConfig {

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
                HttpRequests.request(url).readString()
            } catch (e: IOException) {
                thisLogger().warn("could not read marimo config from ${redactAccessTokens(url)}", e)
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

    /**
     * Carries no URL or page content: the failure is that marimo's served config could not be
     * located or parsed, and the stack trace is the actionable part.
     */
    private class UnreadableMarimoConfigException :
        RuntimeException("could not read display.theme from the marimo page")

    internal fun displayTheme(html: String): String? {
        val escapedConfig = CONFIG_ATTRIBUTE.find(html)?.groupValues?.get(1) ?: return null
        return try {
            JsonParser.parseString(unescapeHtml(escapedConfig))
                .asJsonObject
                .getAsJsonObject("display")
                ?.get("theme")
                ?.asString
        } catch (e: RuntimeException) {
            thisLogger().warn("could not parse marimo config", e)
            null
        }
    }

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
