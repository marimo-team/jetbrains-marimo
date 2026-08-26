/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.pair

import com.intellij.openapi.util.Disposer
import io.marimo.notebook.pair.MarimoPairPromptService.PromptCommandResult
import io.marimo.notebook.pair.MarimoPairPromptService.PromptDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoPairPromptServiceTest {

    @Test
    fun trimsSuccessfulGenericPrompt() {
        assertEquals(
            "pairing prompt",
            MarimoPairPromptService.promptText(
                PromptCommandResult(exitCode = 0, stdout = "  pairing prompt\n")
            ),
        )
    }

    @Test
    fun rejectsNonzeroPromptCommand() {
        assertNull(
            MarimoPairPromptService.promptText(PromptCommandResult(exitCode = 1, stdout = "error"))
        )
    }

    @Test
    fun rejectsAnExecutionFailure() {
        assertNull(MarimoPairPromptService.promptText(null))
    }

    @Test
    fun rejectsBlankSuccessfulStdout() {
        assertNull(
            MarimoPairPromptService.promptText(PromptCommandResult(exitCode = 0, stdout = "  \n"))
        )
    }

    @Test
    fun deliversANonBlankPrompt() {
        val delivered = mutableListOf<String>()
        val result =
            MarimoPairPromptService.completePrompt(
                PromptCommandResult(exitCode = 0, stdout = " pairing prompt \n"),
                disposed = false,
            ) {
                delivered += it
            }
        assertEquals(PromptDelivery.DELIVERED, result)
        assertEquals(listOf("pairing prompt"), delivered)
    }

    @Test
    fun treatsBlankStdoutAsFailureWithoutCallingBack() {
        val delivered = mutableListOf<String>()
        val result =
            MarimoPairPromptService.completePrompt(
                PromptCommandResult(exitCode = 0, stdout = "\n"),
                disposed = false,
            ) {
                delivered += it
            }
        assertEquals(PromptDelivery.FAILED, result)
        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun disposingTheParentExpiresDeliveryAndClosesTheLeaseOnce() {
        val parent = Disposer.newDisposable("pair prompt test parent")
        var leaseCloseCount = 0
        val lifetime = PromptLifetime(parent) { leaseCloseCount++ }
        val delivered = mutableListOf<String>()
        var parentDisposed = false
        try {
            Disposer.dispose(parent)
            parentDisposed = true

            val result =
                MarimoPairPromptService.completePrompt(
                    PromptCommandResult(exitCode = 0, stdout = "pairing prompt"),
                    disposed = lifetime.isExpired,
                ) {
                    delivered += it
                }

            assertTrue(lifetime.isExpired)
            assertEquals(PromptDelivery.EXPIRED, result)
            assertEquals(emptyList<String>(), delivered)
            assertEquals(1, leaseCloseCount)

            lifetime.dispose()
            assertEquals(1, leaseCloseCount)
        } finally {
            if (!parentDisposed) Disposer.dispose(parent)
        }
    }
}
