package com.horain.llm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * LLM client for OpenAI-compatible APIs (OpenAI, OpenRouter, LiteLLM, etc.).
 * Configure via LLM_BASE_URL, LLM_API_KEY, LLM_MODEL.
 */
class OpenAiCompatibleLlmClient : StreamingLlmClient {

    private val baseUrl: String
    private val apiKey: String
    private val model: String
    private val restTemplate: RestTemplate
    private val webClient: WebClient
    private val objectMapper: ObjectMapper

    constructor(
        properties: LlmProperties,
        restTemplate: RestTemplate,
        webClient: WebClient,
        objectMapper: ObjectMapper
    ) : this(
        properties.resolvedBaseUrl().trim(),
        properties.apiKey?.trim() ?: "",
        properties.resolvedModel(),
        restTemplate,
        webClient,
        objectMapper
    )

    /**
     * Constructor for multi-model routing: specify base URL, API key and model for the simple (no-reasoning) level.
     */
    constructor(
        baseUrl: String,
        apiKey: String,
        model: String,
        restTemplate: RestTemplate,
        webClient: WebClient,
        objectMapper: ObjectMapper
    ) {
        this.baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" }
        this.apiKey = apiKey
        this.model = if (model.isNotBlank()) model else "gpt-4o-mini"
        this.restTemplate = restTemplate
        this.webClient = webClient
        this.objectMapper = objectMapper
    }

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        val url = baseUrl.replace(Regex("/$"), "") + "/chat/completions"
        val body = buildRequestBody(messages, tools)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(apiKey)
        val entity = HttpEntity(body.toString(), headers)
        var last429: HttpClientErrorException? = null
        for (attempt in 0..MAX_RETRIES_429) {
            try {
                val response = restTemplate.exchange(url, HttpMethod.POST, entity, String::class.java)
                return parseResponse(response.body)
            } catch (e: HttpClientErrorException) {
                if (e.statusCode.value() == 429) {
                    last429 = e
                    if (attempt == MAX_RETRIES_429) {
                        break
                    }
                    val delayMs = parseRetryAfterMs(e.responseBodyAsString)
                    try {
                        Thread.sleep(delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException("Interrupted while waiting for rate limit", ie)
                    }
                } else {
                    throw e
                }
            }
        }
        throw last429 ?: RuntimeException("Unexpected error in chat")
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>,
        reasoningConsumer: Consumer<String>?
    ): LlmResponse {
        val url = baseUrl.replace(Regex("/$"), "") + "/chat/completions"
        val body = buildRequestBody(messages, tools)
        body.put("stream", true)
        val contentAccumulator = StringBuilder()
        val toolCallsAccumulator = mutableListOf<ToolCallRequest>()
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
                    val line = lineBuffer.substring(0, idx).trim()
                    lineBuffer.delete(0, idx + 1)
                    if (!line.startsWith("data: ")) continue
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") continue
                    try {
                        val root = objectMapper.readTree(data)
                        val choices = root.path("choices")
                        if (choices.isEmpty || !choices.isArray) continue
                        val choice = choices[0]
                        val delta = choice.path("delta")
                        if (delta.has("content") && !delta.get("content").isNull) {
                            val text = delta.get("content").asText("")
                            if (text.isNotEmpty()) {
                                textConsumer.accept(text)
                            }
                            contentAccumulator.append(text)
                        }
                        if (delta.has("tool_calls") && delta.get("tool_calls").isArray) {
                            for (tc in delta.get("tool_calls")) {
                                val toolIndex = tc.path("index").asInt(0)
                                while (toolCallsAccumulator.size <= toolIndex) {
                                    toolCallsAccumulator.add(ToolCallRequest("", "", ""))
                                }
                                val existing = toolCallsAccumulator[toolIndex]
                                var id = if (tc.has("id") && !tc.get("id").isNull) {
                                    tc.get("id").asText()
                                } else {
                                    existing.id
                                }
                                var name = existing.name
                                var args = existing.arguments
                                if (tc.has("function") && tc.get("function").isObject) {
                                    val fn = tc.get("function")
                                    if (fn.has("name") && !fn.get("name").isNull) {
                                        name = fn.get("name").asText()
                                    }
                                    if (fn.has("arguments") && !fn.get("arguments").isNull) {
                                        args = args + fn.get("arguments").asText()
                                    }
                                }
                                toolCallsAccumulator[toolIndex] = ToolCallRequest(id, name, args)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore malformed SSE lines
                    }
                }
            },
            {
                resultRef.set(LlmResponse("", null, "error"))
                latch.countDown()
            },
            {
                val tc = if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator
                resultRef.set(LlmResponse(contentAccumulator.toString(), tc, "stop"))
                latch.countDown()
            }
        )
        return try {
            if (!latch.await(STREAM_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)) {
                LlmResponse(
                    contentAccumulator.toString(),
                    if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                    "stop"
                )
            } else {
                resultRef.get() ?: LlmResponse(
                    contentAccumulator.toString(),
                    if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                    "stop"
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LlmResponse(
                contentAccumulator.toString(),
                if (toolCallsAccumulator.isEmpty()) null else toolCallsAccumulator,
                "stop"
            )
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>?): ObjectNode {
        val body = objectMapper.createObjectNode()
        body.put("model", model)
        body.put("temperature", 0.2)
        val messagesArray = objectMapper.createArrayNode()
        for (msg in messages) {
            val m = objectMapper.createObjectNode()
            m.put("role", msg.role)
            if (!msg.content.isNullOrBlank()) {
                m.put("content", msg.content)
            } else if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                m.put("content", "")
            }
            if (!msg.toolCalls.isNullOrEmpty()) {
                val toolCallsArray = objectMapper.createArrayNode()
                for (tc in msg.toolCalls!!) {
                    val tcNode = objectMapper.createObjectNode()
                    tcNode.put("id", tc.id)
                    tcNode.put("type", "function")
                    val fn = objectMapper.createObjectNode()
                    fn.put("name", tc.name)
                    fn.put("arguments", tc.arguments)
                    tcNode.put("function", fn)
                    toolCallsArray.add(tcNode)
                }
                m.put("tool_calls", toolCallsArray)
            }
            if (msg.toolCallId != null) {
                m.put("tool_call_id", msg.toolCallId)
            }
            messagesArray.add(m)
        }
        body.put("messages", messagesArray)
        if (!tools.isNullOrEmpty()) {
            val toolsArray = objectMapper.createArrayNode()
            for (t in tools) {
                val toolNode = objectMapper.createObjectNode()
                toolNode.put("type", "function")
                val fn = objectMapper.createObjectNode()
                fn.put("name", t.name)
                fn.put("description", t.description)
                if (!t.parameters.isNullOrEmpty()) {
                    fn.put("parameters", objectMapper.valueToTree(t.parameters))
                } else {
                    fn.putObject("parameters")
                }
                toolNode.put("function", fn)
                toolsArray.add(toolNode)
            }
            body.put("tools", toolsArray)
        }
        return body
    }

    private fun parseResponse(json: String?): LlmResponse {
        try {
            val root = objectMapper.readTree(json)
            val choices = root.get("choices")
            if (choices == null || !choices.isArray || choices.isEmpty) {
                return LlmResponse("", null, "stop")
            }
            val choice = choices[0]
            val finishReason = if (choice.has("finish_reason")) {
                choice.get("finish_reason").asText()
            } else {
                "stop"
            }
            val message = choice.get("message") ?: return LlmResponse("", null, finishReason)
            val content = if (message.has("content") && message.get("content") != null) {
                message.get("content").asText("")
            } else {
                ""
            }
            var toolCalls: MutableList<ToolCallRequest>? = null
            if (message.has("tool_calls") && message.get("tool_calls").isArray) {
                toolCalls = mutableListOf()
                for (tc in message.get("tool_calls")) {
                    val id = if (tc.has("id")) tc.get("id").asText() else ""
                    val fn = if (tc.has("function")) tc.get("function") else null
                    val name = if (fn != null && fn.has("name")) fn.get("name").asText() else ""
                    val args = if (fn != null && fn.has("arguments")) fn.get("arguments").asText() else "{}"
                    toolCalls.add(ToolCallRequest(id, name, args))
                }
            }
            return LlmResponse(content, toolCalls, finishReason)
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse LLM response", e)
        }
    }

    companion object {
        private const val STREAM_TIMEOUT_SECONDS = 120
        /** Max retries when OpenAI returns 429 (rate limit). */
        private const val MAX_RETRIES_429 = 5
        /** Default delay in ms when 429 body does not specify retry-after. */
        private const val DEFAULT_RETRY_DELAY_MS = 2000L
        /** Cap retry delay to avoid excessive wait. */
        private const val MAX_RETRY_DELAY_MS = 60_000L
        /** OpenAI error message pattern: "Please try again in 744ms". */
        private val RETRY_AFTER_MS = Pattern.compile("try again in (\\d+)ms", Pattern.CASE_INSENSITIVE)

        private fun parseRetryAfterMs(body: String?): Long {
            if (body.isNullOrBlank()) {
                return DEFAULT_RETRY_DELAY_MS
            }
            val m = RETRY_AFTER_MS.matcher(body)
            if (m.find()) {
                val ms = m.group(1).toLong()
                return minOf(maxOf(ms, 100L), MAX_RETRY_DELAY_MS)
            }
            return DEFAULT_RETRY_DELAY_MS
        }
    }
}
