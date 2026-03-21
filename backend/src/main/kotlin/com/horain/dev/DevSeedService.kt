package com.horain.dev

import com.horain.dto.ProjectDto
import com.horain.dto.TimeLogDto
import com.horain.repository.ActivityTypeRepository
import com.horain.repository.ProjectRepository
import com.horain.repository.TimeLogRepository
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import java.util.UUID

/**
 * Generates and loads fictional seed data for development.
 * Provides varied, credible projects and time logs over a long period for chart testing.
 */
@Service
class DevSeedService(
    private val projectService: ProjectService,
    private val timeLogService: TimeLogService,
    private val timeLogRepository: TimeLogRepository,
    private val projectRepository: ProjectRepository,
    private val activityTypeRepository: ActivityTypeRepository
) {

    /** Clears all time logs and projects, then loads seed data. Dev only. */
    @Transactional
    fun resetAndLoadSeed(fixedToday: LocalDate?): DevSeedResult {
        timeLogRepository.deleteAll()
        projectRepository.deleteAll()
        return loadSeed(fixedToday)
    }

    @Transactional
    fun loadSeed(): DevSeedResult = loadSeed(null)

    @Transactional
    fun loadSeed(fixedToday: LocalDate?): DevSeedResult {
        val projects = listOf(
            createProject(PROJECT_HORAIN, "Horain", "Personal time journal PWA", true),
            createProject(PROJECT_HATCAST_V1, "HatCast V1", "Podcast production app", true),
            createProject(PROJECT_HATCAST_V2, "HatCast V2", "Podcast production app", true),
            createProject(PROJECT_CHRONO, "Chrono EPS", "School timetable manager", true),
            createProject(PROJECT_FESTIBASK, "Festibask", "Event basket platform", false),
            createProject(PROJECT_MEEDS, "Meeds", "Community engagement", false),
            createProject(PROJECT_WEATHER, "Weather Station", "IoT weather dashboard", false)
        )
        for (p in projects) {
            projectService.createOrSkip(p.id.toString(), p)
        }
        val activityTypeCodes = activityTypeRepository.findAllByOrderByCodeAsc()
            .map { it.code }
        var logsCreated = 0
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)
        val rand = Random(42)
        var globalSeq = 0
        var d = start
        while (!d.isAfter(end)) {
            val dow = d.dayOfWeek
            val weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            val maxEntries = if (weekend) rand.nextInt(2) else 2 + rand.nextInt(4)
            repeat(maxEntries) {
                val projectId = pickProject(rand)
                val duration = DURATIONS[rand.nextInt(DURATIONS.size)]
                val note = NOTES[rand.nextInt(NOTES.size)]
                val hour = if (weekend) 10 + rand.nextInt(6) else 8 + rand.nextInt(10)
                val minute = rand.nextInt(4) * 15
                val projectBillable = PROJECT_BILLABLE[projectId] ?: true
                val entryBillableOverride = if (rand.nextInt(10) == 0) !projectBillable else null
                val billable = entryBillableOverride ?: projectBillable
                val loggedAt = d.atTime(hour, minute).atZone(ZONE)
                val instant = loggedAt.toInstant()
                val b = TimeLogDto.builder()
                    .projectId(projectId)
                    .durationMinutes(duration)
                    .note(note)
                    .billable(entryBillableOverride)
                    .loggedAt(instant)
                if (billable && activityTypeCodes.isNotEmpty() && rand.nextInt(4) != 0) {
                    b.activityTypeCode(activityTypeCodes[rand.nextInt(activityTypeCodes.size)])
                }
                val log = b.build()
                val seedId = UUID.nameUUIDFromBytes(("seed-v1$d$projectId$globalSeq").toByteArray()).toString()
                timeLogService.createOrSkip(seedId, log)
                logsCreated++
                globalSeq++
            }
            d = d.plusDays(1)
        }
        return DevSeedResult(projects.size, logsCreated)
    }

    private fun createProject(id: UUID, name: String, description: String, billable: Boolean): ProjectDto =
        ProjectDto.builder()
            .id(id)
            .name(name)
            .description(description)
            .billable(billable)
            .build()

    private fun pickProject(rand: Random): UUID {
        val i = rand.nextInt(100)
        return when {
            i < 25 -> PROJECT_HORAIN
            i < 35 -> PROJECT_HATCAST_V1
            i < 45 -> PROJECT_HATCAST_V2
            i < 60 -> PROJECT_CHRONO
            i < 75 -> PROJECT_FESTIBASK
            i < 88 -> PROJECT_MEEDS
            else -> PROJECT_WEATHER
        }
    }

    data class DevSeedResult(val projectsCreated: Int, val timeLogsCreated: Int)

    companion object {
        private val ZONE: ZoneId = ZoneId.of("UTC")

        private val PROJECT_HORAIN: UUID = UUID.fromString("11111111-1111-1111-1111-111111111101")
        private val PROJECT_HATCAST_V1: UUID = UUID.fromString("22222222-2222-2222-2222-222222222201")
        private val PROJECT_HATCAST_V2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222202")
        private val PROJECT_CHRONO: UUID = UUID.fromString("33333333-3333-3333-3333-333333333303")
        private val PROJECT_FESTIBASK: UUID = UUID.fromString("44444444-4444-4444-4444-444444444404")
        private val PROJECT_MEEDS: UUID = UUID.fromString("55555555-5555-5555-5555-555555555505")
        private val PROJECT_WEATHER: UUID = UUID.fromString("66666666-6666-6666-6666-666666666606")

        private val PROJECT_BILLABLE: Map<UUID, Boolean> = mapOf(
            PROJECT_HORAIN to true,
            PROJECT_HATCAST_V1 to true,
            PROJECT_HATCAST_V2 to true,
            PROJECT_CHRONO to true,
            PROJECT_FESTIBASK to false,
            PROJECT_MEEDS to false,
            PROJECT_WEATHER to false
        )

        private val DURATIONS = intArrayOf(15, 30, 45, 60, 90, 120)

        private val NOTES = arrayOf(
            "Feature implementation", "Bug fix", "Code review", "Refactor",
            "Documentation", "API integration", "UI polish", "Tests",
            "Sprint planning", "Client call", "Research", "Deployment",
            "Développement backend", "API REST", "Tests e2e"
        )
    }
}
