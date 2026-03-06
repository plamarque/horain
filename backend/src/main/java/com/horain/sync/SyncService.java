package com.horain.sync;

import com.horain.dto.*;
import com.horain.service.ProjectService;
import com.horain.service.TimeLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Sync service. Handles push (batch operations) and pull (updates since timestamp).
 */
@Service
public class SyncService {

    private final ProjectService projectService;
    private final TimeLogService timeLogService;

    public SyncService(ProjectService projectService, TimeLogService timeLogService) {
        this.projectService = projectService;
        this.timeLogService = timeLogService;
    }

    @Transactional
    public SyncPushResponse push(List<SyncOperationDto> operations) {
        int count = 0;
        List<SyncOperationDto> ops = operations != null ? operations : Collections.emptyList();
        for (SyncOperationDto op : ops) {
            try {
                applyOperation(op);
                count++;
            } catch (Exception e) {
                // Log but continue processing
                // In production, consider returning partial success info
            }
        }
        return SyncPushResponse.builder()
                .success(true)
                .processedCount(count)
                .build();
    }

    private void applyOperation(SyncOperationDto op) {
        String entityType = op.getEntityType();
        String operation = op.getOperation();
        Map<String, Object> payload = op.getPayload() != null ? op.getPayload() : new HashMap<>();

        if ("project".equalsIgnoreCase(entityType) && "create".equalsIgnoreCase(operation)) {
            projectService.createOrSkip(op.getEntityId(), mapToProjectDto(op.getEntityId(), payload));
        } else if ("time_log".equalsIgnoreCase(entityType) && "create".equalsIgnoreCase(operation)) {
            timeLogService.createOrSkip(op.getEntityId(), mapToTimeLogDto(op.getEntityId(), payload));
        }
    }

    private ProjectDto mapToProjectDto(String entityId, Map<String, Object> payload) {
        return ProjectDto.builder()
                .id(entityId != null ? UUID.fromString(entityId) : null)
                .name((String) payload.get("name"))
                .description((String) payload.get("description"))
                .userId((String) payload.get("userId"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TimeLogDto mapToTimeLogDto(String entityId, Map<String, Object> payload) {
        Object projectId = payload.get("projectId");
        Object duration = payload.get("durationMinutes");
        Object loggedAt = payload.get("loggedAt");

        if (projectId == null) throw new IllegalArgumentException("projectId required");
        if (duration == null) throw new IllegalArgumentException("durationMinutes required");

        UUID projectUuid = projectId instanceof String
                ? UUID.fromString((String) projectId)
                : UUID.fromString(projectId.toString());
        int durationMinutes = duration instanceof Number
                ? ((Number) duration).intValue()
                : Integer.parseInt(duration.toString());
        Instant loggedAtInstant = loggedAt != null && !loggedAt.toString().isEmpty()
                ? Instant.parse(loggedAt.toString())
                : Instant.now();

        return TimeLogDto.builder()
                .id(entityId != null ? UUID.fromString(entityId) : null)
                .projectId(projectUuid)
                .durationMinutes(durationMinutes)
                .note((String) payload.get("note"))
                .loggedAt(loggedAtInstant)
                .userId((String) payload.get("userId"))
                .build();
    }

    public SyncPullResponse pull(Long sinceTimestamp) {
        Instant since = sinceTimestamp != null && sinceTimestamp > 0
                ? Instant.ofEpochMilli(sinceTimestamp)
                : Instant.EPOCH;

        List<ProjectDto> projects = projectService.findUpdatedAfter(since);
        List<TimeLogDto> timeLogs = timeLogService.findUpdatedAfter(since);

        return SyncPullResponse.builder()
                .projects(projects)
                .timeLogs(timeLogs)
                .build();
    }
}
