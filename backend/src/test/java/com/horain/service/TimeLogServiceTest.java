package com.horain.service;

import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TimeLogService, especially findLogsByKeyword (search by note or project name).
 */
@SpringBootTest
@Transactional
class TimeLogServiceTest {

    @Autowired
    private TimeLogService timeLogService;

    @Autowired
    private ProjectService projectService;

    private UUID projectHorainId;
    private UUID projectApiClientId;

    @BeforeEach
    void setUp() {
        ProjectDto horain = projectService.create(ProjectDto.builder()
                .name("Horain")
                .description("Time journal")
                .billable(true)
                .build());
        projectHorainId = horain.getId();

        ProjectDto apiClient = projectService.create(ProjectDto.builder()
                .name("API Client")
                .description("Client project")
                .billable(true)
                .build());
        projectApiClientId = apiClient.getId();
    }

    @Test
    void findLogsByKeyword_returnsEmptyWhenQueryBlank() {
        assertThat(timeLogService.findLogsByKeyword(null, 20)).isEmpty();
        assertThat(timeLogService.findLogsByKeyword("  ", 20)).isEmpty();
    }

    @Test
    void findLogsByKeyword_matchesNote() {
        timeLogService.create(TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(60)
                .note("Développement backend et API REST")
                .loggedAt(Instant.now())
                .build());
        timeLogService.create(TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(30)
                .note("Réunion")
                .loggedAt(Instant.now())
                .build());

        List<TimeLogDto> dev = timeLogService.findLogsByKeyword("développement", 20);
        assertThat(dev).hasSize(1);
        assertThat(dev.get(0).getNote()).contains("Développement");

        List<TimeLogDto> api = timeLogService.findLogsByKeyword("api", 20);
        assertThat(api).hasSize(1);
        assertThat(api.get(0).getNote()).contains("API");

        List<TimeLogDto> reunion = timeLogService.findLogsByKeyword("réunion", 20);
        assertThat(reunion).hasSize(1);
        assertThat(reunion.get(0).getNote()).contains("Réunion");
    }

    @Test
    void findLogsByKeyword_matchesProjectName() {
        timeLogService.create(TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(45)
                .note("Some work")
                .loggedAt(Instant.now())
                .build());
        timeLogService.create(TimeLogDto.builder()
                .projectId(projectApiClientId)
                .durationMinutes(60)
                .note("Integration")
                .loggedAt(Instant.now())
                .build());

        List<TimeLogDto> horain = timeLogService.findLogsByKeyword("Horain", 20);
        assertThat(horain).hasSize(1);
        assertThat(horain.get(0).getProjectId()).isEqualTo(projectHorainId);

        List<TimeLogDto> api = timeLogService.findLogsByKeyword("API", 20);
        assertThat(api).hasSize(1);
        assertThat(api.get(0).getProjectId()).isEqualTo(projectApiClientId);
    }

    @Test
    void findLogsByKeyword_caseInsensitive() {
        timeLogService.create(TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(30)
                .note("Tests e2e et développement")
                .loggedAt(Instant.now())
                .build());

        assertThat(timeLogService.findLogsByKeyword("tests", 20)).hasSize(1);
        assertThat(timeLogService.findLogsByKeyword("TESTS", 20)).hasSize(1);
        assertThat(timeLogService.findLogsByKeyword("Développement", 20)).hasSize(1);
    }

    @Test
    void findLogsByKeyword_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            timeLogService.create(TimeLogDto.builder()
                    .projectId(projectHorainId)
                    .durationMinutes(30)
                    .note("keyword in note")
                    .loggedAt(Instant.now().minusSeconds(i))
                    .build());
        }
        assertThat(timeLogService.findLogsByKeyword("keyword", 2)).hasSize(2);
        assertThat(timeLogService.findLogsByKeyword("keyword", 10)).hasSize(5);
    }

    @Test
    void findLogsByKeyword_noMatchReturnsEmpty() {
        timeLogService.create(TimeLogDto.builder()
                .projectId(projectHorainId)
                .durationMinutes(30)
                .note("Only this note")
                .loggedAt(Instant.now())
                .build());
        assertThat(timeLogService.findLogsByKeyword("absent", 20)).isEmpty();
    }
}
