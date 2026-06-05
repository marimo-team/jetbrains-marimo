/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.pycharm.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoThemeConfigTest {
    @Test fun blankConfigGetsDisplayTheme() {
        val out = MarimoThemeConfig.withTheme(null, "dark")
        assertEquals("[display]\ntheme = \"dark\"\n", out)
    }

    @Test fun replacesExistingThemeAndKeepsOtherSettings() {
        val existing = """
            [display]
            theme = "system"
            cell_output = "below"

            [runtime]
            auto_instantiate = true
        """.trimIndent()
        val out = MarimoThemeConfig.withTheme(existing, "light")
        assertTrue(out.contains("theme = \"light\""))
        assertTrue("other display key preserved", out.contains("cell_output = \"below\""))
        assertTrue("other table preserved", out.contains("auto_instantiate = true"))
        assertTrue("no stale theme", !out.contains("\"system\""))
    }

    @Test fun insertsThemeWhenDisplayTableHasNoTheme() {
        val existing = "[display]\ncell_output = \"below\""
        val out = MarimoThemeConfig.withTheme(existing, "dark")
        assertTrue(out.contains("theme = \"dark\""))
        assertTrue(out.contains("cell_output = \"below\""))
    }

    @Test fun appendsDisplayTableWhenAbsent() {
        val existing = "[runtime]\nauto_instantiate = true\n"
        val out = MarimoThemeConfig.withTheme(existing, "dark")
        assertTrue(out.contains("auto_instantiate = true"))
        assertTrue(out.contains("[display]"))
        assertTrue(out.contains("theme = \"dark\""))
    }

    @Test fun doesNotTouchThemeOutsideDisplayTable() {
        val existing = "[runtime]\ntheme = \"keepme\"\n"
        val out = MarimoThemeConfig.withTheme(existing, "dark")
        assertTrue("unrelated theme key untouched", out.contains("theme = \"keepme\""))
        assertTrue(out.contains("[display]\ntheme = \"dark\""))
    }
}
