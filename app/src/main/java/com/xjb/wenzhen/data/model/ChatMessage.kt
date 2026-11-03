package com.xjb.wenzhen.data.model

enum class MessageRole {
    DOCTOR,
    PATIENT,
    SYSTEM,
}

enum class MessageDeliveryState {
    SENT,
    SENDING,
    FAILED,
}

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryState: MessageDeliveryState = MessageDeliveryState.SENT,
    val systemTitle: String? = null,
)
