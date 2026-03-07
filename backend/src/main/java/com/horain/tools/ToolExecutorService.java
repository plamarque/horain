package com.horain.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.analytics.AnalyticsService;
import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.llm.ToolCallRequest;
import com.horain.llm.ToolCallResult;
import com.horain.service.ProjectService;
import com.horain.service.TimeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executes tool calls requested by the LLM.
 * Dispatches to ProjectService, TimeLogService, and AnalyticsService.
 */
@Service
public class ToolExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorService.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    private final ProjectService projectService;
    private final TimeLogService timeLogService;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    public ToolExecutorService(
            ProjectService projectService,
            TimeLogService timeLogService,
            AnalyticsService analyticsService,
            ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.timeLogService = timeLogService;
        this.analyticsService = analyticsService;
        this.objectMapper = objectMapper;
    }

    public ToolCallResult execute(ToolCallRequest request) {
        try {
            JsonNode args = parseArgs(request.arguments());
            String result = switch (request.name()) {
                case ToolRegistry.LIST_PROJECTS -> executeListProjects();
                case ToolRegistry.SEARCH_PROJECT -> executeSearchProject(args);
                case ToolRegistry.CREATE_PROJECT -> executeCreateProject(args);
                case ToolRegistry.CREATE_TIME_LOG -> executeCreateTimeLog(args);
                case ToolRegistry.GET_RECENT_LOGS -> executeGetRecentLogs(args);
                case ToolRegistry.GET_TIME_LOGS_FOR_PERIOD -> executeGetTimeLogsForPeriod(args);
                case ToolRegistry.SUM_TIME_BY_PROJECT -> executeSumTimeByProject(args);
                case ToolRegistry.SUM_TIME_FOR_PERIOD -> executeSumTimeForPeriod(args);
                case ToolRegistry.GET_CURRENT_DATETIME -> executeGetCurrentDatetime();
                case ToolRegistry.GET_TIME_AGGREGATED_FOR_CHART -> executeGetTimeAggregatedForChart(args);
                case ToolRegistry.PROPOSE_CHART -> executeProposeChart(args);
                default -> "{\"error\":\"Unknown tool: " + request.name() + "\"}";
            };
            return new ToolCallResult(request.id(), result);
        } catch (Exception e) {
            log.warn("Tool execution failed: {} - {}", request.name(), e.getMessage());
            return new ToolCallResult(request.id(), "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private JsonNode parseArgs(String arguments) {
        try {
            if (arguments == null || arguments.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(arguments);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String executeListProjects() {
        List<ProjectDto> projects = projectService.findAll();
        List<Map<String, String>> list = projects.stream()
                .map(p -> Map.<String, String>of(
                        "id", p.getId().toString(),
                        "name", p.getName(),
                        "description", p.getDescription() != null ? p.getDescription() : ""))
                .toList();
        return toJson(Map.of("projects", list));
    }

    private String executeSearchProject(JsonNode args) {
        String name = getText(args, "name");
        if (name == null || name.isBlank()) {
            return toJson(Map.of("error", "name is required"));
        }
        List<ProjectDto> matches = projectService.searchByName(name);
        List<Map<String, String>> list = matches.stream()
                .map(p -> Map.<String, String>of(
                        "id", p.getId().toString(),
                        "name", p.getName(),
                        "description", p.getDescription() != null ? p.getDescription() : ""))
                .toList();
        return toJson(Map.of("matching_projects", list));
    }

    private String executeCreateProject(JsonNode args) {
        String name = getText(args, "name");
        if (name == null || name.isBlank()) {
            return toJson(Map.of("error", "name is required"));
        }
        String description = getText(args, "description");
        ProjectDto dto = ProjectDto.builder()
                .name(name)
                .description(description)
                .build();
        ProjectDto created = projectService.create(dto);
        return toJson(Map.of(
                "project", Map.of(
                        "id", created.getId().toString(),
                        "name", created.getName(),
                        "description", created.getDescription() != null ? created.getDescription() : "")));
    }

    private String executeCreateTimeLog(JsonNode args) {
        String projectIdStr = getText(args, "projectId");
        Integer durationMinutes = getInt(args, "durationMinutes");
        if (projectIdStr == null || projectIdStr.isBlank()) {
            return toJson(Map.of("error", "projectId is required"));
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            return toJson(Map.of("error", "durationMinutes must be a positive integer"));
        }
        UUID projectId = UUID.fromString(projectIdStr);
        String note = getText(args, "note");
        String loggedAtStr = getText(args, "loggedAt");
        Instant loggedAt = loggedAtStr != null && !loggedAtStr.isBlank()
                ? Instant.parse(loggedAtStr)
                : Instant.now();

        TimeLogDto dto = TimeLogDto.builder()
                .projectId(projectId)
                .durationMinutes(durationMinutes)
                .note(note)
                .loggedAt(loggedAt)
                .build();
        TimeLogDto created = timeLogService.create(dto);
        return toJson(Map.of(
                "time_log", Map.of(
                        "id", created.getId().toString(),
                        "projectId", created.getProjectId().toString(),
                        "durationMinutes", created.getDurationMinutes(),
                        "note", created.getNote() != null ? created.getNote() : "",
                        "loggedAt", created.getLoggedAt().toString())));
    }

    private String executeGetRecentLogs(JsonNode args) {
        Integer limit = getInt(args, "limit");
        int limitVal = limit != null && limit > 0 ? Math.min(limit, 50) : 20;
        List<TimeLogDto> logs = timeLogService.findRecentLogs(limitVal);
        List<ProjectDto> projects = projectService.findAll();
        var projectMap = projects.stream().collect(Collectors.toMap(p -> p.getId().toString(), p -> p.getName()));

        List<Map<String, Object>> entries = new ArrayList<>();
        for (TimeLogDto log : logs) {
            entries.add(Map.of(
                    "id", log.getId().toString(),
                    "projectId", log.getProjectId().toString(),
                    "projectName", projectMap.getOrDefault(log.getProjectId().toString(), "?"),
                    "durationMinutes", log.getDurationMinutes(),
                    "note", log.getNote() != null ? log.getNote() : "",
                    "loggedAt", log.getLoggedAt().toString()));
        }
        return toJson(Map.of("time_logs", entries));
    }

    private String executeGetTimeLogsForPeriod(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (startStr == null || endStr == null) {
            return toJson(Map.of("error", "start and end (ISO-8601) are required"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        String projectIdStr = getText(args, "projectId");
        UUID projectId = projectIdStr != null && !projectIdStr.isBlank() ? UUID.fromString(projectIdStr) : null;

        List<TimeLogDto> logs = timeLogService.findLogsForPeriod(start, end, projectId);
        List<ProjectDto> projects = projectService.findAll();
        var projectMap = projects.stream().collect(Collectors.toMap(p -> p.getId().toString(), p -> p.getName()));

        List<Map<String, Object>> entries = new ArrayList<>();
        for (TimeLogDto log : logs) {
            entries.add(Map.of(
                    "id", log.getId().toString(),
                    "projectId", log.getProjectId().toString(),
                    "projectName", projectMap.getOrDefault(log.getProjectId().toString(), "?"),
                    "durationMinutes", log.getDurationMinutes(),
                    "note", log.getNote() != null ? log.getNote() : "",
                    "loggedAt", log.getLoggedAt().toString()));
        }
        return toJson(Map.of("time_logs", entries));
    }

    private String executeSumTimeByProject(JsonNode args) {
        String projectIdStr = getText(args, "projectId");
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (projectIdStr == null || startStr == null || endStr == null) {
            return toJson(Map.of("error", "projectId, start, and end are required"));
        }
        UUID projectId = UUID.fromString(projectIdStr);
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        int minutes = analyticsService.sumTimeByProject(projectId, start, end);
        return toJson(Map.of("totalMinutes", minutes, "totalHours", Math.round(minutes / 6.0) / 10.0));
    }

    private String executeSumTimeForPeriod(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (startStr == null || endStr == null) {
            return toJson(Map.of("error", "start and end (ISO-8601) are required"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        int minutes = analyticsService.sumTimeForPeriod(start, end);
        return toJson(Map.of("totalMinutes", minutes, "totalHours", Math.round(minutes / 6.0) / 10.0));
    }

    private String executeGetCurrentDatetime() {
        ZonedDateTime now = ZonedDateTime.now(DEFAULT_ZONE);
        return toJson(Map.of(
                "iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "timezone", DEFAULT_ZONE.getId(),
                "startOfToday", AnalyticsService.startOfDay(DEFAULT_ZONE).toString(),
                "endOfToday", AnalyticsService.endOfDay(DEFAULT_ZONE).toString(),
                "startOfWeek", AnalyticsService.startOfWeek(DEFAULT_ZONE).toString(),
                "endOfWeek", AnalyticsService.endOfWeek(DEFAULT_ZONE).toString(),
                "startOfMonth", AnalyticsService.startOfMonth(DEFAULT_ZONE).toString(),
                "endOfMonth", AnalyticsService.endOfMonth(DEFAULT_ZONE).toString()));
    }

    private String executeGetTimeAggregatedForChart(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        String groupBy = getText(args, "groupBy");
        if (startStr == null || endStr == null) {
            return toJson(Map.of("error", "start and end (ISO-8601) are required"));
        }
        if (groupBy == null || groupBy.isBlank()) {
            return toJson(Map.of("error", "groupBy is required (day_and_project or project_only)"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        var result = analyticsService.getTimeAggregatedForChart(start, end, groupBy, DEFAULT_ZONE);
        return toJson(result);
    }

    private String executeProposeChart(JsonNode args) {
        return toJson(Map.of("status", "ok"));
    }

    private String getText(JsonNode args, String key) {
        JsonNode n = args != null ? args.get(key) : null;
        return n != null && n.isTextual() ? n.asText() : (n != null ? n.asText() : null);
    }

    private Integer getInt(JsonNode args, String key) {
        JsonNode n = args != null ? args.get(key) : null;
        return n != null && n.isNumber() ? n.intValue() : null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"Serialization failed\"}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
