/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64

/** marimo uses `secrets.token_urlsafe(16)`; match that shape for compatibility. */
fun generateAccessToken(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun authenticatedMarimoUrl(host: String, port: Int, token: String): String =
    "http://$host:$port?access_token=$token"

/**
 * Writes [token] for `--token-password-file`. Each launch gets a new file under the IDE system
 * temp (`FileUtil.createTempFile` adds a random suffix) so two notebooks started in quick
 * succession never collide.
 */
fun writeTokenPasswordFile(token: String): File {
    val file = FileUtil.createTempFile("marimo-token-", ".txt", false)
    try {
        file.writeText(token)
        restrictTokenFilePermissions(file)
        return file
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

internal interface TokenFilePermissionOperations {
    fun setPosixFilePermissions(path: Path, permissions: Set<PosixFilePermission>)
    fun setReadable(file: File, readable: Boolean, ownerOnly: Boolean): Boolean
    fun setWritable(file: File, writable: Boolean, ownerOnly: Boolean): Boolean
    fun setExecutable(file: File, executable: Boolean, ownerOnly: Boolean): Boolean
}

private object DefaultTokenFilePermissionOperations : TokenFilePermissionOperations {
    override fun setPosixFilePermissions(path: Path, permissions: Set<PosixFilePermission>) {
        Files.setPosixFilePermissions(path, permissions)
    }

    override fun setReadable(file: File, readable: Boolean, ownerOnly: Boolean): Boolean =
        file.setReadable(readable, ownerOnly)

    override fun setWritable(file: File, writable: Boolean, ownerOnly: Boolean): Boolean =
        file.setWritable(writable, ownerOnly)

    override fun setExecutable(file: File, executable: Boolean, ownerOnly: Boolean): Boolean =
        file.setExecutable(executable, ownerOnly)
}

internal fun restrictTokenFilePermissions(
    file: File,
    operations: TokenFilePermissionOperations = DefaultTokenFilePermissionOperations,
) {
    val path = file.toPath()
    try {
        operations.setPosixFilePermissions(
            path,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    } catch (_: UnsupportedOperationException) {
        // Non-POSIX file system; reject the file if owner-only fallback cannot be applied.
        val applied = listOf(
            operations.setReadable(file, false, false),
            operations.setWritable(file, false, false),
            operations.setExecutable(file, false, false),
            operations.setReadable(file, true, true),
            operations.setWritable(file, true, true),
        )
        if (applied.any { !it }) throw IOException("Could not restrict token file permissions")
    }
}
