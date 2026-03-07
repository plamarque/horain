package com.horain.analytics;

import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.service.ProjectService;
import com.horain.service.TimeLogService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics service for time log queries.
 * Provides total duration by project, by period, recent activity, and summaries.
 */
@Service
public class AnalyticsService {

    private final TimeLogService timeLogService;
    private final ProjectService projectService;

    public AnalyticsService(TimeLogService timeLogService, ProjectService projectService) {
        this.timeLogService = timeLogService;
        this.projectService = projectService;
    }

    public int sumTimeForPeriod(Instant start, Instant end) {
        return timeLogService.sumDurationForPeriod(start, end);
    }

    public int sumTimeByProject(UUID projectId, Instant start, Instant end) {
        return timeLogService.sumDurationByProject(projectId, start, end);
    }

    public List<TimeLogDto> getRecentLogs(int limit) {
        return timeLogService.findRecentLogs(limit);
    }

    public List<TimeLogDto> getTimeLogsForPeriod(Instant start, Instant end, UUID projectId) {
        return timeLogService.findLogsForPeriod(start, end, projectId);
    }

    /**
     * Aggregates time logs for chart display.
     *
     * @param groupBy "day_and_project" for stacked bar (days on x-axis, projects as series),
     *                "project_only" for pie (distribution by project)
     * @return Map with "categories" (List of strings) and "series" (List of {name, data})
     *         Values in data are hours (decimal).
     */
    public Map<String, Object> getTimeAggregatedForChart(Instant start, Instant end, String groupBy, ZoneId zone) {
        List<TimeLogDto> logs = timeLogService.findLogsForPeriod(start, end, null);
        Map<UUID, String> projectNames = projectService.findAll().stream()
                .collect(Collectors.toMap(ProjectDto::getId, ProjectDto::getName));

        if ("project_only".equals(groupBy)) {
            Map<UUID, Integer> projectToMinutes = new LinkedHashMap<>();
            for (TimeLogDto log : logs) {
                projectToMinutes.merge(log.getProjectId(), log.getDurationMinutes(), Integer::sum);
            }
            List<String> categories = new ArrayList<>();
            List<Double> data = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : projectToMinutes.entrySet()) {
                categories.add(projectNames.getOrDefault(e.getKey(), "?"));
                data.add(Math.round(e.getValue() / 6.0) / 10.0);
            }
            return Map.of(
                    "categories", categories,
                    "series", List.of(Map.of("name", "Heures", "data", data)));
        }

        if ("day_and_project".equals(groupBy)) {
            Map<LocalDate, Map<UUID, Integer>> dayToProjectToMinutes = new TreeMap<>();
            for (TimeLogDto log : logs) {
                LocalDate day = log.getLoggedAt().atZone(zone).toLocalDate();
                dayToProjectToMinutes
                        .computeIfAbsent(day, k -> new HashMap<>())
                        .merge(log.getProjectId(), log.getDurationMinutes(), Integer::sum);
            }
            List<LocalDate> days = new ArrayList<>(dayToProjectToMinutes.keySet());
            Set<UUID> allProjects = logs.stream().map(TimeLogDto::getProjectId).collect(Collectors.toSet());
            List<UUID> projectsOrdered = allProjects.stream()
                    .sorted((a, b) -> {
                        int totalA = logs.stream().filter(l -> l.getProjectId().equals(a)).mapToInt(TimeLogDto::getDurationMinutes).sum();
                        int totalB = logs.stream().filter(l -> l.getProjectId().equals(b)).mapToInt(TimeLogDto::getDurationMinutes).sum();
                        return Integer.compare(totalB, totalA);
                    })
                    .toList();

            DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEE d", java.util.Locale.FRENCH);
            List<String> categories = days.stream()
                    .map(d -> d.format(dayFormat))
                    .toList();

            List<Map<String, Object>> series = new ArrayList<>();
            for (UUID projectId : projectsOrdered) {
                List<Double> data = new ArrayList<>();
                for (LocalDate day : days) {
                    int minutes = dayToProjectToMinutes.getOrDefault(day, Map.of()).getOrDefault(projectId, 0);
                    data.add(Math.round(minutes / 6.0) / 10.0);
                }
                series.add(Map.<String, Object>of("name", projectNames.getOrDefault(projectId, "?"), "data", data));
            }

            return Map.of("categories", categories, "series", series);
        }

        return Map.of("categories", List.of(), "series", List.of());
    }

    /**
     * Start of today in the given timezone (inclusive).
     */
    public static Instant startOfDay(ZoneId zone) {
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    /**
     * End of today in the given timezone (exclusive, i.e. start of next day).
     */
    public static Instant endOfDay(ZoneId zone) {
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
    }

    /**
     * Start of the week (Monday) in the given timezone.
     */
    public static Instant startOfWeek(ZoneId zone) {
        LocalDate now = LocalDate.now(zone);
        LocalDate monday = now.with(java.time.DayOfWeek.MONDAY);
        return monday.atStartOfDay(zone).toInstant();
    }

    /**
     * End of the week (Sunday 23:59:59.999) - exclusive bound for next week start.
     */
    public static Instant endOfWeek(ZoneId zone) {
        return startOfWeek(zone).plus(java.time.Duration.ofDays(7));
    }

    /**
     * Start of the month in the given timezone.
     */
    public static Instant startOfMonth(ZoneId zone) {
        LocalDate now = LocalDate.now(zone);
        LocalDate first = now.withDayOfMonth(1);
        return first.atStartOfDay(zone).toInstant();
    }

    /**
     * End of the month (exclusive).
     */
    public static Instant endOfMonth(ZoneId zone) {
        LocalDate now = LocalDate.now(zone);
        LocalDate first = now.withDayOfMonth(1);
        return first.plusMonths(1).atStartOfDay(zone).toInstant();
    }
}
