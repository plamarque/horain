package com.horain.controller;

import com.horain.dto.TimeLogDto;
import com.horain.service.TimeLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
