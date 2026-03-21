package com.horain.service

import com.horain.dto.TimeLogDto
import com.horain.model.TimeLog
import com.horain.repository.ActivityTypeRepository
import com.horain.repository.ProjectRepository
import com.horain.repository.TimeLogRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Service for time log operations.
 */
@Service
class TimeLogService(
    private val timeLogRepository: TimeLogRepository,
    private val projectRepository: ProjectRepository,
    private val activityTypeRepository: ActivityTypeRepository
) {

    @Transactional
    fun create(dto: TimeLogDto): TimeLogDto {
        val project = projectRepository.findById(dto.projectId!!)
            .orElseThrow { IllegalArgumentException("Project not found: ${dto.projectId}") }
        val entity = TimeLog()
        entity.projectId = dto.projectId
        entity.durationMinutes = dto.durationMinutes
        entity.note = dto.note
        val billable = dto.billable ?: (project.billable ?: true)
        entity.billable = billable
        entity.loggedAt = dto.loggedAt ?: Instant.now()
        entity.userId = dto.userId
        entity.updatedAt = entity.loggedAt
        entity.id = dto.id ?: UUID.randomUUID()
        if (!dto.activityTypeCode.isNullOrBlank()) {
            val code = dto.activityTypeCode!!.trim().uppercase()
            if (!activityTypeRepository.existsById(code)) {
                throw IllegalArgumentException("Activity type not found: ${dto.activityTypeCode}")
            }
            entity.activityTypeCode = code
        }
        val saved = timeLogRepository.save(entity)
        return toDto(saved)
    }

    /** Idempotent create: skip if entity with same ID already exists (or duplicate key on concurrent seed). */
    @Transactional
    fun createOrSkip(entityId: String?, dto: TimeLogDto) {
        if (entityId.isNullOrBlank()) {
            create(dto)
            return
        }
        val id = UUID.fromString(entityId)
        if (timeLogRepository.existsById(id)) return
        dto.id = id
        try {
            create(dto)
        } catch (_: DataIntegrityViolationException) {
            // Already exists (e.g. concurrent seed); treat as skip
        }
    }

    @Transactional(readOnly = true)
    fun findAll(): List<TimeLogDto> =
        timeLogRepository.findAll().map { toDto(it) }

    @Transactional(readOnly = true)
    fun findRecentLogs(limit: Int): List<TimeLogDto> {
        val safeLimit = minOf(maxOf(limit, 1), 50)
        val logs = timeLogRepository.findTop50ByOrderByLoggedAtDesc()
        return logs.take(safeLimit).map { toDto(it) }
    }

    @Transactional(readOnly = true)
    fun findLogsForPeriod(start: Instant, end: Instant, projectId: UUID?): List<TimeLogDto> {
        val logs = if (projectId != null) {
            timeLogRepository.findByProjectIdAndLoggedAtBetweenOrderByLoggedAtDesc(projectId, start, end)
        } else {
            timeLogRepository.findByLoggedAtBetweenOrderByLoggedAtDesc(start, end)
        }
        return logs.map { toDto(it) }
    }

    /**
     * Search time logs by keyword (note or project name, case-insensitive contains).
     * Returns at most [limit] results (1–50), most recent first.
     */
    @Transactional(readOnly = true)
    fun findLogsByKeyword(query: String?, limit: Int): List<TimeLogDto> {
        if (query.isNullOrBlank()) {
            return emptyList()
        }
        val safeLimit = minOf(maxOf(limit, 1), 50)
        val logs = timeLogRepository.searchByKeyword(query.trim(), PageRequest.of(0, safeLimit))
        return logs.map { toDto(it) }
    }

    @Transactional(readOnly = true)
    fun sumDurationForPeriod(start: Instant, end: Instant): Int {
        val sum = timeLogRepository.sumDurationMinutesByLoggedAtBetween(start, end)
        return sum ?: 0
    }

    @Transactional(readOnly = true)
    fun sumDurationByProject(projectId: UUID, start: Instant, end: Instant): Int {
        val sum = timeLogRepository.sumDurationMinutesByProjectAndLoggedAtBetween(projectId, start, end)
        return sum ?: 0
    }

    @Transactional(readOnly = true)
    fun sumDurationForPeriodByBillable(start: Instant, end: Instant, billable: Boolean): Int {
        val sum = timeLogRepository.sumDurationMinutesByLoggedAtBetweenAndBillable(start, end, billable)
        return sum ?: 0
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Optional<TimeLogDto> =
        timeLogRepository.findById(id).map { toDto(it) }

    @Transactional
    fun update(id: UUID, patch: TimeLogDto): TimeLogDto {
        val entity = timeLogRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Time log not found: $id") }
        if (patch.projectId != null) {
            if (!projectRepository.existsById(patch.projectId!!)) {
                throw IllegalArgumentException("Project not found: ${patch.projectId}")
            }
            entity.projectId = patch.projectId
        }
        if (patch.durationMinutes != null && patch.durationMinutes!! > 0) {
            entity.durationMinutes = patch.durationMinutes
        }
        if (patch.note != null) {
            entity.note = patch.note
        }
        if (patch.loggedAt != null) {
            entity.loggedAt = patch.loggedAt
        }
        if (patch.billable != null) {
            entity.billable = patch.billable
        }
        if (patch.activityTypeCode != null) {
            if (patch.activityTypeCode!!.isBlank()) {
                entity.activityTypeCode = null
            } else {
                val code = patch.activityTypeCode!!.trim().uppercase()
                if (!activityTypeRepository.existsById(code)) {
                    throw IllegalArgumentException("Activity type not found: ${patch.activityTypeCode}")
                }
                entity.activityTypeCode = code
            }
        }
        return toDto(timeLogRepository.save(entity))
    }

    @Transactional
    fun deleteById(id: UUID) {
        if (!timeLogRepository.existsById(id)) {
            throw IllegalArgumentException("Time log not found: $id")
        }
        timeLogRepository.deleteById(id)
    }

    private fun toDto(t: TimeLog): TimeLogDto {
        val dto = TimeLogDto.builder()
            .id(t.id)
            .projectId(t.projectId)
            .durationMinutes(t.durationMinutes)
            .note(t.note)
            .billable(t.billable ?: true)
            .loggedAt(t.loggedAt)
            .createdAt(t.createdAt)
            .updatedAt(t.updatedAt)
            .userId(t.userId)
            .build()
        if (!t.activityTypeCode.isNullOrBlank()) {
            activityTypeRepository.findById(t.activityTypeCode!!).ifPresent { a ->
                dto.activityTypeCode = a.code
                dto.activityTypeLabel = a.label
                dto.dailyRateCents = a.dailyRateCents
            }
        }
        return dto
    }
}
