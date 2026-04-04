package com.horain.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import java.util.function.Consumer

/**
 * Deterministic [StreamingLlmClient] for Playwright e2e. Drives the real [com.horain.chat.LlmChatService]
 * tool loop without external API calls.
 */
class E2eStubStreamingLlmClient(
    objectMapper: ObjectMapper,
    timeLogService: TimeLogService,
    projectService: ProjectService
) : StreamingLlmClient {

    private val resolver = E2eChatStubScenarioResolver(objectMapper, timeLogService, projectService)

    override fun isConfigured(): Boolean = true

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse =
        chatStream(messages, tools, Consumer { }, null)

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>,
        reasoningConsumer: Consumer<String>?
    ): LlmResponse {
        val lastUser = E2eChatStubScenarioResolver.lastUserContent(messages)
        val round = E2eChatStubScenarioResolver.countToolRoundsAfterLastUser(messages)
        val response = resolver.resolve(lastUser, round, messages)
        if (!response.content.isNullOrBlank()) {
            resolver.streamContentToConsumer(response.content, textConsumer)
        }
        return response
    }
}
