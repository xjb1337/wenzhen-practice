package com.xjb.wenzhen.data.api

import org.json.JSONObject

sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data object Done : SseEvent
    data object Ignore : SseEvent
}

object SseParser {
    fun parseLine(line: String): SseEvent {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return SseEvent.Ignore
        if (!trimmed.startsWith("data:")) return SseEvent.Ignore

        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return SseEvent.Ignore
        if (payload == "[DONE]") return SseEvent.Done

        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return SseEvent.Ignore
        val content = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.opt("content") as? String
        return if (content.isNullOrEmpty()) SseEvent.Ignore else SseEvent.Delta(content)
    }
}
