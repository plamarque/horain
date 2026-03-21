package com.horain.llm

import kotlin.jvm.JvmRecord

/**
 * A message in the chat conversation.
 * For tool-role messages, toolCallId must be set.
 */
@JvmRecord
data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCallRequest>?,
    val toolCallId: String?
) {
    companion object {
        @JvmStatic
        fun user(content: String) = ChatMessage("user", content, null, null)

        @JvmStatic
        fun assistant(content: String) = ChatMessage("assistant", content, null, null)

        @JvmStatic
        fun assistantWithToolCalls(content: String, toolCalls: List<ToolCallRequest>) =
            ChatMessage("assistant", content, toolCalls, null)

        @JvmStatic
        fun tool(content: String, toolCallId: String) =
            ChatMessage("tool", content, null, toolCallId)

        @JvmStatic
        fun system(content: String) = ChatMessage("system", content, null, null)
    }
}
