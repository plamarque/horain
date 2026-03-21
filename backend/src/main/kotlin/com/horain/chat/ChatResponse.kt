package com.horain.chat

import java.util.UUID
import kotlin.jvm.JvmRecord

/**
 * Response from the chat endpoint.
 */
@JvmRecord
data class ChatResponse(
    val assistantMessage: String,
    val toolCalls: List<ToolCallRecord>,
    val data: Any?,
    val turnId: UUID?,
    /** Stable id for the chat thread; reuse on follow-up requests for LangSmith threads and DB grouping. */
    val conversationId: UUID
)
