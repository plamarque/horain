package com.horain.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * OpenAI Responses API rejects JSON Schema when [required] accidentally contains Kotlin [Pair]
 * (serialized as {"first":...,"second":...}) instead of property name strings.
 */
class ToolRegistryParametersSchemaTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `required arrays contain only string entries`() {
        val registry = ToolRegistry()
        for (tool in registry.getAllTools()) {
            val root: JsonNode = objectMapper.valueToTree(tool.parameters)
            val required = root.get("required") ?: continue
            assertTrue(required.isArray, "${tool.name}: parameters.required must be a JSON array")
            for (element in required) {
                assertTrue(
                    element.isTextual,
                    "${tool.name}: each required[] entry must be a string (property name); got: $element"
                )
            }
        }
    }
}
