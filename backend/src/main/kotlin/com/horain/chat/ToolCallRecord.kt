package com.horain.chat

import kotlin.jvm.JvmRecord

/**
 * Record of a tool call for the response.
 */
@JvmRecord
data class ToolCallRecord(
    val name: String,
    val arguments: String,
    val result: String
)
