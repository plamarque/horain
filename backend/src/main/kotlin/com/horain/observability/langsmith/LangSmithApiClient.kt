package com.horain.observability.langsmith

import com.fasterxml.jackson.databind.JsonNode
import com.horain.observability.FeedbackEvent
import com.horain.observability.LangSmithProps
import com.horain.observability.TurnCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Minimal LangSmith REST client: create runs and post feedback.
 * See https://docs.smith.langchain.com/ for API details.
 */
class LangSmithApiClient(
    private val props: LangSmithProps,
    webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(LangSmithApiClient::class.java)
    private val baseUrl = props.endpoint.trimEnd('/')
    private val webClient: WebClient = webClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", props.apiKey)
        .build()

    /** Cached value for `Langsmith-Project`: resolved tracer session UUID, or project name as fallback. */
    private val langsmithProjectHeaderCache = AtomicReference<String?>()

    private fun projectName(): String = props.project.trim().ifEmpty { "default" }

    /**
     * Resolves [projectName] to the LangSmith tracer session id (UUID) via GET /api/v1/sessions,
     * for use in the Langsmith-Project header. Returns null if lookup fails.
     */
    private fun lookupTracerSessionId(projectName: String): String? {
        return try {
            val node = webClient.get()
                .uri { ub ->
                    ub.path("/api/v1/sessions")
                        .queryParam("name", projectName)
                        .queryParam("limit", 20)
                        .build()
                }
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
            if (node == null || !node.isArray) {
                log.debug("LangSmith: unexpected response when resolving project '{}'", projectName)
                return null
            }
            for (e in node) {
                val id = e.get("id")?.asText() ?: continue
                val n = e.get("name")?.asText()
                if (n == projectName) {
                    return id
                }
            }
            log.debug("LangSmith: no tracer session with exact name '{}' in listing", projectName)
            null
        } catch (ex: Exception) {
            log.warn("LangSmith: failed to resolve project id for '{}': {}", projectName, ex.message)
            null
        }
    }

    private fun langsmithProjectHeader(): String {
        langsmithProjectHeaderCache.get()?.let { return it }
        synchronized(this) {
            langsmithProjectHeaderCache.get()?.let { return it }
            val name = projectName()
            val id = lookupTracerSessionId(name)
            val header = id ?: name
            if (id != null) {
                log.debug("LangSmith: resolved tracing project '{}' to session id {}", name, id)
            } else {
                log.warn(
                    "LangSmith: could not resolve session id for '{}'; using name in Langsmith-Project header",
                    name
                )
            }
            langsmithProjectHeaderCache.set(header)
            return header
        }
    }

    private companion object {
        /** Max chars per field to avoid oversized LangSmith payloads. */
        private const val MAX_RUN_FIELD_CHARS = 32_000
    }

    private fun truncateForLangSmith(s: String): String {
        if (s.length <= MAX_RUN_FIELD_CHARS) return s
        return s.take(MAX_RUN_FIELD_CHARS) + "\n... [truncated]"
    }

    private fun reasoningBlockDurationMs(event: TurnCompletedEvent): Long =
        event.reasoningPhases.sumOf { phase -> phase.durationMs?.takeIf { it > 0 } ?: 1L }

    private fun threadMetadata(event: TurnCompletedEvent): Map<String, String> =
        mapOf(
            "model" to (event.model ?: ""),
            "status" to (event.status ?: ""),
            "turn_id" to event.turnId.toString(),
            "conversation_id" to event.conversationId.toString(),
            "thread_id" to event.conversationId.toString()
        )

    private fun postRun(body: Map<String, Any?>) {
        webClient.post()
            .uri("/runs")
            .header("Langsmith-Project", langsmithProjectHeader())
            .bodyValue(body)
            .retrieve()
            .onStatus({ it.isError }) { resp ->
                resp.bodyToMono(String::class.java).flatMap { err ->
                    log.warn("LangSmith POST /runs failed: status={} body={}", resp.statusCode(), err)
                    Mono.error(RuntimeException("LangSmith create run failed: ${resp.statusCode()}"))
                }
            }
            .toBodilessEntity()
            .block()
    }

    /**
     * Creates a run in LangSmith. Returns the run id used (generated UUID string).
     */
    fun createRun(runId: UUID, event: TurnCompletedEvent): String {
        val start = Instant.ofEpochMilli(event.startTimeEpochMs)
        val end = Instant.ofEpochMilli(event.startTimeEpochMs + (event.latencyMs ?: 0L))

        val inputs = mapOf(
            "input" to event.userMessage
        )
        val outputs = mapOf(
            "output" to (event.assistantMessage ?: ""),
            "tool_calls" to event.toolCallNames.joinToString(",")
        )
        // Do not set session_id to a client-generated UUID: LangSmith expects an existing tracer session
        // and returns 404 otherwise. Keep conversation_id in metadata for correlation.
        val rid = runId.toString()
        val body = mutableMapOf<String, Any?>(
            "id" to rid,
            // Same trace id for root + children so LangSmith groups one nested tree.
            "trace_id" to rid,
            "name" to "horain.agent.turn",
            "run_type" to "chain",
            "inputs" to inputs,
            "outputs" to outputs,
            "session_name" to projectName(),
            "start_time" to start.toString(),
            "end_time" to end.toString(),
            "extra" to mapOf(
                "metadata" to threadMetadata(event)
            )
        )

        postRun(body)
        return rid
    }

    /**
     * Child runs for each reasoning phase, linked via [parent_run_id] (LangSmith trace tree).
     */
    fun createReasoningChildRuns(parentRunId: String, event: TurnCompletedEvent) {
        if (event.reasoningPhases.isEmpty()) {
            return
        }
        var cursorMs = event.startTimeEpochMs
        event.reasoningPhases.forEachIndexed { index, phase ->
            val childId = UUID.randomUUID()
            val durMs = phase.durationMs?.takeIf { it > 0 } ?: 1L
            val start = Instant.ofEpochMilli(cursorMs)
            val end = Instant.ofEpochMilli(cursorMs + durMs)
            cursorMs += durMs
            val meta = threadMetadata(event).toMutableMap()
            meta["reasoning_phase_index"] = index.toString()
            val body = mutableMapOf<String, Any?>(
                "id" to childId.toString(),
                "trace_id" to parentRunId,
                "name" to "horain.agent.reasoning",
                "run_type" to "chain",
                "parent_run_id" to parentRunId,
                "inputs" to mapOf("phase_index" to index),
                "outputs" to mapOf("reasoning" to phase.text),
                "session_name" to projectName(),
                "start_time" to start.toString(),
                "end_time" to end.toString(),
                "extra" to mapOf("metadata" to meta)
            )
            postRun(body)
        }
    }

    /**
     * Child runs for each tool invocation ([run_type] = tool), linked via [parent_run_id].
     * Placed after reasoning phases on the timeline (same parent as reasoning children).
     */
    fun createToolChildRuns(parentRunId: String, event: TurnCompletedEvent) {
        if (event.toolCallSteps.isEmpty()) {
            return
        }
        var cursorMs = event.startTimeEpochMs + reasoningBlockDurationMs(event)
        event.toolCallSteps.forEachIndexed { index, tc ->
            val childId = UUID.randomUUID()
            val durMs = 1L
            val start = Instant.ofEpochMilli(cursorMs)
            val end = Instant.ofEpochMilli(cursorMs + durMs)
            cursorMs += durMs
            val meta = threadMetadata(event).toMutableMap()
            meta["tool_call_index"] = index.toString()
            val body = mutableMapOf<String, Any?>(
                "id" to childId.toString(),
                "trace_id" to parentRunId,
                "name" to tc.name,
                "run_type" to "tool",
                "parent_run_id" to parentRunId,
                "inputs" to mapOf("arguments" to truncateForLangSmith(tc.arguments)),
                "outputs" to mapOf("result" to truncateForLangSmith(tc.result)),
                "session_name" to projectName(),
                "start_time" to start.toString(),
                "end_time" to end.toString(),
                "extra" to mapOf("metadata" to meta)
            )
            postRun(body)
        }
    }

    fun postFeedback(runId: String, event: FeedbackEvent) {
        val score = when (event.rating.lowercase()) {
            "up" -> 1.0
            "down" -> 0.0
            else -> null
        }
        val commentStr = listOfNotNull(
            event.reasonCode?.let { "reason: $it" },
            event.comment?.takeIf { it.isNotBlank() }
        ).joinToString("; ").ifBlank { null }
        val body = mutableMapOf<String, Any?>(
            "run_id" to runId,
            "key" to "user_feedback"
        )
        if (score != null) {
            body["score"] = score
        }
        if (!commentStr.isNullOrBlank()) {
            body["comment"] = commentStr
        }

        webClient.post()
            .uri("/feedback")
            .header("Langsmith-Project", langsmithProjectHeader())
            .bodyValue(body)
            .retrieve()
            .onStatus({ it.isError }) { resp ->
                resp.bodyToMono(String::class.java).flatMap { err ->
                    log.warn("LangSmith POST /feedback failed: status={} body={}", resp.statusCode(), err)
                    Mono.error(RuntimeException("LangSmith feedback failed: ${resp.statusCode()}"))
                }
            }
            .toBodilessEntity()
            .block()
    }
}
