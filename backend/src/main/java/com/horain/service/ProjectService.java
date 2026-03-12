package com.horain.service;

import com.horain.dto.ProjectDto;
import com.horain.model.Project;
import com.horain.repository.ProjectRepository;
import com.horain.repository.TimeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
        return projectRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
        return ProjectDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .billable(p.getBillable() != null ? p.getBillable() : true)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .userId(p.getUserId())
                .build();
    }
}
