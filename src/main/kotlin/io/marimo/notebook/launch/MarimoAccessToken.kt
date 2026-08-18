/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.openapi.util.io.FileUtil
import java.io.File
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
    val file = FileUtil.createTempFile("marimo-token-", ".txt", true)
    file.writeText(token)
    return file
}
