package com.xjb.wenzhen.data.db

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MessageRole
import org.json.JSONArray
import org.json.JSONObject

object ConversationJsonCodec {
    fun encode(messages: List<ChatMessage>): String = JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().apply {
                put("role", message.role.name)
                put("content", message.content)
                put("timestamp", message.timestamp)
            })
        }
    }.toString()

    fun decode(raw: String): List<ChatMessage> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val content = item.optString("content").trim()
                    if (content.isEmpty()) continue
                    val role = when (item.optString("role")) {
                        MessageRole.PATIENT.name -> MessageRole.PATIENT
                        else -> MessageRole.DOCTOR
                    }
                    add(
                        ChatMessage(
                            role = role,
                            content = content,
                            timestamp = item.optLong("timestamp", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
