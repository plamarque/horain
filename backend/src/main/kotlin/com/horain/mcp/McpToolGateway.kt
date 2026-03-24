package com.horain.mcp

import com.horain.llm.ToolCallRequest
import com.horain.tools.ToolExecutorService
import com.horain.tools.ToolRegistry
import org.springframework.stereotype.Component
import java.util.UUID

interface McpToolGateway {
    fun listTools(): List<Map<String, Any?>>
    fun executeTool(name: String, argumentsJson: String): String?
}

@Component
class DefaultMcpToolGateway(
    private val toolRegistry: ToolRegistry,
    private val toolExecutorService: ToolExecutorService
) : McpToolGateway {

    override fun listTools(): List<Map<String, Any?>> {
        return toolRegistry.getAllTools().map { d ->
            mapOf(
                "name" to d.name,
                "description" to d.description,
                "inputSchema" to d.parameters
            )
        }
    }

    override fun executeTool(name: String, argumentsJson: String): String? {
        val request = ToolCallRequest(UUID.randomUUID().toString(), name, argumentsJson)
        return toolExecutorService.execute(request).content
    }
}
