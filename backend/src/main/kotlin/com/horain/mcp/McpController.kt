package com.horain.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.llm.ToolCallRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mcp")
class McpController(
    private val objectMapper: ObjectMapper,
    private val gateway: McpToolGateway
) {

    @PostMapping
    fun handle(@RequestBody payload: JsonNode?): ResponseEntity<Map<String, Any?>> {
        if (payload == null || !payload.isObject) {
            return ResponseEntity.ok(
                errorResponse(null, -32600, "Invalid Request")
            )
        }

        val jsonrpc = payload.get("jsonrpc")?.asText()
        if (jsonrpc != "2.0") {
            return ResponseEntity.ok(
                errorResponse(payload.get("id"), -32600, "Invalid Request: jsonrpc must be '2.0'")
            )
        }

        val method = payload.get("method")?.asText()
        return when (method) {
            "tools/list" -> ResponseEntity.ok(
                successResponse(
                    payload.get("id"),
                    mapOf("tools" to gateway.listTools())
                )
            )

            "tools/call" -> ResponseEntity.ok(handleToolsCall(payload))
            else -> ResponseEntity.ok(
                errorResponse(payload.get("id"), -32601, "Method not found: $method")
            )
        }
    }

    private fun handleToolsCall(payload: JsonNode): Map<String, Any?> {
        val params = payload.get("params")
        if (params == null || !params.isObject) {
            return errorResponse(payload.get("id"), -32602, "Invalid params: object expected")
        }
        val name = params.get("name")?.asText()
        if (name.isNullOrBlank()) {
            return errorResponse(payload.get("id"), -32602, "Invalid params: name is required")
        }

        val argsNode = params.get("arguments")
        val argsJson = if (argsNode == null || argsNode.isNull) "{}" else argsNode.toString()
        val rawResult = gateway.executeTool(name, argsJson)
        val parsedContent: Any? = parseJsonSafely(rawResult)
        return successResponse(payload.get("id"), parsedContent ?: mapOf("content" to (rawResult ?: "")))
    }

    private fun parseJsonSafely(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return try {
            objectMapper.readTree(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun successResponse(idNode: JsonNode?, result: Any?): Map<String, Any?> {
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to toIdValue(idNode),
            "result" to result
        )
    }

    private fun errorResponse(idNode: JsonNode?, code: Int, message: String): Map<String, Any?> {
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to toIdValue(idNode),
            "error" to mapOf(
                "code" to code,
                "message" to message
            )
        )
    }

    private fun toIdValue(node: JsonNode?): Any? {
        if (node == null || node.isNull) return null
        return when {
            node.isIntegralNumber -> node.asLong()
            node.isFloatingPointNumber -> node.asDouble()
            node.isTextual -> node.asText()
            else -> node.toString()
        }
    }
}
