package com.horain.controller;

import com.horain.dto.ActivityTypeDto;
import com.horain.service.ActivityTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Activity types (natures + TJM) API. Used by frontend and by MCP tools.
 */
@RestController
@RequestMapping("/activity-types")
public class ActivityTypeController {

    private final ActivityTypeService activityTypeService;

    public ActivityTypeController(ActivityTypeService activityTypeService) {
        this.activityTypeService = activityTypeService;
    }

    @GetMapping
    public ResponseEntity<List<ActivityTypeDto>> list() {
        return ResponseEntity.ok(activityTypeService.findAll());
    }

    @PostMapping
    public ResponseEntity<ActivityTypeDto> create(@RequestBody ActivityTypeDto dto) {
        ActivityTypeDto created = activityTypeService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{code}")
    public ResponseEntity<ActivityTypeDto> update(
            @PathVariable String code,
            @RequestBody Map<String, Object> patch) {
        ActivityTypeDto dto = new ActivityTypeDto();
        if (patch.containsKey("label")) {
            dto.setLabel(patch.get("label") != null ? patch.get("label").toString() : null);
        }
        if (patch.containsKey("dailyRateCents")) {
            Object v = patch.get("dailyRateCents");
            dto.setDailyRateCents(v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString()));
        }
        ActivityTypeDto updated = activityTypeService.update(code, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        activityTypeService.deleteByCode(code);
        return ResponseEntity.noContent().build();
    }
}
