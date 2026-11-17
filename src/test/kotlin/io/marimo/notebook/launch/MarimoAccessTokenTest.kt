/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import io.marimo.notebook.MarimoLocalhost
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun tokenPasswordFileIsAccessibleToItsOwnerOnly() {
        val path = writeTokenPasswordFile("token").toPath()
        try {
            val views = path.fileSystem.supportedFileAttributeViews()
            if ("posix" in views) {
                assertEquals(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(path),
                )
            } else {
                val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                assertEquals(listOf(view.owner), view.acl.map { it.principal() })
            }
            assertEquals("token", Files.readString(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun posixFileSystemUsesPosixPermissions() {
        assertSame(PosixRestrictor, privateFileRestrictorFor(setOf("basic", "posix", "owner")))
    }

    @Test
    fun posixIsPreferredWhenBothViewsExist() {
        assertSame(PosixRestrictor, privateFileRestrictorFor(setOf("posix", "acl")))
    }

    @Test
    fun fileSystemWithoutPosixUsesAcl() {
        assertSame(AclRestrictor, privateFileRestrictorFor(setOf("basic", "dos", "acl", "owner")))
    }

    @Test
    fun fileSystemWithoutOwnerPermissionsIsRejected() {
        assertThrows(IOException::class.java) { privateFileRestrictorFor(setOf("basic")) }
    }
}
