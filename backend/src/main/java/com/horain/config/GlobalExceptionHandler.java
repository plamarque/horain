package com.horain.config;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for API errors.
 * Returns explicit error messages so the frontend can display the actual cause
 * instead of generic "Internal Server Error".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<Map<String, String>> handleLlmProviderError(NonTransientAiException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Unknown LLM error";
        String hint = "";
        if (msg.contains("404")) {
            hint = " Check LLM_BASE_URL (e.g. https://api.openai.com/v1 or https://openrouter.ai/api/v1) and LLM_MODEL.";
        } else if (msg.contains("401") || msg.contains("403")) {
            hint = " Check LLM_API_KEY is valid and has access to the model.";
        } else if (msg.contains("429")) {
            hint = " Rate limit exceeded. Try again later.";
        }
        String detail = msg + hint;
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "LLM provider error", "message", detail));
    }

    /**
     * When a path variable cannot be converted to the required type (e.g. "entry-uuid-123"
     * for time-logs PATCH/DELETE which expect a UUID), return 422 with a clear message.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == UUID.class) {
            String value = ex.getValue() != null ? ex.getValue().toString() : "null";
            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error", "Invalid id",
                            "message", "Time log id must be a valid UUID, got: " + value));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request", "message", ex.getMessage() != null ? ex.getMessage() : "Type mismatch"));
    }
}
