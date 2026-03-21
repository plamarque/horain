package com.horain.llm

import kotlin.jvm.JvmRecord

/**
 * Definition of a tool for the LLM (OpenAI function-calling format).
 */
@JvmRecord
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)
