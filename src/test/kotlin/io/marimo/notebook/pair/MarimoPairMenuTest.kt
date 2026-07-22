/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoPairMenuTest {

    @Test fun terminalActionsPrecedeCopyWithSeparator() {
        val actions = MarimoPairMenu.actions(
            terminalActions = listOf(
                TestAction("Claude Code in Terminal"),
                TestAction("Codex CLI in Terminal"),
                TestAction("opencode in Terminal"),
            ),
            copyAction = TestAction("Copy pairing prompt"),
        )

        assertEquals(
            listOf(
                "Claude Code in Terminal",
                "Codex CLI in Terminal",
                "opencode in Terminal",
                null,
                "Copy pairing prompt",
            ),
            actions.map { it.templatePresentation.text },
        )
        assertTrue(actions[3] is Separator)
    }

    @Test fun copyOnlyProducesNoSeparator() {
        val actions = MarimoPairMenu.actions(
            terminalActions = emptyList(),
            copyAction = TestAction("Copy pairing prompt"),
        )

        assertEquals(
            listOf("Copy pairing prompt"),
            actions.map { it.templatePresentation.text },
        )
    }

    private class TestAction(text: String) : AnAction(text) {
        override fun actionPerformed(e: AnActionEvent) = Unit
    }
}
