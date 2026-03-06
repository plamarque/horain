package com.horain.service;

import com.horain.dto.TimeLogDto;
import com.horain.model.TimeLog;
import com.horain.repository.ProjectRepository;
import com.horain.repository.TimeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for time log operations.
 */
@Service
public class TimeLogService {

    private final TimeLogRepository timeLogRepository;
    private final ProjectRepository projectRepository;

    public TimeLogService(TimeLogRepository timeLogRepository, ProjectRepository projectRepository) {
        this.timeLogRepository = timeLogRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TimeLogDto create(TimeLogDto dto) {
        if (!projectRepository.existsById(dto.getProjectId())) {
            throw new IllegalArgumentException("Project not found: " + dto.getProjectId());
        }
        TimeLog entity = new TimeLog();
        entity.setProjectId(dto.getProjectId());
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setNote(dto.getNote());
        entity.setLoggedAt(dto.getLoggedAt() != null ? dto.getLoggedAt() : Instant.now());
        entity.setUserId(dto.getUserId());
        entity.setUpdatedAt(entity.getLoggedAt());
        entity.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID());
        TimeLog saved = timeLogRepository.save(entity);
        return toDto(saved);
    }

    /** Idempotent create: skip if entity with same ID already exists. */
    @Transactional
    public void createOrSkip(String entityId, TimeLogDto dto) {
        if (entityId == null || entityId.isBlank()) {
            create(dto);
            return;
        }
        UUID id = UUID.fromString(entityId);
        if (timeLogRepository.existsById(id)) return;
        dto.setId(id);
        create(dto);
    }

    @Transactional(readOnly = true)
    public List<TimeLogDto> findAll() {
        return timeLogRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TimeLogDto> findUpdatedAfter(Instant after) {
        return timeLogRepository.findByUpdatedAtAfter(after).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private TimeLogDto toDto(TimeLog t) {
        return TimeLogDto.builder()
                .id(t.getId())
                .projectId(t.getProjectId())
                .durationMinutes(t.getDurationMinutes())
                .note(t.getNote())
                .loggedAt(t.getLoggedAt())
                .updatedAt(t.getUpdatedAt())
                .userId(t.getUserId())
                .build();
    }
}
