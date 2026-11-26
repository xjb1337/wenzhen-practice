package com.xjb.wenzhen.data.api

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import okhttp3.Call
import okhttp3.Response

// Cancel the exchange even while the I/O thread is blocked reading the response body.
@OptIn(InternalCoroutinesApi::class)
internal suspend fun <T> executeCall(call: Call, block: suspend (Response) -> T): T {
    val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true) {
        call.cancel()
    }
    try {
        return call.execute().use { response -> block(response) }
    } finally {
        cancellation.dispose()
    }
}
