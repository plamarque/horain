package com.horain.controller;

import com.horain.dto.ProjectDto;
import com.horain.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Project API controller.
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectDto> create(@RequestBody ProjectDto dto) {
        ProjectDto created = projectService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto>> list() {
        return ResponseEntity.ok(projectService.findAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProjectDto> update(@PathVariable UUID id, @RequestBody Map<String, Object> patch) {
        ProjectDto dto = ProjectDto.builder().id(id).build();
        if (patch.containsKey("name")) {
            dto.setName(patch.get("name") != null ? patch.get("name").toString().trim() : null);
        }
        if (patch.containsKey("description")) {
            dto.setDescription(patch.get("description") != null ? patch.get("description").toString() : null);
        }
        if (patch.containsKey("billable")) {
            Object v = patch.get("billable");
            dto.setBillable(v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(v.toString()));
        }
        ProjectDto updated = projectService.update(id, dto);
        return ResponseEntity.ok(updated);
    }
}
