/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoThemedUrlTest {

    @Test fun systemFollowsTheIde() {
        assertTrue(MarimoThemedUrl.followsIdeTheme("system"))
        assertEquals(
            "http://127.0.0.1:2718?theme=dark",
            MarimoThemedUrl.of("http://127.0.0.1:2718", "system", "dark"),
        )
    }

    @Test fun unreadableThemeFollowsTheIde() {
        assertTrue(MarimoThemedUrl.followsIdeTheme(null))
        assertEquals(
            "http://127.0.0.1:2718?theme=light",
            MarimoThemedUrl.of("http://127.0.0.1:2718", null, "light"),
        )
    }

    @Test fun pinnedThemeIsLeftAlone() {
        assertFalse(MarimoThemedUrl.followsIdeTheme("light"))
        assertFalse(MarimoThemedUrl.followsIdeTheme("dark"))
        val url = "http://127.0.0.1:2718"
        assertEquals(url, MarimoThemedUrl.of(url, "light", "dark"))
        assertEquals(url, MarimoThemedUrl.of(url, "dark", "light"))
    }

    @Test fun keepsExistingQuery() {
        assertEquals(
            "http://127.0.0.1:2718/?file=%2Ftmp%2Fnb.py&theme=dark",
            MarimoThemedUrl.of("http://127.0.0.1:2718/?file=%2Ftmp%2Fnb.py", "system", "dark"),
        )
    }
}
