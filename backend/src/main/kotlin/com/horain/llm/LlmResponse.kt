package com.horain.llm

import kotlin.jvm.JvmRecord

/**
 * Response from the LLM chat completion.
 *
 * @param content Assistant text content
 * @param toolCalls Tool calls if any (e.g. for tool-use models)
 * @param finishReason Finish reason from API (stop, length, etc.)
 * @param reasoningSummary Optional reasoning text when the model exposes it (e.g. Responses API)
 */
@JvmRecord
data class LlmResponse(
    val content: String?,
    val toolCalls: List<ToolCallRequest>?,
    val finishReason: String?,
    val reasoningSummary: String? = null
) {
    /** Constructor for callers that do not have reasoning (backward compatible). */
    constructor(content: String?, toolCalls: List<ToolCallRequest>?, finishReason: String?) :
        this(content, toolCalls, finishReason, null)

    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()

    fun isFinished(): Boolean =
        finishReason != null && finishReason.equals("stop", ignoreCase = true)
}
