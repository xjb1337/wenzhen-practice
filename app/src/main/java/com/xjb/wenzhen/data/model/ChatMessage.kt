package com.xjb.wenzhen.data.model

enum class MessageRole {
    DOCTOR,
    PATIENT,
}

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)
