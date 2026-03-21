package com.horain.llm

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi

/**
 * LlmClient implementation using Spring AI ChatModel.
 * Delegates to OpenAiChatModel (or compatible) for OpenAI-compatible APIs.
 */
class SpringAiLlmClient(
    private val chatModel: ChatModel,
    llmProperties: LlmProperties?
) : LlmClient {

    private val model: String = llmProperties?.resolvedModel() ?: DEFAULT_MODEL

    override fun isConfigured(): Boolean = chatModel != null

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        val springMessages = toSpringMessages(messages)
        val options = buildChatOptions(tools)
        val prompt = Prompt(springMessages, options)
        val response = chatModel.call(prompt)
        return toLlmResponse(response)
    }

    private fun toSpringMessages(messages: List<ChatMessage>?): List<org.springframework.ai.chat.messages.Message> {
        if (messages.isNullOrEmpty()) {
            return emptyList()
        }
        val result = mutableListOf<org.springframework.ai.chat.messages.Message>()
        for (m in messages) {
            val role = m.role ?: continue
            when (role.lowercase()) {
                "system" -> result.add(SystemMessage(m.content ?: ""))
                "user" -> result.add(UserMessage(m.content ?: ""))
                "assistant" -> {
                    if (!m.toolCalls.isNullOrEmpty()) {
                        val toolCalls = m.toolCalls!!.map { tc ->
                            AssistantMessage.ToolCall(
                                tc.id,
                                "function",
                                tc.name,
                                tc.arguments ?: "{}"
                            )
                        }
                        result.add(
                            AssistantMessage.builder()
                                .content(m.content ?: "")
                                .toolCalls(toolCalls)
                                .build()
                        )
                    } else {
                        result.add(AssistantMessage(m.content ?: ""))
                    }
                }
                "tool" -> {
                    val toolResponse = ToolResponseMessage.ToolResponse(
                        m.toolCallId ?: "",
                        "",
                        m.content ?: ""
                    )
                    result.add(
                        ToolResponseMessage.builder()
                            .responses(listOf(toolResponse))
                            .build()
                    )
                }
                else -> { /* ignore unknown roles */ }
            }
        }
        return result
    }

    private fun buildChatOptions(tools: List<ToolDefinition>?): OpenAiChatOptions {
        val options = OpenAiChatOptions.builder().model(model).build()
        options.internalToolExecutionEnabled = false
        if (!tools.isNullOrEmpty()) {
            val functionTools = tools.map { t ->
                OpenAiApi.FunctionTool(
                    OpenAiApi.FunctionTool.Function(
                        t.description,
                        t.name,
                        t.parameters ?: emptyMap(),
                        false
                    )
                )
            }
            options.tools = functionTools
        }
        return options
    }

    private fun toLlmResponse(response: org.springframework.ai.chat.model.ChatResponse?): LlmResponse {
        if (response == null || response.results.isNullOrEmpty()) {
            return LlmResponse("", null, "stop")
        }
        val gen = response.result
        val output = gen.output ?: return LlmResponse("", null, "stop")
        val content = output.text ?: ""
        var toolCalls: List<ToolCallRequest>? = null
        if (output.hasToolCalls() && output.toolCalls != null) {
            toolCalls = output.toolCalls!!.map { tc ->
                ToolCallRequest(
                    tc.id ?: "",
                    tc.name ?: "",
                    tc.arguments ?: "{}"
                )
            }
        }
        var finishReason = "stop"
        if (gen.metadata != null && gen.metadata.finishReason != null) {
            val reason = gen.metadata.finishReason
            finishReason = when (reason) {
                is Enum<*> -> reason.name.lowercase()
                else -> reason.toString().lowercase()
            }
        }
        return LlmResponse(content, toolCalls, finishReason)
    }

    companion object {
        private const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
