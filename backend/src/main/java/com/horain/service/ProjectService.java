package com.horain.service;

import com.horain.dto.ProjectActivityTypeSummaryDto;
import com.horain.dto.ProjectDto;
import com.horain.model.Project;
import com.horain.repository.ProjectRepository;
import com.horain.repository.TimeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for project operations.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TimeLogRepository timeLogRepository;

    public ProjectService(ProjectRepository projectRepository, TimeLogRepository timeLogRepository) {
        this.projectRepository = projectRepository;
        this.timeLogRepository = timeLogRepository;
    }

    @Transactional
    public ProjectDto create(ProjectDto dto) {
        Project entity = new Project();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setBillable(dto.getBillable() != null ? dto.getBillable() : true);
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
    public Optional<ProjectDto> findById(UUID id) {
        return projectRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> findAll() {
        List<Project> all = projectRepository.findAll();
        Map<UUID, Long> revenueByProject = sumRevenueCentsByProjectMap();
        Map<UUID, Long> countByProject = countByProjectIdMap();
        Map<UUID, List<ProjectActivityTypeSummaryDto>> topActivityTypesByProject = topActivityTypesByProjectMap();
        return all.stream()
                .map(p -> toDto(p, revenueByProject.get(p.getId()), countByProject.get(p.getId()),
                        topActivityTypesByProject.get(p.getId())))
                .collect(Collectors.toList());
    }

    /** Builds projectId -> number of time log entries. Projects with zero logs are not in the result (default 0). */
    private Map<UUID, Long> countByProjectIdMap() {
        List<Object[]> rows = timeLogRepository.countByProjectId();
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> toUuid(row[0]),
                        row -> ((Number) row[1]).longValue()));
    }

    private static final int TOP_ACTIVITY_TYPES_MAX = 5;

    /** Builds projectId -> list of top activity types (code, label, count) sorted by count desc, max 5. */
    private Map<UUID, List<ProjectActivityTypeSummaryDto>> topActivityTypesByProjectMap() {
        List<Object[]> rows = timeLogRepository.countByProjectIdAndActivityType();
        Map<UUID, List<ProjectActivityTypeSummaryDto>> byProject = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID projectId = toUuid(row[0]);
            String code = row[1] != null ? row[1].toString() : "";
            String label = row[2] != null ? row[2].toString() : "";
            long count = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            byProject.computeIfAbsent(projectId, k -> new ArrayList<>())
                    .add(new ProjectActivityTypeSummaryDto(code, label, count));
        }
        byProject.replaceAll((k, list) -> list.stream()
                .sorted(Comparator.comparingLong(ProjectActivityTypeSummaryDto::count).reversed())
                .limit(TOP_ACTIVITY_TYPES_MAX)
                .collect(Collectors.toList()));
        return byProject;
    }

    /** Builds projectId -> total revenue (cents) for billable entries with activity type. */
    private Map<UUID, Long> sumRevenueCentsByProjectMap() {
        List<Object[]> rows = timeLogRepository.sumRevenueCentsByProject();
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> toUuid(row[0]),
                        row -> Math.round(((Number) row[1]).doubleValue())));
    }

    private static UUID toUuid(Object value) {
        if (value == null) throw new IllegalArgumentException("projectId is null");
        if (value instanceof UUID) return (UUID) value;
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            return new UUID(bb.getLong(), bb.getLong());
        }
        return UUID.fromString(value.toString());
    }

    /**
     * Updates an existing project. Only non-null fields in the patch are applied.
     */
    @Transactional
    public ProjectDto update(UUID id, ProjectDto patch) {
        Project entity = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        if (patch.getName() != null && !patch.getName().isBlank()) {
            entity.setName(patch.getName().trim());
        }
        if (patch.getDescription() != null) {
            entity.setDescription(patch.getDescription());
        }
        if (patch.getBillable() != null) {
            entity.setBillable(patch.getBillable());
        }
        return toDto(projectRepository.save(entity));
    }

    /**
     * Deletes a project. Fails if the project has any time log entries (RESTRICT).
     */
    @Transactional
    public void deleteById(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new IllegalArgumentException("Project not found: " + id);
        }
        long count = timeLogRepository.countByProjectId(id);
        if (count > 0) {
            throw new IllegalStateException(
                    "Cannot delete project: it has " + count + " time log entries. Delete or reassign them first.");
        }
        projectRepository.deleteById(id);
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

    /**
     * When no exact/contains match exists, returns projects with similar names (typo-tolerant).
     * Uses normalized Levenshtein similarity; only returns projects above the similarity threshold.
     *
     * @param name       query (e.g. "Horian")
     * @param maxResults max number of close matches to return (e.g. 3)
     * @return list of projects sorted by similarity descending, best match first
     */
    @Transactional(readOnly = true)
    public List<ProjectDto> findCloseMatchesByName(String name, int maxResults) {
        if (name == null || name.isBlank() || maxResults <= 0) {
            return List.of();
        }
        String query = name.trim().toLowerCase();
        List<Project> all = projectRepository.findAll();
        double threshold = 0.5;
        return all.stream()
                .map(p -> new Object[]{p, similarity(query, p.getName().toLowerCase())})
                .filter(pair -> (Double) pair[1] >= threshold)
                .sorted(Comparator.<Object[], Double>comparing(pair -> (Double) pair[1]).reversed())
                .limit(maxResults)
                .map(pair -> toDto((Project) pair[0]))
                .toList();
    }

    /**
     * Normalized similarity between two strings (0 = unrelated, 1 = identical).
     * Based on Levenshtein distance: 1 - (distance / maxLength).
     */
    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int maxLen = Math.max(a.length(), b.length());
        int distance = levenshteinDistance(a, b);
        return 1.0 - (double) distance / maxLen;
    }

    private static int levenshteinDistance(CharSequence a, CharSequence b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[m];
    }

    private ProjectDto toDto(Project p) {
        return toDto(p, null, null, null);
    }

    private ProjectDto toDto(Project p, Long revenueCents, Long timeLogCount,
                             List<ProjectActivityTypeSummaryDto> topActivityTypes) {
        return ProjectDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .billable(p.getBillable() != null ? p.getBillable() : true)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .userId(p.getUserId())
                .revenueCents(revenueCents)
                .timeLogCount(timeLogCount != null ? timeLogCount : 0L)
                .topActivityTypes(topActivityTypes != null ? topActivityTypes : List.of())
                .build();
    }
}
