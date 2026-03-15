package com.horain.service;

import com.horain.dto.TimeLogDto;
import com.horain.model.ActivityType;
import com.horain.model.TimeLog;
import com.horain.repository.ActivityTypeRepository;
import com.horain.repository.ProjectRepository;
import com.horain.repository.TimeLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for time log operations.
 */
@Service
public class TimeLogService {

    private final TimeLogRepository timeLogRepository;
    private final ProjectRepository projectRepository;
    private final ActivityTypeRepository activityTypeRepository;

    public TimeLogService(TimeLogRepository timeLogRepository, ProjectRepository projectRepository,
                          ActivityTypeRepository activityTypeRepository) {
        this.timeLogRepository = timeLogRepository;
        this.projectRepository = projectRepository;
        this.activityTypeRepository = activityTypeRepository;
    }

    @Transactional
    public TimeLogDto create(TimeLogDto dto) {
        var project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + dto.getProjectId()));
        TimeLog entity = new TimeLog();
        entity.setProjectId(dto.getProjectId());
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setNote(dto.getNote());
        boolean billable = dto.getBillable() != null ? dto.getBillable() : (project.getBillable() != null ? project.getBillable() : true);
        entity.setBillable(billable);
        entity.setLoggedAt(dto.getLoggedAt() != null ? dto.getLoggedAt() : Instant.now());
        entity.setUserId(dto.getUserId());
        entity.setUpdatedAt(entity.getLoggedAt());
        entity.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID());
        if (dto.getActivityTypeCode() != null && !dto.getActivityTypeCode().isBlank()) {
            String code = dto.getActivityTypeCode().trim().toUpperCase();
            if (!activityTypeRepository.existsById(code)) {
                throw new IllegalArgumentException("Activity type not found: " + dto.getActivityTypeCode());
            }
            entity.setActivityTypeCode(code);
        }
        TimeLog saved = timeLogRepository.save(entity);
        return toDto(saved);
    }

    /** Idempotent create: skip if entity with same ID already exists (or duplicate key on concurrent seed). */
    @Transactional
    public void createOrSkip(String entityId, TimeLogDto dto) {
        if (entityId == null || entityId.isBlank()) {
            create(dto);
            return;
        }
        UUID id = UUID.fromString(entityId);
        if (timeLogRepository.existsById(id)) return;
        dto.setId(id);
        try {
            create(dto);
        } catch (DataIntegrityViolationException e) {
            // Already exists (e.g. concurrent seed); treat as skip
        }
    }

    @Transactional(readOnly = true)
    public List<TimeLogDto> findAll() {
        return timeLogRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TimeLogDto> findRecentLogs(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<TimeLog> logs = timeLogRepository.findTop50ByOrderByLoggedAtDesc();
        List<TimeLogDto> result = logs.stream()
                .limit(safeLimit)
                .map(this::toDto)
                .collect(Collectors.toList());
        return result;
    }

    @Transactional(readOnly = true)
    public List<TimeLogDto> findLogsForPeriod(Instant start, Instant end, UUID projectId) {
        List<TimeLog> logs = projectId != null
                ? timeLogRepository.findByProjectIdAndLoggedAtBetweenOrderByLoggedAtDesc(projectId, start, end)
                : timeLogRepository.findByLoggedAtBetweenOrderByLoggedAtDesc(start, end);
        return logs.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Search time logs by keyword (note or project name, case-insensitive contains).
     * Returns at most {@code limit} results (1–50), most recent first.
     */
    @Transactional(readOnly = true)
    public List<TimeLogDto> findLogsByKeyword(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<TimeLog> logs = timeLogRepository.searchByKeyword(query.trim(), PageRequest.of(0, safeLimit));
        return logs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public int sumDurationForPeriod(Instant start, Instant end) {
        Integer sum = timeLogRepository.sumDurationMinutesByLoggedAtBetween(start, end);
        return sum != null ? sum : 0;
    }

    @Transactional(readOnly = true)
    public int sumDurationByProject(UUID projectId, Instant start, Instant end) {
        Integer sum = timeLogRepository.sumDurationMinutesByProjectAndLoggedAtBetween(projectId, start, end);
        return sum != null ? sum : 0;
    }

    @Transactional(readOnly = true)
    public int sumDurationForPeriodByBillable(Instant start, Instant end, boolean billable) {
        Integer sum = timeLogRepository.sumDurationMinutesByLoggedAtBetweenAndBillable(start, end, billable);
        return sum != null ? sum : 0;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TimeLogDto> findById(UUID id) {
        return timeLogRepository.findById(id).map(this::toDto);
    }

    @Transactional
    public TimeLogDto update(UUID id, TimeLogDto patch) {
        TimeLog entity = timeLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Time log not found: " + id));
        if (patch.getProjectId() != null) {
            if (!projectRepository.existsById(patch.getProjectId())) {
                throw new IllegalArgumentException("Project not found: " + patch.getProjectId());
            }
            entity.setProjectId(patch.getProjectId());
        }
        if (patch.getDurationMinutes() != null && patch.getDurationMinutes() > 0) {
            entity.setDurationMinutes(patch.getDurationMinutes());
        }
        if (patch.getNote() != null) {
            entity.setNote(patch.getNote());
        }
        if (patch.getLoggedAt() != null) {
            entity.setLoggedAt(patch.getLoggedAt());
        }
        if (patch.getBillable() != null) {
            entity.setBillable(patch.getBillable());
        }
        if (patch.getActivityTypeCode() != null) {
            if (patch.getActivityTypeCode().isBlank()) {
                entity.setActivityTypeCode(null);
            } else {
                String code = patch.getActivityTypeCode().trim().toUpperCase();
                if (!activityTypeRepository.existsById(code)) {
                    throw new IllegalArgumentException("Activity type not found: " + patch.getActivityTypeCode());
                }
                entity.setActivityTypeCode(code);
            }
        }
        return toDto(timeLogRepository.save(entity));
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!timeLogRepository.existsById(id)) {
            throw new IllegalArgumentException("Time log not found: " + id);
        }
        timeLogRepository.deleteById(id);
    }

    private TimeLogDto toDto(TimeLog t) {
        TimeLogDto dto = TimeLogDto.builder()
                .id(t.getId())
                .projectId(t.getProjectId())
                .durationMinutes(t.getDurationMinutes())
                .note(t.getNote())
                .billable(t.getBillable() != null ? t.getBillable() : true)
                .loggedAt(t.getLoggedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .userId(t.getUserId())
                .build();
        if (t.getActivityTypeCode() != null && !t.getActivityTypeCode().isBlank()) {
            Optional<ActivityType> at = activityTypeRepository.findById(t.getActivityTypeCode());
            at.ifPresent(a -> {
                dto.setActivityTypeCode(a.getCode());
                dto.setActivityTypeLabel(a.getLabel());
                dto.setDailyRateCents(a.getDailyRateCents());
            });
        }
        return dto;
    }
}
