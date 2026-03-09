package com.horain.controller;

import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.dto.TimeLogEntryDto;
import com.horain.service.ProjectService;
import com.horain.service.TimeLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Time log API controller.
 */
@RestController
@RequestMapping("/time-logs")
public class TimeLogController {

    private final TimeLogService timeLogService;
    private final ProjectService projectService;

    public TimeLogController(TimeLogService timeLogService, ProjectService projectService) {
        this.timeLogService = timeLogService;
        this.projectService = projectService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<TimeLogEntryDto>> getRecent(
            @RequestParam(defaultValue = "5") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<TimeLogDto> logs = timeLogService.findRecentLogs(safeLimit);
        Map<String, String> projectNames = projectService.findAll().stream()
                .collect(Collectors.toMap(p -> p.getId().toString(), ProjectDto::getName));
        List<TimeLogEntryDto> entries = logs.stream()
                .map(log -> new TimeLogEntryDto(
                        log.getId().toString(),
                        log.getProjectId().toString(),
                        projectNames.getOrDefault(log.getProjectId().toString(), "?"),
                        log.getDurationMinutes() != null ? log.getDurationMinutes() : 0,
                        log.getNote(),
                        log.getLoggedAt() != null ? log.getLoggedAt().toString() : null))
                .toList();
        return ResponseEntity.ok(entries);
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
