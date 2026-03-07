package com.horain.controller;

import com.horain.dto.TimeLogDto;
import com.horain.service.TimeLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time log API controller.
 */
@RestController
@RequestMapping("/time-logs")
public class TimeLogController {

    private final TimeLogService timeLogService;

    public TimeLogController(TimeLogService timeLogService) {
        this.timeLogService = timeLogService;
    }

    @PostMapping
    public ResponseEntity<TimeLogDto> create(@RequestBody TimeLogDto dto) {
        TimeLogDto created = timeLogService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TimeLogDto>> list() {
        return ResponseEntity.ok(timeLogService.findAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TimeLogDto> update(@PathVariable UUID id, @RequestBody Map<String, Object> patch) {
        TimeLogDto dto = TimeLogDto.builder().id(id).build();
        if (patch.containsKey("projectId")) {
            Object v = patch.get("projectId");
            dto.setProjectId(v instanceof String ? UUID.fromString((String) v) : UUID.fromString(v.toString()));
        }
        if (patch.containsKey("durationMinutes")) {
            Object v = patch.get("durationMinutes");
            dto.setDurationMinutes(v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString()));
        }
        if (patch.containsKey("note")) {
            dto.setNote(patch.get("note") != null ? patch.get("note").toString() : null);
        }
        if (patch.containsKey("loggedAt")) {
            dto.setLoggedAt(java.time.Instant.parse(patch.get("loggedAt").toString()));
        }
        TimeLogDto updated = timeLogService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        timeLogService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
