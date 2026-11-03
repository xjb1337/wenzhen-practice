package com.xjb.wenzhen.data.db

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MessageDeliveryState
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
                put("deliveryState", message.deliveryState.name)
                message.systemTitle?.let { put("systemTitle", it) }
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
                        MessageRole.SYSTEM.name -> MessageRole.SYSTEM
                        else -> MessageRole.DOCTOR
                    }
                    val deliveryState = when (item.optString("deliveryState")) {
                        MessageDeliveryState.SENDING.name -> MessageDeliveryState.SENDING
                        MessageDeliveryState.FAILED.name -> MessageDeliveryState.FAILED
                        else -> MessageDeliveryState.SENT
                    }
                    add(
                        ChatMessage(
                            role = role,
                            content = content,
                            timestamp = item.optLong("timestamp", 0L),
                            deliveryState = deliveryState,
                            systemTitle = item.optString("systemTitle").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
