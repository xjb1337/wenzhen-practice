package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage

interface LlmClient {
    suspend fun complete(
        config: LlmConfig,
        messages: List<LlmMessage>,
        jsonMode: Boolean = false,
        temperature: Double = 0.7,
    ): String
}

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
