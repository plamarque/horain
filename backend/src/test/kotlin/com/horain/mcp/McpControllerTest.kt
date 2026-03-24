package com.horain.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpControllerTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `tools list returns tool metadata`() {
        val controller = McpController(objectMapper, FakeGateway())
        val body = objectMapper.readTree(
            """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
        )

        val response = controller.handle(body)
        assertEquals(200, response.statusCode.value())
        val tools = response.body?.get("result")
            ?.let { objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(it) }
            ?.path("tools")
        assertTrue(tools != null && tools.isArray)
        assertTrue(tools!!.any { it.path("name").asText() == "list_projects" })
    }

    @Test
    fun `tools call delegates to gateway`() {
        val controller = McpController(objectMapper, FakeGateway())
        val body = objectMapper.readTree(
            """{"jsonrpc":"2.0","id":"abc","method":"tools/call","params":{"name":"list_projects","arguments":{}}}"""
        )

        val response = controller.handle(body)
        assertEquals(200, response.statusCode.value())
        val resultNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(response.body?.get("result"))
        assertTrue(resultNode.has("data"))
        assertTrue(resultNode.path("data").has("projects"))
    }

    private class FakeGateway : McpToolGateway {
        override fun listTools(): List<Map<String, Any?>> =
            listOf(
                mapOf(
                    "name" to "list_projects",
                    "description" to "List projects",
                    "inputSchema" to mapOf("type" to "object", "properties" to emptyMap<String, Any>())
                )
            )

        override fun executeTool(name: String, argumentsJson: String): String {
            return """{"llm":"ok","data":{"projects":[]}}"""
        }
    }
}
