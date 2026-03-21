package com.horain.config

import org.springframework.ai.retry.NonTransientAiException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

/**
 * Global exception handler for API errors.
 * Returns explicit error messages so the frontend can display the actual cause
 * instead of generic "Internal Server Error".
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NonTransientAiException::class)
    fun handleLlmProviderError(ex: NonTransientAiException): ResponseEntity<Map<String, String>> {
        val msg = ex.message ?: "Unknown LLM error"
        val hint = when {
            msg.contains("404") ->
                " Check LLM_BASE_URL (e.g. https://api.openai.com/v1 or https://openrouter.ai/api/v1) and LLM_MODEL."
            msg.contains("401") || msg.contains("403") ->
                " Check LLM_API_KEY is valid and has access to the model."
            msg.contains("429") ->
                " Rate limit exceeded. Try again later."
            else -> ""
        }
        val detail = msg + hint
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("error" to "LLM provider error", "message" to detail))
    }

    /**
     * When a path variable cannot be converted to the required type (e.g. "entry-uuid-123"
     * for time-logs PATCH/DELETE which expect a UUID), return 422 with a clear message.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, String>> {
        if (ex.requiredType == UUID::class.java) {
            val value = ex.value?.toString() ?: "null"
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(
                    mapOf(
                        "error" to "Invalid id",
                        "message" to "Time log id must be a valid UUID, got: $value"
                    )
                )
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "error" to "Invalid request",
                    "message" to (ex.message ?: "Type mismatch")
                )
            )
    }

    /**
     * Map domain "not found" cases to 404 instead of default 500. Other [IllegalArgumentException]
     * from the API layer are treated as client errors (400).
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        val msg = ex.message ?: "Bad request"
        var status = HttpStatus.BAD_REQUEST
        if (msg.startsWith("Time log not found:") ||
            msg.startsWith("Project not found:") ||
            msg.startsWith("Turn not found:") ||
            msg.startsWith("No project found matching ")
        ) {
            status = HttpStatus.NOT_FOUND
        }
        val error = if (status == HttpStatus.NOT_FOUND) "Not found" else "Bad request"
        return ResponseEntity.status(status).body(mapOf("error" to error, "message" to msg))
    }
}
