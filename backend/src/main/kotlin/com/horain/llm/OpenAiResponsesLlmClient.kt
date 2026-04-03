package com.horain.llm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * LLM client for OpenAI Responses API (/v1/responses).
 * Supports reasoning models (o1, o3, o4-mini, etc.) with streaming reasoning summary
 * via response.reasoning_summary_text.delta events.
 */
class OpenAiResponsesLlmClient : StreamingLlmClient {

    private val baseUrl: String
    private val apiKey: String
    private val model: String
    private val reasoningEffort: String
    private val webClient: WebClient
    private val objectMapper: ObjectMapper

    constructor(
        properties: LlmProperties,
        webClient: WebClient,
        objectMapper: ObjectMapper
    ) : this(
        properties.resolvedBaseUrl().trim(),
        properties.apiKey?.trim() ?: "",
        properties.resolvedModel(),
        null,
        webClient,
        objectMapper
    )

    /**
     * Constructor for multi-model routing: specify model and reasoning effort per level.
     */
    constructor(
        baseUrl: String,
        apiKey: String,
        model: String,
        reasoningEffort: String?,
        webClient: WebClient,
        objectMapper: ObjectMapper
    ) {
        this.baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" }
        this.apiKey = apiKey
        this.model = if (model.isNotBlank()) model else "gpt-4o-mini"
        this.reasoningEffort = if (!reasoningEffort.isNullOrBlank()) reasoningEffort else DEFAULT_REASONING_EFFORT
        this.webClient = webClient
        this.objectMapper = objectMapper
    }

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        val url = baseUrl.replace(Regex("/$"), "") + "/responses"
        val body = buildRequestBody(messages, tools, false)
        val responseBody = webClient.post()
            .uri(url)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .bodyValue(body.toString())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        return parseNonStreamResponse(responseBody)
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>,
        reasoningConsumer: Consumer<String>?
    ): LlmResponse {
        val url = baseUrl.replace(Regex("/$"), "") + "/responses"
        val body = buildRequestBody(messages, tools, true)
        val contentAccumulator = StringBuilder()
        val reasoningAccumulator = StringBuilder()
        val toolCallsAccumulator = mutableListOf<ToolCallRequest>()
        val itemIdToName = mutableMapOf<String, String>()
        val resultRef = AtomicReference<LlmResponse?>()
        val latch = CountDownLatch(1)
        val lineBuffer = StringBuilder()
        val bodyFlux: Flux<DataBuffer> = webClient.post()
            .uri(url)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .bodyValue(body.toString())
            .retrieve()
            .bodyToFlux(DataBuffer::class.java)
        bodyFlux.subscribe(
            { dataBuffer ->
                val chunk = dataBuffer.toString(StandardCharsets.UTF_8)
                lineBuffer.append(chunk)
                var idx: Int
                while (lineBuffer.indexOf("\n").also { idx = it } >= 0) {
                    var line = lineBuffer.substring(0, idx).trim()
                    lineBuffer.delete(0, idx + 1)
                    if (line.isEmpty()) continue
                    if (line.startsWith("data:")) {
                        line = line.substring(5).trim()
                        if (line.isEmpty() || line == "[DONE]") continue
                    }
                    try {
                        val event = objectMapper.readTree(line)
                        val type = if (event.has("type")) event.get("type").asText("") else ""
                        when (type) {
                            "response.reasoning_summary_text.delta" -> {
                                if (event.has("delta")) {
                                    val delta = event.get("delta").asText("")
                                    if (delta.isNotEmpty()) {
                                        reasoningAccumulator.append(delta)
                                        reasoningConsumer?.accept(delta)
                                    }
                                }
                            }
                            "response.reasoning_summary_text.done" -> {
                                if (event.has("text")) {
                                    val full = event.get("text").asText("")
                                    if (full.isNotEmpty() && reasoningAccumulator.isEmpty()) {
                                        reasoningAccumulator.append(full)
                                    }
                                }
                            }
                            "response.output_text.delta" -> {
                                if (event.has("delta")) {
                                    val delta = event.get("delta").asText("")
                                    if (delta.isNotEmpty()) {
                                        contentAccumulator.append(delta)
                                        textConsumer.accept(delta)
                                    }
                                }
                            }
                            "response.output_text.done" -> {
                                if (event.has("text")) {
                                    val full = event.get("text").asText("")
                                    if (full.isNotEmpty() && contentAccumulator.isEmpty()) {
                                        contentAccumulator.append(full)
                                    }
                                }
                            }
                            "response.output_item.added" -> {
                                val item: JsonNode? = if (event.has("item")) event.get("item") else null
                                if (item != null &&
                                    (if (item.has("type")) item.get("type").asText("") else "") == "function_call"
                                ) {
                                    val itemId = when {
                                        item.has("id") -> item.get("id").asText("")
                                        item.has("call_id") -> item.get("call_id").asText("")
                                        else -> ""
                                    }
                                    val itemName = if (item.has("name")) item.get("name").asText("") else ""
                                    if (itemId.isNotEmpty()) {
                                        itemIdToName[itemId] = itemName
                                    }
                                }
                            }
                            "response.function_call_arguments.delta" -> {
                                // Accumulate per item_id; we only need final args from .done
                            }
                            "response.function_call_arguments.done" -> {
                                var id = if (event.has("item_id")) event.get("item_id").asText("") else ""
                                var name = if (event.has("name")) event.get("name").asText("") else ""
                                if (name.isBlank()) {
                                    name = itemIdToName[id] ?: ""
                                }
                                val args = if (event.has("arguments")) event.get("arguments").asText("{}") else "{}"
                                toolCallsAccumulator.add(ToolCallRequest(id, name, args))
                            }
                            else -> { /* ignore other events */ }
                        }
                    } catch (_: Exception) {
                        // Ignore malformed lines
                    }
                }
            },
            { err ->
                when (err) {
                    is WebClientResponseException -> {
                        val errorBody = err.responseBodyAsString
                        log.warn(
                            "Responses API stream failed: model={} status={} body={}",
                            model,
                            err.statusCode,
                            if (errorBody.length > 500) errorBody.substring(0, 500) + "..." else errorBody
                        )
                    }
                    else -> log.warn("Responses API stream failed: model={} error={}", model, err.message, err)
                }
                resultRef.set(
                    LlmResponse(
                        contentAccumulator.toString(),
                        if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                        "error",
                        if (reasoningAccumulator.isEmpty()) null else reasoningAccumulator.toString()
                    )
                )
                latch.countDown()
            },
            {
                if (contentAccumulator.isEmpty()) {
                    log.warn("Responses API stream completed with empty content (model={})", model)
                }
                resultRef.set(
                    LlmResponse(
                        contentAccumulator.toString(),
                        if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                        "stop",
                        if (reasoningAccumulator.isEmpty()) null else reasoningAccumulator.toString()
                    )
                )
                latch.countDown()
            }
        )
        return try {
            if (!latch.await(STREAM_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)) {
                LlmResponse(
                    contentAccumulator.toString(),
                    if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                    "stop",
                    if (reasoningAccumulator.isEmpty()) null else reasoningAccumulator.toString()
                )
            } else {
                resultRef.get() ?: LlmResponse(
                    contentAccumulator.toString(),
                    if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                    "stop",
                    if (reasoningAccumulator.isEmpty()) null else reasoningAccumulator.toString()
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LlmResponse(
                contentAccumulator.toString(),
                if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                "stop",
                if (reasoningAccumulator.isEmpty()) null else reasoningAccumulator.toString()
            )
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>?, stream: Boolean): ObjectNode {
        val body = objectMapper.createObjectNode()
        body.put("model", model)
        body.put("stream", stream)
        val reasoning = objectMapper.createObjectNode()
        reasoning.put("effort", reasoningEffort)
        reasoning.put("summary", "auto")
        body.set<JsonNode>("reasoning", reasoning)
        val inputArray = objectMapper.createArrayNode()
        for (msg in messages) {
            val role = msg.role
            when {
                role == "system" -> {
                    val sysMsg = objectMapper.createObjectNode()
                    sysMsg.put("role", "system")
                    sysMsg.put("content", msg.content ?: "")
                    inputArray.add(sysMsg)
                }
                role == "user" -> {
                    val userMsg = objectMapper.createObjectNode()
                    userMsg.put("role", "user")
                    if (!msg.content.isNullOrBlank()) {
                        val contentParts = objectMapper.createArrayNode()
                        val textPart = objectMapper.createObjectNode()
                        textPart.put("type", "input_text")
                        textPart.put("text", msg.content)
                        contentParts.add(textPart)
                        userMsg.set<JsonNode>("content", contentParts)
                    } else {
                        userMsg.put("content", "")
                    }
                    inputArray.add(userMsg)
                }
                role == "assistant" -> {
                    val asstMsg = objectMapper.createObjectNode()
                    asstMsg.put("role", "assistant")
                    if (!msg.content.isNullOrBlank()) {
                        val contentParts = objectMapper.createArrayNode()
                        val textPart = objectMapper.createObjectNode()
                        textPart.put("type", "output_text")
                        textPart.put("text", msg.content)
                        contentParts.add(textPart)
                        asstMsg.set<JsonNode>("content", contentParts)
                    } else {
                        asstMsg.putArray("content")
                    }
                    inputArray.add(asstMsg)
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        for (tc in msg.toolCalls!!) {
                            val fcNode = objectMapper.createObjectNode()
                            fcNode.put("type", "function_call")
                            fcNode.put("call_id", tc.id)
                            fcNode.put("name", tc.name)
                            fcNode.put("arguments", tc.arguments ?: "{}")
                            inputArray.add(fcNode)
                        }
                    }
                }
                role == "tool" && msg.toolCallId != null -> {
                    val toolOutput = objectMapper.createObjectNode()
                    toolOutput.put("type", "function_call_output")
                    toolOutput.put("call_id", msg.toolCallId)
                    toolOutput.put("output", msg.content ?: "")
                    inputArray.add(toolOutput)
                }
            }
        }
        body.set<JsonNode>("input", inputArray)
        if (!tools.isNullOrEmpty()) {
            val toolsArray = objectMapper.createArrayNode()
            for (t in tools) {
                val toolNode = objectMapper.createObjectNode()
                toolNode.put("type", "function")
                toolNode.put("name", t.name)
                toolNode.put("description", t.description ?: "")
                if (!t.parameters.isNullOrEmpty()) {
                    toolNode.set<JsonNode>("parameters", objectMapper.valueToTree(t.parameters))
                } else {
                    toolNode.putObject("parameters")
                }
                toolsArray.add(toolNode)
            }
            body.set<JsonNode>("tools", toolsArray)
        }
        return body
    }

    private fun parseNonStreamResponse(json: String?): LlmResponse {
        if (json.isNullOrBlank()) {
            return LlmResponse("", null, "stop", null)
        }
        try {
            val root = objectMapper.readTree(json)
            val output = root.path("output")
            var content = ""
            var reasoningSummary: String? = null
            val toolCalls = mutableListOf<ToolCallRequest>()
            if (output.isArray) {
                for (item in output) {
                    val type = if (item.has("type")) item.get("type").asText("") else ""
                    if (type == "message" && item.path("role").asText("") == "assistant") {
                        val contentArr = item.path("content")
                        if (contentArr.isArray) {
                            for (part in contentArr) {
                                if (part.has("type") && part.get("type").asText("") == "output_text" && part.has("text")) {
                                    content += part.get("text").asText("")
                                }
                            }
                        }
                    } else if (type == "reasoning" && item.has("summary") && item.get("summary").isArray) {
                        for (sumPart in item.get("summary")) {
                            if (sumPart.has("type") && sumPart.get("type").asText("") == "summary_text" && sumPart.has("text")) {
                                reasoningSummary = (reasoningSummary ?: "") + sumPart.get("text").asText("")
                            }
                        }
                    } else if (type == "function_call") {
                        val id = if (item.has("id")) item.get("id").asText("") else ""
                        val name = if (item.has("name")) item.get("name").asText("") else ""
                        val args = if (item.has("arguments")) item.get("arguments").asText("{}") else "{}"
                        toolCalls.add(ToolCallRequest(id, name, args))
                    }
                }
            }
            val finishReason = if (root.has("status")) root.get("status").asText("completed") else "completed"
            return LlmResponse(content, if (toolCalls.isEmpty()) null else toolCalls, finishReason, reasoningSummary)
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse Responses API response", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OpenAiResponsesLlmClient::class.java)
        private const val STREAM_TIMEOUT_SECONDS = 120
        private const val DEFAULT_REASONING_EFFORT = "medium"
    }
}
