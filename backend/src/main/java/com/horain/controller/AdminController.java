package com.horain.controller;

import com.horain.agent.ExportEvalCandidatesService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints. Protected by the same API key as the rest of the API.
 * GET /admin/export-eval-candidates returns eval candidates as JSONL (one JSON object per line).
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ExportEvalCandidatesService exportEvalCandidatesService;

    public AdminController(ExportEvalCandidatesService exportEvalCandidatesService) {
        this.exportEvalCandidatesService = exportEvalCandidatesService;
    }

    @GetMapping(value = "/export-eval-candidates", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> exportEvalCandidates() {
        List<Map<String, Object>> rows = exportEvalCandidatesService.getCandidates();
        StreamingResponseBody body = out -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                var mapper = exportEvalCandidatesService.getObjectMapper();
                for (Map<String, Object> row : rows) {
                    writer.write(mapper.writeValueAsString(row));
                    writer.write('\n');
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson; charset=utf-8"))
                .body(body);
    }
}
