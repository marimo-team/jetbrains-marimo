package com.github.kirangadhave.marimopycharm.server

import com.github.kirangadhave.marimopycharm.vars.MarimoVar
import com.github.kirangadhave.marimopycharm.vars.VarsIntrospection
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.io.HttpRequests

/**
 * Talks to a running marimo server's HTTP API. Assumes the server was launched with --no-token (the
 * current spike default): marimo disables auth entirely when the token is empty, so no Authorization
 * header is sent. NOT safe to point at a token-protected server — that's the token-from-banner follow-up.
 */
class MarimoKernelClient(private val baseUrl: String) {

    fun resolveSessionId(notebookPath: String): String? {
        val body = runCatching { HttpRequests.request("$baseUrl/api/sessions").readString() }.getOrNull()
            ?: return null
        val sessions = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        for ((id, info) in sessions.entrySet()) {
            val path = info.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("path")?.takeIf { !it.isJsonNull }?.asString
            if (path == notebookPath) return id
        }
        return sessions.keySet().singleOrNull()
    }

    fun execute(sessionId: String, code: String): ScratchpadResponse {
        val raw = HttpRequests.post("$baseUrl/api/kernel/execute", "application/json")
            .tuner { it.setRequestProperty("Marimo-Session-Id", sessionId) }
            .connect { request ->
                request.write(JsonObject().apply { addProperty("code", code) }.toString())
                request.readString()
            }
        return parseScratchpadSse(raw)
    }

    fun readVariables(notebookPath: String): List<MarimoVar> {
        val sessionId = resolveSessionId(notebookPath) ?: return emptyList()
        return VarsIntrospection.parse(execute(sessionId, VarsIntrospection.SCRIPT).stdout)
    }
}
