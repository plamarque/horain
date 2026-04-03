package com.horain.service

import com.horain.dto.ProjectActivityTypeSummaryDto
import com.horain.dto.ProjectDto
import com.horain.model.Project
import com.horain.repository.ProjectRepository
import com.horain.repository.TimeLogRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Service for project operations.
 */
@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val timeLogRepository: TimeLogRepository
) {

    @Transactional
    fun create(dto: ProjectDto): ProjectDto {
        val entity = Project()
        entity.name = dto.name
        entity.description = dto.description
        entity.billable = dto.billable ?: true
        entity.userId = dto.userId
        val now = Instant.now()
        entity.createdAt = now
        entity.updatedAt = now
        entity.id = dto.id ?: UUID.randomUUID()
        val saved = projectRepository.save(entity)
        return toDto(saved)
    }

    /** Idempotent create: skip if entity with same ID already exists (or duplicate key on concurrent seed). */
    @Transactional
    fun createOrSkip(entityId: String?, dto: ProjectDto) {
        if (entityId.isNullOrBlank()) {
            create(dto)
            return
        }
        val id = UUID.fromString(entityId)
        try {
            if (projectRepository.existsById(id)) return
            dto.id = id
            create(dto)
        } catch (_: DataIntegrityViolationException) {
            // Already exists (e.g. concurrent seed, or flush during existsById revealed a duplicate); treat as skip
        }
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): java.util.Optional<ProjectDto> =
        projectRepository.findById(id).map { toDto(it) }

    @Transactional(readOnly = true)
    fun findAll(): List<ProjectDto> {
        val all = projectRepository.findAll()
        val revenueByProject = sumRevenueCentsByProjectMap()
        val countByProject = countByProjectIdMap()
        val topActivityTypesByProject = topActivityTypesByProjectMap()
        val durationByProject = sumDurationMinutesByProjectMap()
        return all.map { p ->
            toDto(
                p,
                revenueByProject[p.id],
                countByProject[p.id],
                topActivityTypesByProject[p.id],
                durationByProject[p.id]
            )
        }
    }

    /**
     * Projects that have at least one time log in [start, end), sorted by name (case-insensitive).
     * Revenue, counts, and top activity types are scoped to the same interval.
     */
    @Transactional(readOnly = true)
    fun findAllWithActivityInPeriod(start: Instant, end: Instant): List<ProjectDto> {
        require(start.isBefore(end)) { "activityFrom must be before activityTo" }
        require(ChronoUnit.DAYS.between(start, end) <= MAX_ACTIVITY_PERIOD_DAYS) {
            "Activity period must not exceed $MAX_ACTIVITY_PERIOD_DAYS days"
        }
        val projectIds = timeLogRepository.findDistinctProjectIdsByLoggedAtBetween(start, end).toSet()
        if (projectIds.isEmpty()) {
            return emptyList()
        }
        val projects = projectRepository.findAllById(projectIds)
            .sortedBy { it.name?.lowercase(Locale.ROOT) ?: "" }
        val revenueByProject = sumRevenueCentsByProjectMapForPeriod(start, end)
        val countByProject = countByProjectIdMapForPeriod(start, end)
        val topActivityTypesByProject = topActivityTypesByProjectMapForPeriod(start, end)
        val durationByProject = sumDurationMinutesByProjectMapForPeriod(start, end)
        return projects.map { p ->
            toDto(
                p,
                revenueByProject[p.id],
                countByProject[p.id],
                topActivityTypesByProject[p.id],
                durationByProject[p.id]
            )
        }
    }

    /** Builds projectId -> number of time log entries. Projects with zero logs are not in the result (default 0). */
    private fun countByProjectIdMap(): Map<UUID, Long> {
        val rows = timeLogRepository.countByProjectId()
        return rows.associate { row ->
            toUuid(row[0]) to (row[1] as Number).toLong()
        }
    }

    /** Builds projectId -> list of top activity types (code, label, count) sorted by count desc, max 5. */
    private fun topActivityTypesByProjectMap(): Map<UUID, List<ProjectActivityTypeSummaryDto>> {
        val rows = timeLogRepository.countByProjectIdAndActivityType()
        val byProject = linkedMapOf<UUID, MutableList<ProjectActivityTypeSummaryDto>>()
        for (row in rows) {
            val projectId = toUuid(row[0])
            val code = row[1]?.toString() ?: ""
            val label = row[2]?.toString() ?: ""
            val count = if (row[3] != null) (row[3] as Number).toLong() else 0L
            byProject.getOrPut(projectId) { mutableListOf() }
                .add(ProjectActivityTypeSummaryDto(code, label, count))
        }
        return byProject.mapValues { (_, list) ->
            list.sortedByDescending { it.count }.take(TOP_ACTIVITY_TYPES_MAX)
        }
    }

    /** Builds projectId -> total revenue (cents) for billable entries with activity type. */
    private fun sumRevenueCentsByProjectMap(): Map<UUID, Long> {
        val rows = timeLogRepository.sumRevenueCentsByProject()
        return rows.associate { row ->
            toUuid(row[0]) to (row[1] as Number).toDouble().roundToLong()
        }
    }

    private fun countByProjectIdMapForPeriod(start: Instant, end: Instant): Map<UUID, Long> {
        val rows = timeLogRepository.countLogsByProjectForPeriod(start, end)
        return rows.associate { row ->
            toUuid(row[0]) to (row[1] as Number).toLong()
        }
    }

    private fun topActivityTypesByProjectMapForPeriod(
        start: Instant,
        end: Instant
    ): Map<UUID, List<ProjectActivityTypeSummaryDto>> {
        val rows = timeLogRepository.countByProjectIdAndActivityTypeForPeriod(start, end)
        val byProject = linkedMapOf<UUID, MutableList<ProjectActivityTypeSummaryDto>>()
        for (row in rows) {
            val projectId = toUuid(row[0])
            val code = row[1]?.toString() ?: ""
            val label = row[2]?.toString() ?: ""
            val count = if (row[3] != null) (row[3] as Number).toLong() else 0L
            byProject.getOrPut(projectId) { mutableListOf() }
                .add(ProjectActivityTypeSummaryDto(code, label, count))
        }
        return byProject.mapValues { (_, list) ->
            list.sortedByDescending { it.count }.take(TOP_ACTIVITY_TYPES_MAX)
        }
    }

    private fun sumRevenueCentsByProjectMapForPeriod(start: Instant, end: Instant): Map<UUID, Long> {
        val rows = timeLogRepository.sumRevenueCentsByProjectForPeriod(start, end)
        return rows.associate { row ->
            toUuid(row[0]) to (row[1] as Number).toDouble().roundToLong()
        }
    }

    private fun sumDurationMinutesByProjectMap(): Map<UUID, Long> {
        val rows = timeLogRepository.sumDurationMinutesByProject()
        return rows.associate { row ->
            toUuid(row[0]) to (row[1] as Number).toLong()
        }
    }

    private fun sumDurationMinutesByProjectMapForPeriod(start: Instant, end: Instant): Map<UUID, Long> {
        val rows = timeLogRepository.sumDurationMinutesByProjectForPeriod(start, end)
        return rows.associate { row ->
            toUuid(row[0]) to (row[1] as Number).toLong()
        }
    }

    private fun toUuid(value: Any?): UUID {
        if (value == null) throw IllegalArgumentException("projectId is null")
        if (value is UUID) return value
        if (value is ByteArray && value.size == 16) {
            val bb = ByteBuffer.wrap(value)
            return UUID(bb.long, bb.long)
        }
        return UUID.fromString(value.toString())
    }

    /**
     * Updates an existing project. Only non-null fields in the patch are applied.
     */
    @Transactional
    fun update(id: UUID, patch: ProjectDto): ProjectDto {
        val entity = projectRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Project not found: $id") }
        if (patch.name != null && patch.name!!.isNotBlank()) {
            entity.name = patch.name!!.trim()
        }
        if (patch.description != null) {
            entity.description = patch.description
        }
        if (patch.billable != null) {
            entity.billable = patch.billable
        }
        return toDto(projectRepository.save(entity))
    }

    /**
     * Deletes a project. Fails if the project has any time log entries (RESTRICT).
     */
    @Transactional
    fun deleteById(id: UUID) {
        if (!projectRepository.existsById(id)) {
            throw IllegalArgumentException("Project not found: $id")
        }
        val count = timeLogRepository.countByProjectId(id)
        if (count > 0) {
            throw IllegalStateException(
                "Cannot delete project: it has $count time log entries. Delete or reassign them first."
            )
        }
        projectRepository.deleteById(id)
    }

    /**
     * Fuzzy search by project name. Returns projects whose name contains the query (case-insensitive).
     */
    @Transactional(readOnly = true)
    fun searchByName(name: String?): List<ProjectDto> {
        if (name.isNullOrBlank()) {
            return findAll()
        }
        return projectRepository.findByNameContainingIgnoreCase(name.trim()).map { toDto(it) }
    }

    /**
     * When no exact/contains match exists, returns projects with similar names (typo-tolerant).
     * Uses normalized Levenshtein similarity; only returns projects above the similarity threshold.
     */
    @Transactional(readOnly = true)
    fun findCloseMatchesByName(name: String, maxResults: Int): List<ProjectDto> {
        if (name.isBlank() || maxResults <= 0) {
            return emptyList()
        }
        val query = name.trim().lowercase()
        val all = projectRepository.findAll()
        val threshold = 0.5
        return all.map { p -> p to similarity(query, p.name!!.lowercase()) }
            .filter { (_, sim) -> sim >= threshold }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { toDto(it.first) }
    }

    /**
     * Normalized similarity between two strings (0 = unrelated, 1 = identical).
     * Based on Levenshtein distance: 1 - (distance / maxLength).
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = maxOf(a.length, b.length)
        val distance = levenshteinDistance(a, b)
        return 1.0 - distance.toDouble() / maxLen
    }

    private fun levenshteinDistance(a: CharSequence, b: CharSequence): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        var prev = IntArray(m + 1) { j -> j }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[m]
    }

    private fun toDto(p: Project): ProjectDto = toDto(p, null, null, null, null)

    private fun toDto(
        p: Project,
        revenueCents: Long?,
        timeLogCount: Long?,
        topActivityTypes: List<ProjectActivityTypeSummaryDto>?,
        totalDurationMinutes: Long?
    ): ProjectDto =
        ProjectDto.builder()
            .id(p.id)
            .name(p.name)
            .description(p.description)
            .billable(p.billable ?: true)
            .createdAt(p.createdAt)
            .updatedAt(p.updatedAt)
            .userId(p.userId)
            .revenueCents(revenueCents)
            .timeLogCount(timeLogCount ?: 0L)
            .topActivityTypes(topActivityTypes ?: emptyList())
            .totalDurationMinutes(totalDurationMinutes ?: 0L)
            .build()

    companion object {
        private const val TOP_ACTIVITY_TYPES_MAX = 5
        const val MAX_ACTIVITY_PERIOD_DAYS = 366L
    }
}
