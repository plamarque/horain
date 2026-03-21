package com.horain.chat

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.UUID

/**
 * StreamEventWriter implementation that writes SSE events to an SseEmitter.
 * Event format: "chunk" (data = JSON {"text": "delta"}), "done" (data = JSON payload), "error" (data = JSON {"message": "..."}).
 */
class SseEmitterStreamEventWriter(
    private val emitter: SseEmitter,
    private val objectMapper: ObjectMapper
) : StreamEventWriter {

    private var completed: Boolean = false

    init {
        emitter.onTimeout {
            if (!completed) {
                completed = true
                log.debug("SSE emitter timed out")
            }
        }
        emitter.onCompletion { completed = true }
    }

    override fun sendChunk(text: String) {
        if (completed) return
        try {
            val data = objectMapper.writeValueAsString(mapOf("text" to (text.ifBlank { "" })))
            emitter.send(SseEmitter.event().name("chunk").data(data, MediaType.APPLICATION_JSON))
        } catch (e: IOException) {
            log.warn("Failed to send chunk: {}", e.message)
            completeWithError(e.message)
        }
    }

    override fun sendDone(
        assistantMessage: String,
        toolCalls: List<ToolCallRecord>,
        toolCallIterations: List<Int>?,
        data: Any?,
        turnId: UUID?,
        reasoningText: String?,
        reasoningDurationMs: Long?,
        modelName: String?,
        conversationId: UUID
    ) {
        if (completed) return
        try {
            val toolCallsDto =
                toolCalls.mapIndexed { i, tc ->
                    val m = hashMapOf<String, Any>(
                        "name" to tc.name,
                        "arguments" to tc.arguments,
                        "result" to tc.result
                    )
                    if (toolCallIterations != null && i < toolCallIterations.size) {
                        m["iterationIndex"] = toolCallIterations[i]
                    }
                    m
                }
            val payload = hashMapOf<String, Any>(
                "assistantMessage" to (assistantMessage.ifBlank { "" }),
                "toolCalls" to toolCallsDto,
                "data" to (data ?: emptyMap<String, Any>())
            )
            if (turnId != null) {
                payload["turnId"] = turnId.toString()
            }
            if (!reasoningText.isNullOrBlank()) {
                payload["reasoningText"] = reasoningText
            }
            if (reasoningDurationMs != null && reasoningDurationMs >= 0) {
                payload["reasoningDurationMs"] = reasoningDurationMs
            }
            if (!modelName.isNullOrBlank()) {
                payload["modelName"] = modelName
            }
            payload["conversationId"] = conversationId.toString()
            val json = objectMapper.writeValueAsString(payload)
            emitter.send(SseEmitter.event().name("done").data(json, MediaType.APPLICATION_JSON))
            emitter.complete()
            completed = true
        } catch (e: JsonProcessingException) {
            log.warn("Failed to serialize done payload: {}", e.message)
            completeWithError(e.message)
        } catch (e: IOException) {
            log.warn("Failed to send done: {}", e.message)
            completeWithError(e.message)
        }
    }

    override fun sendReasoningChunk(text: String) {
        if (completed) return
        try {
            val data = objectMapper.writeValueAsString(mapOf("text" to text.ifBlank { "" }))
            emitter.send(SseEmitter.event().name("reasoning_chunk").data(data, MediaType.APPLICATION_JSON))
        } catch (e: IOException) {
            log.warn("Failed to send reasoning_chunk: {}", e.message)
            completeWithError(e.message)
        }
    }

    override fun sendReasoningPhaseDone(reasoningDurationMs: Long?) {
        if (completed) return
        try {
            val data = hashMapOf<String, Any>()
            if (reasoningDurationMs != null && reasoningDurationMs >= 0) {
                data["reasoningDurationMs"] = reasoningDurationMs
            }
            val json = objectMapper.writeValueAsString(data)
            emitter.send(SseEmitter.event().name("reasoning_phase_done").data(json, MediaType.APPLICATION_JSON))
        } catch (e: IOException) {
            log.warn("Failed to send reasoning_phase_done: {}", e.message)
            completeWithError(e.message)
        }
    }

    override fun sendModelName(modelName: String) {
        if (completed || modelName.isBlank()) return
        try {
            val data = objectMapper.writeValueAsString(mapOf("model" to modelName))
            emitter.send(SseEmitter.event().name("model").data(data, MediaType.APPLICATION_JSON))
        } catch (e: IOException) {
            log.warn("Failed to send model event: {}", e.message)
        }
    }

    override fun sendError(message: String) {
        completeWithError(message)
    }

    override fun sendToolCall(record: ToolCallRecord, iterationIndex: Int) {
        if (completed) return
        try {
            val payload = mapOf(
                "name" to record.name,
                "arguments" to record.arguments,
                "result" to record.result,
                "iterationIndex" to iterationIndex
            )
            val json = objectMapper.writeValueAsString(payload)
            emitter.send(SseEmitter.event().name("tool_call").data(json, MediaType.APPLICATION_JSON))
        } catch (e: JsonProcessingException) {
            log.warn("Failed to serialize tool_call payload: {}", e.message)
        } catch (e: IOException) {
            log.warn("Failed to send tool_call: {}", e.message)
        }
    }

    override fun sendAssistantSegment(text: String, iterationIndex: Int) {
        if (completed) return
        try {
            val payload = mapOf(
                "text" to text.ifBlank { "" },
                "iterationIndex" to iterationIndex
            )
            val json = objectMapper.writeValueAsString(payload)
            emitter.send(SseEmitter.event().name("assistant_segment").data(json, MediaType.APPLICATION_JSON))
        } catch (e: IOException) {
            log.warn("Failed to send assistant_segment: {}", e.message)
            completeWithError(e.message)
        }
    }

    private fun completeWithError(message: String?) {
        if (completed) return
        try {
            val data = objectMapper.writeValueAsString(
                mapOf("message" to (message?.ifBlank { null } ?: "Unknown error"))
            )
            emitter.send(SseEmitter.event().name("error").data(data, MediaType.APPLICATION_JSON))
        } catch (e: Exception) {
            log.warn("Failed to send error event: {}", e.message)
        }
        try {
            emitter.completeWithError(RuntimeException(message ?: "Unknown error"))
        } catch (e: Exception) {
            log.debug("Emitter already completed: {}", e.message)
        }
        completed = true
    }

    companion object {
        private val log = LoggerFactory.getLogger(SseEmitterStreamEventWriter::class.java)
    }
}
