package com.horain.dev;

import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.repository.ActivityTypeRepository;
import com.horain.repository.ProjectRepository;
import com.horain.repository.TimeLogRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates and loads fictional seed data for development.
 * Provides varied, credible projects and time logs over a long period for chart testing.
 */
@Service
public class DevSeedService {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    // Fixed UUIDs for idempotent seeding (same data on repeated runs)
    private static final UUID PROJECT_HORAIN = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID PROJECT_HATCAST_V1 = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID PROJECT_HATCAST_V2 = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID PROJECT_CHRONO = UUID.fromString("33333333-3333-3333-3333-333333333303");
    private static final UUID PROJECT_FESTIBASK = UUID.fromString("44444444-4444-4444-4444-444444444404");
    private static final UUID PROJECT_MEEDS = UUID.fromString("55555555-5555-5555-5555-555555555505");
    private static final UUID PROJECT_WEATHER = UUID.fromString("66666666-6666-6666-6666-666666666606");

    /** Which projects are billable by default (client/work vs internal/personal). */
    private static final Map<UUID, Boolean> PROJECT_BILLABLE = Map.of(
            PROJECT_HORAIN, true,
            PROJECT_HATCAST_V1, true,
            PROJECT_HATCAST_V2, true,
            PROJECT_CHRONO, true,
            PROJECT_FESTIBASK, false,
            PROJECT_MEEDS, false,
            PROJECT_WEATHER, false
    );

    private static final int[] DURATIONS = {15, 30, 45, 60, 90, 120};

    private static final String[] NOTES = {
            "Feature implementation", "Bug fix", "Code review", "Refactor",
            "Documentation", "API integration", "UI polish", "Tests",
            "Sprint planning", "Client call", "Research", "Deployment",
            "Développement backend", "API REST", "Tests e2e"
    };

    private final ProjectService projectService;
    private final TimeLogService timeLogService;
    private final TimeLogRepository timeLogRepository;
    private final ProjectRepository projectRepository;
    private final ActivityTypeRepository activityTypeRepository;

    public DevSeedService(ProjectService projectService, TimeLogService timeLogService,
                         TimeLogRepository timeLogRepository, ProjectRepository projectRepository,
                         ActivityTypeRepository activityTypeRepository) {
        this.projectService = projectService;
        this.timeLogService = timeLogService;
        this.timeLogRepository = timeLogRepository;
        this.projectRepository = projectRepository;
        this.activityTypeRepository = activityTypeRepository;
    }

    /** Clears all time logs and projects, then loads seed data. Dev only. */
    @Transactional
    public DevSeedResult resetAndLoadSeed(LocalDate fixedToday) {
        timeLogRepository.deleteAll();
        projectRepository.deleteAll();
        return loadSeed(fixedToday);
    }

    @Transactional
    public DevSeedResult loadSeed() {
        return loadSeed(null);
    }

    @Transactional
    public DevSeedResult loadSeed(LocalDate fixedToday) {
        List<ProjectDto> projects = List.of(
                createProject(PROJECT_HORAIN, "Horain", "Personal time journal PWA", true),
                createProject(PROJECT_HATCAST_V1, "HatCast V1", "Podcast production app", true),
                createProject(PROJECT_HATCAST_V2, "HatCast V2", "Podcast production app", true),
                createProject(PROJECT_CHRONO, "Chrono EPS", "School timetable manager", true),
                createProject(PROJECT_FESTIBASK, "Festibask", "Event basket platform", false),
                createProject(PROJECT_MEEDS, "Meeds", "Community engagement", false),
                createProject(PROJECT_WEATHER, "Weather Station", "IoT weather dashboard", false)
        );

        for (ProjectDto p : projects) {
            projectService.createOrSkip(p.getId().toString(), p);
        }

        List<String> activityTypeCodes = activityTypeRepository.findAllByOrderByCodeAsc().stream()
                .map(com.horain.model.ActivityType::getCode)
                .collect(Collectors.toList());

        int logsCreated = 0;
        // Seed the full year 2026 so dev and evals have activities across the year
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        java.util.Random rand = new java.util.Random(42);
        int globalSeq = 0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
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

                boolean projectBillable = PROJECT_BILLABLE.getOrDefault(projectId, true);
                // ~10% of entries override: billable entry on non-billable project or vice versa
                Boolean entryBillableOverride = rand.nextInt(10) == 0 ? !projectBillable : null;
                boolean billable = entryBillableOverride != null ? entryBillableOverride : projectBillable;

                ZonedDateTime loggedAt = d.atTime(hour, minute).atZone(ZONE);
                Instant instant = loggedAt.toInstant();

                TimeLogDto b = TimeLogDto.builder()
                        .projectId(projectId)
                        .durationMinutes(duration)
                        .note(note)
                        .billable(entryBillableOverride)
                        .loggedAt(instant);
                // ~75% of billable entries get an activity type so project revenue is non-zero in seed
                if (billable && !activityTypeCodes.isEmpty() && rand.nextInt(4) != 0) {
                    b.activityTypeCode(activityTypeCodes.get(rand.nextInt(activityTypeCodes.size())));
                }
                TimeLogDto log = b.build();

                String seedId = UUID.nameUUIDFromBytes(
                        ("seed-v1" + d + projectId + globalSeq).getBytes()).toString();
                timeLogService.createOrSkip(seedId, log);
                logsCreated++;
                globalSeq++;
            }
        }

        return new DevSeedResult(projects.size(), logsCreated);
    }

    private ProjectDto createProject(UUID id, String name, String description, boolean billable) {
        return ProjectDto.builder()
                .id(id)
                .name(name)
                .description(description)
                .billable(billable)
                .build();
    }

    private UUID pickProject(java.util.Random rand) {
        int i = rand.nextInt(100);
        if (i < 25) return PROJECT_HORAIN;
        if (i < 35) return PROJECT_HATCAST_V1;
        if (i < 45) return PROJECT_HATCAST_V2;
        if (i < 60) return PROJECT_CHRONO;
        if (i < 75) return PROJECT_FESTIBASK;
        if (i < 88) return PROJECT_MEEDS;
        return PROJECT_WEATHER;
    }

    public record DevSeedResult(int projectsCreated, int timeLogsCreated) {}
}
