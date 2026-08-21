/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import io.marimo.notebook.MarimoLocalhost
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoAccessTokenTest {

    @Test
    fun authenticatedUrlCarriesTheToken() {
        assertEquals(
            "http://127.0.0.1:2718?access_token=abc",
            MarimoLocalhost.authenticatedUrl("127.0.0.1", 2718, "abc"),
        )
    }

    @Test
    fun tokenPasswordFileIsUniqueAndContainsTheToken() {
        val a = writeTokenPasswordFile("token-a")
        val b = writeTokenPasswordFile("token-b")
        try {
            assertFalse("parallel launches must not share a path", a.absolutePath == b.absolutePath)
            assertEquals("token-a", a.readText().trim())
            assertEquals("token-b", b.readText().trim())
        } finally {
            a.delete()
            b.delete()
        }
    }

    @Test
    fun generatedTokensAreNonEmpty() {
        assertTrue(generateAccessToken().isNotBlank())
    }

    @Test
    fun rejectedFallbackPermissionChangeFails() {
        val file = File.createTempFile("marimo-token-test-", ".txt")
        try {
            val operations =
                object : TokenFilePermissionOperations {
                    override fun setPosixFilePermissions(
                        path: Path,
                        permissions: Set<PosixFilePermission>,
                    ) {
                        throw UnsupportedOperationException()
                    }

                    override fun setReadable(
                        file: File,
                        readable: Boolean,
                        ownerOnly: Boolean,
                    ): Boolean = false

                    override fun setWritable(
                        file: File,
                        writable: Boolean,
                        ownerOnly: Boolean,
                    ): Boolean = true

                    override fun setExecutable(
                        file: File,
                        executable: Boolean,
                        ownerOnly: Boolean,
                    ): Boolean = true
                }

            assertThrows(IOException::class.java) { restrictTokenFilePermissions(file, operations) }
        } finally {
            file.delete()
        }
    }
}
