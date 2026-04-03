package com.horain.service

import com.horain.dto.ProjectDto
import com.horain.dto.TimeLogDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Projects and recent logs scoped to an activity time window [start, end).
 */
@SpringBootTest
@Transactional
class ProjectActivityPeriodServiceTest {

    @Autowired
    private lateinit var projectService: ProjectService

    @Autowired
    private lateinit var timeLogService: TimeLogService

    private lateinit var projectInWindowId: UUID
    private lateinit var projectOutsideWindowId: UUID

    private lateinit var windowStart: Instant
    private lateinit var windowEnd: Instant

    @BeforeEach
    fun setUp() {
        windowEnd = Instant.now()
        windowStart = windowEnd.minus(28, ChronoUnit.DAYS)

        projectInWindowId = projectService.create(
            ProjectDto.builder()
                .name("Zebra In Window")
                .description("has log in period")
                .billable(true)
                .build()
        ).id!!

        projectOutsideWindowId = projectService.create(
            ProjectDto.builder()
                .name("Alpha Outside Window")
                .description("only old log")
                .billable(true)
                .build()
        ).id!!

        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectInWindowId)
                .durationMinutes(480)
                .note("billable dev day")
                .billable(true)
                .loggedAt(windowStart.plus(1, ChronoUnit.DAYS))
                .activityTypeCode("DEV")
                .build()
        )

        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectOutsideWindowId)
                .durationMinutes(60)
                .note("old")
                .loggedAt(windowStart.minus(1, ChronoUnit.DAYS))
                .build()
        )
    }

    @Test
    fun findAllWithActivityInPeriod_returnsOnlyProjectsWithLogsInWindow_sortedByName() {
        val list = projectService.findAllWithActivityInPeriod(windowStart, windowEnd)
        assertThat(list.map { it.id }).containsExactly(projectInWindowId)
        assertThat(list[0].timeLogCount).isEqualTo(1L)
        assertThat(list[0].totalDurationMinutes).isEqualTo(480L)
        assertThat(list[0].revenueCents).isNotNull()
        assertThat(list[0].revenueCents!!).isGreaterThan(0L)
    }

    @Test
    fun findAllWithActivityInPeriod_totalDurationMinutes_sumsMultipleLogsInWindow() {
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectInWindowId)
                .durationMinutes(90)
                .note("extra in window")
                .billable(true)
                .loggedAt(windowStart.plus(2, ChronoUnit.DAYS))
                .activityTypeCode("DEV")
                .build()
        )
        val list = projectService.findAllWithActivityInPeriod(windowStart, windowEnd)
        assertThat(list).hasSize(1)
        assertThat(list[0].timeLogCount).isEqualTo(2L)
        assertThat(list[0].totalDurationMinutes).isEqualTo(570L)
    }

    @Test
    fun findAllWithActivityInPeriod_rejectsInvalidRange() {
        assertThrows<IllegalArgumentException> {
            projectService.findAllWithActivityInPeriod(windowEnd, windowStart)
        }
    }

    @Test
    fun findRecentLogsInPeriod_returnsOnlyLogsInsideWindow_respectsLimit() {
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectInWindowId)
                .durationMinutes(30)
                .note("second in window")
                .loggedAt(windowEnd.minus(1, ChronoUnit.HOURS))
                .build()
        )

        val logs = timeLogService.findRecentLogsInPeriod(windowStart, windowEnd, 50)
        assertThat(logs).hasSize(2)
        assertThat(logs.all { it.loggedAt!!.isBefore(windowEnd) && !it.loggedAt!!.isBefore(windowStart) }).isTrue()

        val limited = timeLogService.findRecentLogsInPeriod(windowStart, windowEnd, 1)
        assertThat(limited).hasSize(1)
        assertThat(limited[0].note).isEqualTo("second in window")
    }
}
