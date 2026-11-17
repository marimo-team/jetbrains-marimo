/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.EnumSet

/** marimo uses `secrets.token_urlsafe(16)`; match that shape for compatibility. */
fun generateAccessToken(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Writes [token] for `--token-password-file`. Each launch gets a new file under the IDE system temp
 * (`FileUtil.createTempFile` adds a random suffix) so two notebooks started in quick succession
 * never collide.
 */
fun writeTokenPasswordFile(token: String): File {
    val file = FileUtil.createTempFile("marimo-token-", ".txt", false)
    try {
        file.writeText(token)
        val path = file.toPath()
        privateFileRestrictorFor(path.fileSystem.supportedFileAttributeViews())
            .restrictToOwner(path)
        return file
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

/** Makes a file readable and writable by its owner only. */
internal fun interface PrivateFileRestrictor {
    fun restrictToOwner(path: Path)
}

internal object PosixRestrictor : PrivateFileRestrictor {
    override fun restrictToOwner(path: Path) {
        Files.setPosixFilePermissions(
            path,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    }
}

internal object AclRestrictor : PrivateFileRestrictor {
    override fun restrictToOwner(path: Path) {
        val view =
            Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                ?: throw IOException("Could not restrict token file permissions")
        val ownerOnly =
            AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(view.owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
                .build()
        view.acl = listOf(ownerOnly)
    }
}

/**
 * Selects the restrictor from the attribute views that the file system supports, because the views
 * decide which permission API works. A file system with neither view cannot protect the token, so
 * the launch must fail.
 */
internal fun privateFileRestrictorFor(supportedViews: Set<String>): PrivateFileRestrictor =
    when {
        "posix" in supportedViews -> PosixRestrictor
        "acl" in supportedViews -> AclRestrictor
        else -> throw IOException("Could not restrict token file permissions")
    }
