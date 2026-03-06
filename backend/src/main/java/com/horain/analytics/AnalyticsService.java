package com.horain.analytics;

import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.service.ProjectService;
import com.horain.service.TimeLogService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

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
