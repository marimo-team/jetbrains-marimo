package com.github.kirangadhave.marimopycharm.server

import com.google.gson.JsonParser

data class ScratchpadResponse(
    val stdout: String,
    val success: Boolean,
    val outputData: String,
    val errorMsg: String?,
)

/**
 * Assembles a marimo /api/kernel/execute SSE stream into one result. Events:
 *   event: stdout\n data: {"data": "..."}      — concatenated
 *   event: done\n   data: {"success": bool, "output": {"data": "..."}, "error": {"msg": "..."}}
 */
fun parseScratchpadSse(raw: String): ScratchpadResponse {
    val stdout = StringBuilder()
    var success = false
    var outputData = ""
    var errorMsg: String? = null
    var event = ""

    for (line in raw.lineSequence()) {
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val json = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: continue
                when (event) {
                    "stdout" -> json.get("data")?.takeIf { !it.isJsonNull }?.let { stdout.append(it.asString) }
                    "done" -> {
                        success = json.get("success")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                        outputData = json.get("output")?.takeIf { it.isJsonObject }?.asJsonObject
                            ?.get("data")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        errorMsg = json.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                            ?.get("msg")?.takeIf { !it.isJsonNull }?.asString
                    }
                }
            }
        }
    }
    return ScratchpadResponse(stdout.toString(), success, outputData, errorMsg)
}
