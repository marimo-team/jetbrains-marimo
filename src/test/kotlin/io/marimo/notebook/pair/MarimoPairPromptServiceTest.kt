/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import io.marimo.notebook.pair.MarimoPairPromptService.PromptCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarimoPairPromptServiceTest {

    @Test fun trimsSuccessfulGenericPrompt() {
        assertEquals(
            "pairing prompt",
            MarimoPairPromptService.promptText(PromptCommandResult(exitCode = 0, stdout = "  pairing prompt\n")),
        )
    }

    @Test fun rejectsNonzeroPromptCommand() {
        assertNull(MarimoPairPromptService.promptText(PromptCommandResult(exitCode = 1, stdout = "error")))
    }

    @Test fun rejectsAnExecutionFailure() {
        assertNull(MarimoPairPromptService.promptText(null))
    }
}
