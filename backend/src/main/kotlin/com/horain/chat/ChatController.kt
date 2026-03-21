package com.horain.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.agent.AgentFeedbackService
import com.horain.llm.LlmClient
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Chat API controller.
 * POST /chat/message - send a user message and receive assistant response.
 * GET /chat/status - returns whether LLM is configured (for debugging).
 */
@RestController
@RequestMapping("/chat")
class ChatController(
    private val chatService: LlmChatService,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val agentFeedbackService: AgentFeedbackService,
    private val reasoningSummarizerService: ReasoningSummarizerService
) {

    @PostMapping(value = ["/message/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMessage(@RequestBody request: ChatMessageRequest?): SseEmitter {
        val userMessage = request?.message?.trim().orEmpty()
        if (userMessage.isBlank()) {
            val emitter = SseEmitter(SSE_EMITTER_TIMEOUT_MS)
            emitter.completeWithError(IllegalArgumentException("Please provide a message."))
            return emitter
        }
        val history = request?.history ?: emptyList()
        val contextEntries = request?.contextEntries ?: emptyList()
        val contextProjects = request?.contextProjects ?: emptyList()
        val conversationId = parseConversationId(request?.conversationId)
        val emitter = SseEmitter(SSE_EMITTER_TIMEOUT_MS)
        val writer = SseEmitterStreamEventWriter(emitter, objectMapper)
        CompletableFuture.runAsync {
            try {
                chatService.chatStream(userMessage, history, contextEntries, contextProjects, conversationId, writer)
            } catch (e: Exception) {
                writer.sendError(e.message ?: "Unknown error")
            }
        }
        return emitter
    }

    @PostMapping("/message")
    fun message(@RequestBody request: ChatMessageRequest?): ResponseEntity<ChatMessageResponse> {
        val userMessage = request?.message?.trim().orEmpty()
        if (userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                .body(ChatMessageResponse("Please provide a message.", null, null, null, null))
        }
        val history = request?.history ?: emptyList()
        val contextEntries = request?.contextEntries ?: emptyList()
        val contextProjects = request?.contextProjects ?: emptyList()
        val conversationId = parseConversationId(request?.conversationId)
        val response = chatService.chat(userMessage, history, contextEntries, contextProjects, conversationId)
        return ResponseEntity.ok(
            ChatMessageResponse(
                response.assistantMessage,
                response.toolCalls.map { tc -> ToolCallDto(tc.name, tc.arguments, tc.result) },
                response.data,
                response.turnId,
                response.conversationId
            )
        )
    }

    data class ChatMessageRequest(
        val message: String?,
        val history: List<ChatHistoryEntry>?,
        val contextEntries: List<Map<String, Any?>>?,
        val contextProjects: List<Map<String, Any?>>?,
        /** Omit or null for the first message in a thread; send back the value from the previous response for follow-ups. */
        val conversationId: String?
    )

    data class ChatMessageResponse(
        val assistantMessage: String,
        val toolCalls: List<ToolCallDto>?,
        val data: Any?,
        val turnId: UUID?,
        /** Null on validation error responses only. */
        val conversationId: UUID?
    )

    data class ToolCallDto(
        val name: String,
        val arguments: String,
        val result: String
    )

    @PostMapping("/feedback")
    fun feedback(@RequestBody request: FeedbackRequest?): ResponseEntity<Map<String, Any>> {
        if (request?.turnId == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "turnId is required"))
        }
        return try {
            val turnId = UUID.fromString(request.turnId)
            agentFeedbackService.saveFeedback(turnId, request.rating, request.reasonCode, request.comment)
            ResponseEntity.ok(mapOf("ok" to true))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid turnId")))
        }
    }

    data class FeedbackRequest(
        val turnId: String?,
        val rating: String?,
        val reasonCode: String?,
        val comment: String?
    )

    @GetMapping("/status")
    fun status(): Map<String, Any> = mapOf("llmConfigured" to llmClient.isConfigured())

    /**
     * Cursor-style: summarize a reasoning phase in one short sentence (lightweight LLM).
     * POST body: { "text": "..." }. Response: { "summary": "..." } or 400 when text too short / missing.
     */
    @PostMapping("/summarize-reasoning")
    fun summarizeReasoning(@RequestBody request: SummarizeReasoningRequest?): ResponseEntity<Map<String, String>> {
        val text = request?.text
        if (text.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "text is required"))
        }
        val summary = reasoningSummarizerService.summarize(text.trim())
        return ResponseEntity.ok(mapOf("summary" to (summary ?: "")))
    }

    data class SummarizeReasoningRequest(val text: String?)

    companion object {
        private const val SSE_EMITTER_TIMEOUT_MS = 300_000L

        private fun parseConversationId(raw: String?): UUID? {
            val s = raw?.trim().orEmpty()
            if (s.isEmpty()) return null
            return try {
                UUID.fromString(s)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
