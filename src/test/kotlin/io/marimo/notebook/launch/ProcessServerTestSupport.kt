/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

internal const val MARIMO_PAGE_BODY = """<html><marimo-user-config data-config="{}"></html>"""

internal fun httpResponse(statusLine: String, body: String = ""): ByteArray {
    if (body.isEmpty()) {
        return "HTTP/1.1 $statusLine\r\nContent-Length: 0\r\n\r\n".toByteArray()
    }
    return "HTTP/1.1 $statusLine\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body"
        .toByteArray()
}
