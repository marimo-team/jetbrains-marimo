/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarimoPageConfigTest {

    private fun page(escapedConfig: String) = """
        <!DOCTYPE html>
        <html><head>
        <marimo-server-token data-token="&quot;abc&quot;" hidden></marimo-server-token>
        <marimo-user-config data-config="$escapedConfig" hidden></marimo-user-config>
        <title>notebook.py</title>
        </head><body></body></html>
    """.trimIndent()

    /** As marimo serves it: the config JSON escaped with Python's `html.escape(quote=True)`. */
    private fun escapedConfig(theme: String) =
        "{&quot;display&quot;: {&quot;theme&quot;: &quot;$theme&quot;, &quot;cell_output&quot;: &quot;above&quot;}}"

    @Test fun readsTheme() {
        assertEquals("light", MarimoPageConfig.displayTheme(page(escapedConfig("light"))))
        assertEquals("system", MarimoPageConfig.displayTheme(page(escapedConfig("system"))))
    }

    @Test fun ignoresThemeKeysOutsideDisplay() {
        val config = "{&quot;theme&quot;: &quot;dark&quot;, " +
            "&quot;display&quot;: {&quot;theme&quot;: &quot;light&quot;}}"
        assertEquals("light", MarimoPageConfig.displayTheme(page(config)))
    }

    @Test fun nullWhenTagMissing() {
        assertNull(MarimoPageConfig.displayTheme("<html><body>no config here</body></html>"))
    }

    @Test fun nullWhenDisplayTableMissing() {
        assertNull(MarimoPageConfig.displayTheme(page("{&quot;runtime&quot;: {}}")))
    }

    @Test fun nullWhenConfigIsNotJson() {
        assertNull(MarimoPageConfig.displayTheme(page("not json")))
    }

    @Test fun unescapesEntitiesInsideTheConfig() {
        val config = "{&quot;display&quot;: {&quot;theme&quot;: &quot;dark&quot;}, " +
            "&quot;custom_css&quot;: [&quot;a &amp; b &lt;c&gt; &#x27;d&#x27;&quot;]}"
        assertEquals("dark", MarimoPageConfig.displayTheme(page(config)))
    }
}
