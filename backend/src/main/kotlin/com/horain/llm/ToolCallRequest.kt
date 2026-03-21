package com.horain.llm

import kotlin.jvm.JvmRecord

/**
 * A tool call requested by the LLM.
 */
@JvmRecord
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: String
)
