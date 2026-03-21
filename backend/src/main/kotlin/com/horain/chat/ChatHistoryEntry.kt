package com.horain.chat

import kotlin.jvm.JvmRecord

/**
 * A single message from conversation history.
 */
@JvmRecord
data class ChatHistoryEntry(
    val role: String,
    val content: String
)
