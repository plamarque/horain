package com.horain.controller

import com.horain.agent.ExportEvalCandidatesService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Admin endpoints. Protected by the same API key as the rest of the API.
 * GET /admin/export-eval-candidates returns eval candidates as JSONL (one JSON object per line).
 */
@RestController
@RequestMapping("/admin")
class AdminController(
    private val exportEvalCandidatesService: ExportEvalCandidatesService
) {

    @GetMapping(value = ["/export-eval-candidates"], produces = ["application/x-ndjson"])
    fun exportEvalCandidates(): ResponseEntity<StreamingResponseBody> {
        val rows = exportEvalCandidatesService.getCandidates()
        val mapper = exportEvalCandidatesService.objectMapper
        val body = StreamingResponseBody { out ->
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                for (row in rows) {
                    writer.write(mapper.writeValueAsString(row))
                    writer.write('\n'.code)
                }
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-ndjson; charset=utf-8"))
            .body(body)
    }
}
