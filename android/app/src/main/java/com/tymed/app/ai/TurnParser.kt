package com.tymed.app.ai

import org.json.JSONObject

sealed interface ParsedTurn {
    data class Tool(val name: String, val arguments: Map<String, Any?>) : ParsedTurn
    data class Reply(val text: String) : ParsedTurn
    data class Unparseable(val raw: String) : ParsedTurn
}

private fun stripCodeFences(text: String): String {
    val trimmed = text.trim()
    val match = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", RegexOption.IGNORE_CASE).find(trimmed)
    return match?.groupValues?.get(1)?.trim() ?: trimmed
}

/** AICore has no structured-output guarantee, so the model sometimes wraps its JSON in prose
 * ("Sure! {...}") instead of returning pure JSON — grab the outermost {...} block rather
 * than requiring the whole response to parse as-is. */
private fun extractJsonObject(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return null
    return text.substring(start, end + 1)
}

private fun jsonToMap(obj: JSONObject): Map<String, Any?> =
    obj.keys().asSequence().associateWith { key -> jsonValueToKotlin(obj.get(key)) }

private fun jsonValueToKotlin(value: Any?): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> jsonToMap(value)
    is org.json.JSONArray -> (0 until value.length()).map { jsonValueToKotlin(value.get(it)) }
    else -> value
}

/** Converts a parsed-arguments map (possibly containing nested Lists from JSON arrays) back into
 * a [JSONObject] — used to re-serialize a tool call into the transcript exactly as the model
 * would have written it. */
fun mapToJson(map: Map<String, Any?>): JSONObject {
    val obj = JSONObject()
    for ((key, value) in map) obj.put(key, kotlinValueToJson(value))
    return obj
}

private fun kotlinValueToJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> {
        val obj = JSONObject()
        for ((k, v) in value) obj.put(k.toString(), kotlinValueToJson(v))
        obj
    }
    is List<*> -> org.json.JSONArray(value.map { kotlinValueToJson(it) })
    else -> value
}

/** Defensively parses one model turn into a tool call, a direct reply, or "couldn't make sense
 * of this" — never throws. */
fun parseTurn(raw: String): ParsedTurn {
    val stripped = stripCodeFences(raw)
    val candidate = extractJsonObject(stripped) ?: stripped

    val parsed = try {
        JSONObject(candidate)
    } catch (error: Exception) {
        return ParsedTurn.Unparseable(raw)
    }

    val reply = parsed.opt("reply")
    if (reply is String) return ParsedTurn.Reply(reply)

    val tool = parsed.opt("tool")
    if (tool is String) {
        val argsObj = parsed.opt("arguments") as? JSONObject
        return ParsedTurn.Tool(tool, argsObj?.let(::jsonToMap) ?: emptyMap())
    }

    return ParsedTurn.Unparseable(raw)
}
