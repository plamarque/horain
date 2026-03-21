package com.horain.service

import com.horain.dto.ProjectDto
import com.horain.dto.TimeLogDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Tests for TimeLogService, especially findLogsByKeyword (search by note or project name).
 */
@SpringBootTest
@Transactional
class TimeLogServiceTest {

    @Autowired
    private lateinit var timeLogService: TimeLogService

    @Autowired
    private lateinit var projectService: ProjectService

    private lateinit var projectHorainId: UUID
    private lateinit var projectApiClientId: UUID

    @BeforeEach
    fun setUp() {
        val horain = projectService.create(
            ProjectDto.builder()
                .name("Horain")
                .description("Time journal")
                .billable(true)
                .build()
        )
        projectHorainId = horain.id!!

        val apiClient = projectService.create(
            ProjectDto.builder()
                .name("API Client")
                .description("Client project")
                .billable(true)
                .build()
        )
        projectApiClientId = apiClient.id!!
    }

    @Test
    fun findLogsByKeyword_returnsEmptyWhenQueryBlank() {
        assertThat(timeLogService.findLogsByKeyword(null, 20)).isEmpty()
        assertThat(timeLogService.findLogsByKeyword("  ", 20)).isEmpty()
    }

    @Test
    fun findLogsByKeyword_matchesNote() {
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(60)
                .note("Développement backend et API REST")
                .loggedAt(Instant.now())
                .build()
        )
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(30)
                .note("Réunion")
                .loggedAt(Instant.now())
                .build()
        )

        val dev = timeLogService.findLogsByKeyword("développement", 20)
        assertThat(dev).hasSize(1)
        assertThat(dev[0].note).contains("Développement")

        val api = timeLogService.findLogsByKeyword("api", 20)
        assertThat(api).hasSize(1)
        assertThat(api[0].note).contains("API")

        val reunion = timeLogService.findLogsByKeyword("réunion", 20)
        assertThat(reunion).hasSize(1)
        assertThat(reunion[0].note).contains("Réunion")
    }

    @Test
    fun findLogsByKeyword_matchesProjectName() {
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(45)
                .note("Some work")
                .loggedAt(Instant.now())
                .build()
        )
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectApiClientId)
                .durationMinutes(60)
                .note("Integration")
                .loggedAt(Instant.now())
                .build()
        )

        val horain = timeLogService.findLogsByKeyword("Horain", 20)
        assertThat(horain).hasSize(1)
        assertThat(horain[0].projectId).isEqualTo(projectHorainId)

        val api = timeLogService.findLogsByKeyword("API", 20)
        assertThat(api).hasSize(1)
        assertThat(api[0].projectId).isEqualTo(projectApiClientId)
    }

    @Test
    fun findLogsByKeyword_caseInsensitive() {
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(30)
                .note("Tests e2e et développement")
                .loggedAt(Instant.now())
                .build()
        )

        assertThat(timeLogService.findLogsByKeyword("tests", 20)).hasSize(1)
        assertThat(timeLogService.findLogsByKeyword("TESTS", 20)).hasSize(1)
        assertThat(timeLogService.findLogsByKeyword("Développement", 20)).hasSize(1)
    }

    @Test
    fun findLogsByKeyword_respectsLimit() {
        for (i in 0 until 5) {
            timeLogService.create(
                TimeLogDto.builder()
                    .projectId(projectHorainId)
                    .durationMinutes(30)
                    .note("keyword in note")
                    .loggedAt(Instant.now().minusSeconds(i.toLong()))
                    .build()
            )
        }
        assertThat(timeLogService.findLogsByKeyword("keyword", 2)).hasSize(2)
        assertThat(timeLogService.findLogsByKeyword("keyword", 10)).hasSize(5)
    }

    @Test
    fun findLogsByKeyword_noMatchReturnsEmpty() {
        timeLogService.create(
            TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(30)
                .note("Only this note")
                .loggedAt(Instant.now())
                .build()
        )
        assertThat(timeLogService.findLogsByKeyword("absent", 20)).isEmpty()
    }
}
