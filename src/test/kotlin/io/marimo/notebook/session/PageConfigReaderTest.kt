/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import io.marimo.notebook.editor.MarimoThemedUrl
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageConfigReaderTest {

    private fun mounted(config: String, overrides: String = "{}") =
        """<script data-marimo="true">
        Object.defineProperty(window, "__MARIMO_MOUNT_CONFIG__", {
          value: Object.freeze({"config":$config,"configOverrides":$overrides}),
          writable: false, configurable: false
        });
        </script>"""

    @Test
    fun projectThemeOverridesUserSystemWithoutAnIdeQuery() {
        val html =
            page(escapedConfig("system")) +
                mounted("""{"display":{"theme":"system"}}""", """{"display":{"theme":"dark"}}""")
        val theme = PageConfigReader.displayTheme(html)
        assertEquals("dark", theme)
        assertEquals(
            "http://localhost:2718/",
            MarimoThemedUrl.of("http://localhost:2718/", theme, "light"),
        )
    }

    @Test
    fun systemOverrideFollowsTheIdeDespitePinnedUserTheme() {
        val theme =
            PageConfigReader.displayTheme(
                mounted("""{"display":{"theme":"dark"}}""", """{"display":{"theme":"system"}}""")
            )
        assertEquals("system", theme)
        assertTrue(MarimoThemedUrl.followsIdeTheme(theme))
    }

    @Test
    fun mountConfigWorksWithoutLegacyTag() {
        assertEquals(
            "light",
            PageConfigReader.displayTheme(mounted("""{"display":{"theme":"light"}}""")),
        )
    }

    @Test
    fun unrelatedOverridesKeepBaseTheme() {
        assertEquals(
            "dark",
            PageConfigReader.displayTheme(
                mounted(
                    """{"display":{"theme":"dark"}}""",
                    """{"display":{"cell_output":"above"}}""",
                )
            ),
        )
    }

    @Test
    fun parsesNestedJsonAndEscapedStringsWithoutExecutingScript() {
        val config =
            """{"custom_css":["brace } and quote \" and slash \\"],"display":{"theme":"system"}}"""
        assertEquals(
            "dark",
            PageConfigReader.displayTheme(mounted(config, """{"display":{"theme":"dark"}}""")),
        )
    }

    @Test
    fun malformedMountFallsBackToLegacyTheme() {
        assertEquals(
            "dark",
            PageConfigReader.displayTheme(page(escapedConfig("dark")) + mounted("not JSON")),
        )
    }

    private fun page(escapedConfig: String) =
        """
        <!DOCTYPE html>
        <html><head>
        <marimo-server-token data-token="&quot;abc&quot;" hidden></marimo-server-token>
        <marimo-user-config data-config="$escapedConfig" hidden></marimo-user-config>
        <title>notebook.py</title>
        </head><body></body></html>
    """
            .trimIndent()

    /** As marimo serves it: the config JSON escaped with Python's `html.escape(quote=True)`. */
    private fun escapedConfig(theme: String) =
        "{&quot;display&quot;: {&quot;theme&quot;: &quot;$theme&quot;, &quot;cell_output&quot;: &quot;above&quot;}}"

    @Test
    fun readsTheme() {
        assertEquals("light", PageConfigReader.displayTheme(page(escapedConfig("light"))))
        assertEquals("system", PageConfigReader.displayTheme(page(escapedConfig("system"))))
    }

    @Test
    fun ignoresThemeKeysOutsideDisplay() {
        val config =
            "{&quot;theme&quot;: &quot;dark&quot;, " +
                "&quot;display&quot;: {&quot;theme&quot;: &quot;light&quot;}}"
        assertEquals("light", PageConfigReader.displayTheme(page(config)))
    }

    @Test
    fun nullWhenTagMissing() {
        assertNull(PageConfigReader.displayTheme("<html><body>no config here</body></html>"))
    }

    @Test
    fun nullWhenDisplayTableMissing() {
        assertNull(PageConfigReader.displayTheme(page("{&quot;runtime&quot;: {}}")))
    }

    @Test
    fun nullWhenConfigIsNotJson() {
        assertNull(PageConfigReader.displayTheme(page("not json")))
    }

    @Test
    fun unescapesEntitiesInsideTheConfig() {
        val config =
            "{&quot;display&quot;: {&quot;theme&quot;: &quot;dark&quot;}, " +
                "&quot;custom_css&quot;: [&quot;a &amp; b &lt;c&gt; &#x27;d&#x27;&quot;]}"
        assertEquals("dark", PageConfigReader.displayTheme(page(config)))
    }

    @Test
    fun readFailureSummaryContainsNoAccessToken() {
        val url = "http://127.0.0.1:2718?access_token=URL_SECRET"
        val error = IOException("request failed for $url and access_token=MESSAGE_SECRET")

        val warning = PageConfigReader.readFailureSummary(url, error)

        assertFalse(warning.contains("URL_SECRET"))
        assertFalse(warning.contains("MESSAGE_SECRET"))
        assertFalse(warning.contains("access_token"))
        assertTrue(warning.contains("<redacted-token>"))
        assertTrue(warning.contains(IOException::class.java.name))
    }
}
