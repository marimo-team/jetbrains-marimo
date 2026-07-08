/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoProcessServerTest {
    @Test fun detectsUnsupportedWatchOption() {
        assertTrue(indicatesUnsupportedWatch("Usage: marimo edit [OPTIONS]\nError: No such option: --watch"))
    }

    @Test fun ignoresUnrelatedFailures() {
        assertFalse(indicatesUnsupportedWatch("ModuleNotFoundError: No module named 'marimo'"))
        assertFalse(indicatesUnsupportedWatch("Address already in use"))
    }

    @Test fun ignoresWatchMentionThatIsNotAnOptionError() {
        assertFalse(indicatesUnsupportedWatch("watching /proj for changes"))
    }
}
