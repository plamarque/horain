package com.horain.llm

import kotlin.jvm.JvmRecord

/**
 * Result of executing a tool call.
 */
@JvmRecord
data class ToolCallResult(
    val toolCallId: String,
    val content: String
)
