package com.horain.service;

import com.horain.dto.ProjectDto;
import com.horain.model.Project;
import com.horain.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for project operations.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectDto create(ProjectDto dto) {
        Project entity = new Project();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setUserId(dto.getUserId());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID());
        Project saved = projectRepository.save(entity);
        return toDto(saved);
    }

    /** Idempotent create: skip if entity with same ID already exists. */
    @Transactional
    public void createOrSkip(String entityId, ProjectDto dto) {
        if (entityId == null || entityId.isBlank()) {
            create(dto);
            return;
        }
        UUID id = UUID.fromString(entityId);
        if (projectRepository.existsById(id)) return;
        dto.setId(id);
        create(dto);
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> findAll() {
        return projectRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> findUpdatedAfter(Instant after) {
        return projectRepository.findByUpdatedAtAfter(after).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Fuzzy search by project name. Returns projects whose name contains the query (case-insensitive).
     */
    @Transactional(readOnly = true)
    public List<ProjectDto> searchByName(String name) {
        if (name == null || name.isBlank()) {
            return findAll();
        }
        return projectRepository.findByNameContainingIgnoreCase(name.trim()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ProjectDto toDto(Project p) {
        return ProjectDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .userId(p.getUserId())
                .build();
    }
}
