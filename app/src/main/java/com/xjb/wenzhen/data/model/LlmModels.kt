package com.xjb.wenzhen.data.model

data class LlmMessage(
    val role: String,
    val content: String,
)

data class LlmConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String,
)
