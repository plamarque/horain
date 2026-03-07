package com.horain.dev;

import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.service.ProjectService;
import com.horain.service.TimeLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Generates and loads fictional seed data for development.
 * Provides varied, credible projects and time logs over a long period for chart testing.
 */
@Service
public class DevSeedService {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    // Fixed UUIDs for idempotent seeding (same data on repeated runs)
    private static final UUID PROJECT_HORAIN = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID PROJECT_HATCAST = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID PROJECT_CHRONO = UUID.fromString("33333333-3333-3333-3333-333333333303");
    private static final UUID PROJECT_FESTIBASK = UUID.fromString("44444444-4444-4444-4444-444444444404");
    private static final UUID PROJECT_MEEDS = UUID.fromString("55555555-5555-5555-5555-555555555505");
    private static final UUID PROJECT_WEATHER = UUID.fromString("66666666-6666-6666-6666-666666666606");

    private static final int[] DURATIONS = {15, 30, 45, 60, 90, 120};

    private static final String[] NOTES = {
            "Feature implementation", "Bug fix", "Code review", "Refactor",
            "Documentation", "API integration", "UI polish", "Tests",
            "Sprint planning", "Client call", "Research", "Deployment"
    };

    private final ProjectService projectService;
    private final TimeLogService timeLogService;

    public DevSeedService(ProjectService projectService, TimeLogService timeLogService) {
        this.projectService = projectService;
        this.timeLogService = timeLogService;
    }

    @Transactional
    public DevSeedResult loadSeed() {
        List<ProjectDto> projects = List.of(
                createProject(PROJECT_HORAIN, "Horain", "Personal time journal PWA"),
                createProject(PROJECT_HATCAST, "HatCast", "Podcast production app"),
                createProject(PROJECT_CHRONO, "Chrono EPS", "School timetable manager"),
                createProject(PROJECT_FESTIBASK, "Festibask", "Event basket platform"),
                createProject(PROJECT_MEEDS, "Meeds", "Community engagement"),
                createProject(PROJECT_WEATHER, "Weather Station", "IoT weather dashboard")
        );

        for (ProjectDto p : projects) {
            projectService.createOrSkip(p.getId().toString(), p);
        }

        int logsCreated = 0;
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusMonths(4);

        java.util.Random rand = new java.util.Random(42);
        int globalSeq = 0;

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            // Fewer logs on weekends
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            int maxEntries = weekend ? (rand.nextInt(2)) : (2 + rand.nextInt(4));

            for (int i = 0; i < maxEntries; i++) {
                UUID projectId = pickProject(rand);
                int duration = DURATIONS[rand.nextInt(DURATIONS.length)];
                String note = NOTES[rand.nextInt(NOTES.length)];
                int hour = weekend ? 10 + rand.nextInt(6) : 8 + rand.nextInt(10);
                int minute = rand.nextInt(4) * 15;

                ZonedDateTime loggedAt = d.atTime(hour, minute).atZone(ZONE);
                Instant instant = loggedAt.toInstant();

                TimeLogDto log = TimeLogDto.builder()
                        .projectId(projectId)
                        .durationMinutes(duration)
                        .note(note)
                        .loggedAt(instant)
                        .build();

                String seedId = UUID.nameUUIDFromBytes(
                        ("seed-v1" + d + projectId + globalSeq).getBytes()).toString();
                timeLogService.createOrSkip(seedId, log);
                logsCreated++;
                globalSeq++;
            }
        }

        return new DevSeedResult(projects.size(), logsCreated);
    }

    private ProjectDto createProject(UUID id, String name, String description) {
        return ProjectDto.builder()
                .id(id)
                .name(name)
                .description(description)
                .build();
    }

    private UUID pickProject(java.util.Random rand) {
        int i = rand.nextInt(100);
        if (i < 25) return PROJECT_HORAIN;
        if (i < 45) return PROJECT_HATCAST;
        if (i < 60) return PROJECT_CHRONO;
        if (i < 75) return PROJECT_FESTIBASK;
        if (i < 88) return PROJECT_MEEDS;
        return PROJECT_WEATHER;
    }

    public record DevSeedResult(int projectsCreated, int timeLogsCreated) {}
}
