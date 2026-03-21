package com.horain.controller

import com.horain.dto.TimeLogDto
import com.horain.dto.TimeLogEntryDto
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Time log API controller.
 */
@RestController
@RequestMapping("/time-logs")
class TimeLogController(
    private val timeLogService: TimeLogService,
    private val projectService: ProjectService
) {

    @GetMapping("/recent")
    fun getRecent(@RequestParam(defaultValue = "5") limit: Int): ResponseEntity<List<TimeLogEntryDto>> {
        val safeLimit = min(max(limit, 1), 50)
        val logs = timeLogService.findRecentLogs(safeLimit)
        val projectNames = projectService.findAll().associate { it.id.toString() to it.name }
        val entries = logs.map { log ->
            TimeLogEntryDto(
                log.id.toString(),
                log.projectId.toString(),
                projectNames[log.projectId.toString()] ?: "?",
                log.durationMinutes ?: 0,
                log.note,
                log.billable == true,
                log.loggedAt?.toString() ?: "",
                log.createdAt?.toString() ?: "",
                log.activityTypeCode,
                log.activityTypeLabel,
                log.dailyRateCents
            )
        }
        return ResponseEntity.ok(entries)
    }

    @PostMapping
    fun create(@RequestBody dto: TimeLogDto): ResponseEntity<TimeLogDto> {
        val created = timeLogService.create(dto)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping
    fun list(): ResponseEntity<List<TimeLogDto>> =
        ResponseEntity.ok(timeLogService.findAll())

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody patch: Map<String, Any?>): ResponseEntity<TimeLogDto> {
        val dto = TimeLogDto.builder().id(id).build()
        if (patch.containsKey("projectId")) {
            val v = patch["projectId"]
            dto.projectId = when (v) {
                is String -> UUID.fromString(v)
                else -> UUID.fromString(v.toString())
            }
        }
        if (patch.containsKey("durationMinutes")) {
            val v = patch["durationMinutes"]
            dto.durationMinutes = when (v) {
                is Number -> v.toInt()
                else -> v.toString().toInt()
            }
        }
        if (patch.containsKey("note")) {
            dto.note = patch["note"]?.toString()
        }
        if (patch.containsKey("loggedAt")) {
            dto.loggedAt = Instant.parse(patch["loggedAt"].toString())
        }
        if (patch.containsKey("billable")) {
            val v = patch["billable"]
            dto.billable = when (v) {
                is Boolean -> v
                else -> v.toString().toBoolean()
            }
        }
        if (patch.containsKey("activityTypeCode")) {
            val v = patch["activityTypeCode"]
            val code = if (v == null || (v is String && v.isBlank())) "" else v.toString().trim()
            dto.activityTypeCode = code
        }
        val updated = timeLogService.update(id, dto)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        timeLogService.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
