package com.horain.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.analytics.AnalyticsService;
import com.horain.dto.ActivityTypeDto;
import com.horain.dto.ProjectDto;
import com.horain.dto.TimeLogDto;
import com.horain.llm.ToolCallRequest;
import com.horain.llm.ToolCallResult;
import com.horain.service.ActivityTypeService;
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
import java.util.regex.Pattern;
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
    private final ActivityTypeService activityTypeService;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    public ToolExecutorService(
            ProjectService projectService,
            TimeLogService timeLogService,
            ActivityTypeService activityTypeService,
            AnalyticsService analyticsService,
            ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.timeLogService = timeLogService;
        this.activityTypeService = activityTypeService;
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
                case ToolRegistry.UPDATE_PROJECT -> executeUpdateProject(args);
                case ToolRegistry.DELETE_PROJECT -> executeDeleteProject(args);
                case ToolRegistry.LIST_ACTIVITY_TYPES -> executeListActivityTypes();
                case ToolRegistry.CREATE_ACTIVITY_TYPE -> executeCreateActivityType(args);
                case ToolRegistry.UPDATE_ACTIVITY_TYPE -> executeUpdateActivityType(args);
                case ToolRegistry.DELETE_ACTIVITY_TYPE -> executeDeleteActivityType(args);
                case ToolRegistry.CREATE_TIME_LOG -> executeCreateTimeLog(args);
                case ToolRegistry.GET_RECENT_LOGS -> executeGetRecentLogs(args);
                case ToolRegistry.GET_TIME_LOGS_FOR_PERIOD -> executeGetTimeLogsForPeriod(args);
                case ToolRegistry.SUM_TIME_BY_PROJECT -> executeSumTimeByProject(args);
                case ToolRegistry.SUM_TIME_FOR_PERIOD -> executeSumTimeForPeriod(args);
                case ToolRegistry.SUM_BILLABLE_TIME_FOR_PERIOD -> executeSumBillableTimeForPeriod(args);
                case ToolRegistry.SUM_NON_BILLABLE_TIME_FOR_PERIOD -> executeSumNonBillableTimeForPeriod(args);
                case ToolRegistry.GET_CURRENT_DATETIME -> executeGetCurrentDatetime();
                case ToolRegistry.GET_TIME_AGGREGATED_FOR_CHART -> executeGetTimeAggregatedForChart(args);
                case ToolRegistry.PROPOSE_CHART -> executeProposeChart(args);
                case ToolRegistry.PROPOSE_ENTRIES -> executeProposeEntries(args);
                case ToolRegistry.UPDATE_TIME_LOG -> executeUpdateTimeLog(args);
                case ToolRegistry.DELETE_TIME_LOG -> executeDeleteTimeLog(args);
                default -> toDualResult("Error: Unknown tool " + request.name(), Map.of("error", "Unknown tool: " + request.name()));
            };
            return new ToolCallResult(request.id(), result);
        } catch (Exception e) {
            log.warn("Tool execution failed: {} - {}", request.name(), e.getMessage());
            return new ToolCallResult(request.id(), toDualResult("Error: " + e.getMessage(), Map.of("error", e.getMessage())));
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
        List<Map<String, Object>> list = projects.stream()
                .map(p -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId().toString());
                    m.put("name", p.getName());
                    m.put("description", p.getDescription() != null ? p.getDescription() : "");
                    m.put("billable", Boolean.TRUE.equals(p.getBillable()));
                    return m;
                })
                .toList();
        String llm = "## Projects (" + list.size() + ")\n"
                + list.stream()
                .map(m -> "- " + m.get("name") + " (id: " + m.get("id") + ")" + (Boolean.TRUE.equals(m.get("billable")) ? ", billable" : ""))
                .collect(Collectors.joining("\n"));
        return toDualResult(llm, Map.of("projects", list));
    }

    private static final int CLOSE_MATCH_MAX = 3;

    private String executeSearchProject(JsonNode args) {
        String name = getText(args, "name");
        if (name == null || name.isBlank()) {
            return toDualResult("Error: name is required. Provide the project name to search for.", Map.of("error", "name is required"));
        }
        List<ProjectDto> matches = projectService.searchByName(name);
        List<Map<String, Object>> list = projectsToMaps(matches);
        Map<String, Object> data = new java.util.HashMap<>(Map.of("matching_projects", list));
        if (matches.isEmpty()) {
            List<ProjectDto> closeMatches = projectService.findCloseMatchesByName(name, CLOSE_MATCH_MAX);
            if (!closeMatches.isEmpty()) {
                data.put("close_matches", projectsToMaps(closeMatches));
                String closeNames = projectsToMaps(closeMatches).stream()
                        .map(m -> (String) m.get("name"))
                        .collect(Collectors.joining(", "));
                String llm = "No exact match for \"" + name + "\". Close matches: " + closeNames + ". Propose the first one and ask the user to confirm before logging.";
                return toDualResult(llm, data);
            }
        }
        String llm = list.isEmpty() ? "No projects found for \"" + name + "\"." : "## Matching projects (" + list.size() + ")\n"
                + list.stream()
                .map(m -> "- " + m.get("name") + " (id: " + m.get("id") + ")")
                .collect(Collectors.joining("\n"));
        return toDualResult(llm, data);
    }

    private List<Map<String, Object>> projectsToMaps(List<ProjectDto> projects) {
        return projects.stream()
                .map(p -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId().toString());
                    m.put("name", p.getName());
                    m.put("description", p.getDescription() != null ? p.getDescription() : "");
                    m.put("billable", Boolean.TRUE.equals(p.getBillable()));
                    return m;
                })
                .toList();
    }

    private String executeCreateProject(JsonNode args) {
        String name = getText(args, "name");
        if (name == null || name.isBlank()) {
            return toDualResult("Error: name is required. Provide the project name.", Map.of("error", "name is required"));
        }
        String description = getText(args, "description");
        Boolean billable = getBoolean(args, "billable");
        ProjectDto dto = ProjectDto.builder()
                .name(name)
                .description(description)
                .billable(billable != null ? billable : true)
                .build();
        ProjectDto created = projectService.create(dto);
        Map<String, Object> projectMap = new java.util.HashMap<>();
        projectMap.put("id", created.getId().toString());
        projectMap.put("name", created.getName());
        projectMap.put("description", created.getDescription() != null ? created.getDescription() : "");
        projectMap.put("billable", Boolean.TRUE.equals(created.getBillable()));
        String llm = "Created project \"" + created.getName() + "\" (id: " + created.getId() + ").";
        return toDualResult(llm, Map.of("project", projectMap));
    }

    private String executeUpdateProject(JsonNode args) {
        String idStr = getText(args, "id");
        if (idStr == null || idStr.isBlank()) {
            return toDualResult("Error: id is required. Use project UUID or name.", Map.of("error", "id is required"));
        }
        UUID projectId = resolveProjectId(idStr);
        ProjectDto patch = ProjectDto.builder().id(projectId).build();
        String name = getText(args, "name");
        if (name != null && !name.isBlank()) {
            patch.setName(name.trim());
        }
        String description = getText(args, "description");
        if (description != null) {
            patch.setDescription(description);
        }
        Boolean billableArg = getBoolean(args, "billable");
        if (billableArg != null) {
            patch.setBillable(billableArg);
        }
        if (patch.getName() == null && patch.getDescription() == null && patch.getBillable() == null) {
            return toDualResult("Error: Provide at least one of name, description, or billable.", Map.of("error", "At least one of name, description or billable must be provided"));
        }
        ProjectDto updated = projectService.update(projectId, patch);
        Map<String, Object> projectMap = new java.util.HashMap<>();
        projectMap.put("id", updated.getId().toString());
        projectMap.put("name", updated.getName());
        projectMap.put("description", updated.getDescription() != null ? updated.getDescription() : "");
        projectMap.put("billable", Boolean.TRUE.equals(updated.getBillable()));
        return toDualResult("Updated project \"" + updated.getName() + "\".", Map.of("project", projectMap));
    }

    private String executeDeleteProject(JsonNode args) {
        String idStr = getText(args, "id");
        if (idStr == null || idStr.isBlank()) {
            return toDualResult("Error: id is required. Use project UUID or name.", Map.of("error", "id is required"));
        }
        UUID projectId = resolveProjectId(idStr);
        projectService.deleteById(projectId);
        return toDualResult("Project deleted.", Map.of("status", "deleted"));
    }

    private String executeListActivityTypes() {
        List<ActivityTypeDto> types = activityTypeService.findAll();
        List<Map<String, Object>> list = types.stream()
                .map(a -> Map.<String, Object>of(
                        "code", a.getCode(),
                        "label", a.getLabel() != null ? a.getLabel() : "",
                        "dailyRateCents", a.getDailyRateCents() != null ? a.getDailyRateCents() : 0,
                        "description", a.getDescription() != null ? a.getDescription() : ""))
                .toList();
        String llm = "## Activity types (" + list.size() + ")\n"
                + list.stream()
                .map(m -> "- " + m.get("code") + ": " + m.get("label") + " (" + m.get("dailyRateCents") + " cents/day)")
                .collect(Collectors.joining("\n"));
        return toDualResult(llm, Map.of("activity_types", list));
    }

    private String executeCreateActivityType(JsonNode args) {
        String code = getText(args, "code");
        String label = getText(args, "label");
        Integer dailyRateCents = getInt(args, "dailyRateCents");
        if (code == null || code.isBlank()) {
            return toDualResult("Error: code is required. Provide the activity type code (e.g. DEV, AI).", Map.of("error", "code is required"));
        }
        if (dailyRateCents == null || dailyRateCents <= 0) {
            return toDualResult("Error: dailyRateCents must be a positive integer (e.g. 40000 for 400 €).", Map.of("error", "dailyRateCents must be a positive integer"));
        }
        ActivityTypeDto dto = new ActivityTypeDto();
        dto.setCode(code.trim().toUpperCase());
        dto.setLabel(label != null ? label.trim() : "");
        dto.setDailyRateCents(dailyRateCents);
        String description = getText(args, "description");
        if (description != null) dto.setDescription(description);
        ActivityTypeDto created = activityTypeService.create(dto);
        Map<String, Object> at = Map.of(
                "code", created.getCode(),
                "label", created.getLabel() != null ? created.getLabel() : "",
                "dailyRateCents", created.getDailyRateCents() != null ? created.getDailyRateCents() : 0,
                "description", created.getDescription() != null ? created.getDescription() : "");
        String llm = "Created activity type " + created.getCode() + ": " + created.getLabel() + " (" + (created.getDailyRateCents() != null ? created.getDailyRateCents() / 100 : 0) + " €/day).";
        return toDualResult(llm, Map.of("activity_type", at));
    }

    private String executeUpdateActivityType(JsonNode args) {
        String code = getText(args, "code");
        if (code == null || code.isBlank()) {
            return toDualResult("Error: code is required. Use the activity type code to update.", Map.of("error", "code is required"));
        }
        ActivityTypeDto patch = new ActivityTypeDto();
        String label = getText(args, "label");
        if (label != null) patch.setLabel(label);
        Integer dailyRateCents = getInt(args, "dailyRateCents");
        if (dailyRateCents != null) patch.setDailyRateCents(dailyRateCents);
        String description = getText(args, "description");
        if (description != null) patch.setDescription(description);
        ActivityTypeDto updated = activityTypeService.update(code.trim().toUpperCase(), patch);
        Map<String, Object> at = Map.of(
                "code", updated.getCode(),
                "label", updated.getLabel() != null ? updated.getLabel() : "",
                "dailyRateCents", updated.getDailyRateCents() != null ? updated.getDailyRateCents() : 0,
                "description", updated.getDescription() != null ? updated.getDescription() : "");
        return toDualResult("Updated activity type " + updated.getCode() + ".", Map.of("activity_type", at));
    }

    private String executeDeleteActivityType(JsonNode args) {
        String code = getText(args, "code");
        if (code == null || code.isBlank()) {
            return toDualResult("Error: code is required. Use the activity type code to delete.", Map.of("error", "code is required"));
        }
        activityTypeService.deleteByCode(code.trim().toUpperCase());
        return toDualResult("Activity type " + code.trim().toUpperCase() + " deleted.", Map.of("status", "deleted"));
    }

    private String executeCreateTimeLog(JsonNode args) {
        String projectIdStr = getText(args, "projectId");
        Integer durationMinutes = getInt(args, "durationMinutes");
        if (projectIdStr == null || projectIdStr.isBlank()) {
            return toDualResult("Error: projectId is required. Get it from list_projects or search_project.", Map.of("error", "projectId is required"));
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            return toDualResult("Error: durationMinutes must be a positive integer.", Map.of("error", "durationMinutes must be a positive integer"));
        }
        UUID projectId = resolveProjectId(projectIdStr);
        String note = getText(args, "note");
        String loggedAtStr = getText(args, "loggedAt");
        Instant loggedAt = loggedAtStr != null && !loggedAtStr.isBlank()
                ? Instant.parse(loggedAtStr)
                : Instant.now();
        Boolean billableArg = getBoolean(args, "billable");
        String activityTypeCode = getText(args, "activityTypeCode");

        TimeLogDto dto = TimeLogDto.builder()
                .projectId(projectId)
                .durationMinutes(durationMinutes)
                .note(note)
                .billable(billableArg)
                .loggedAt(loggedAt)
                .build();
        if (activityTypeCode != null && !activityTypeCode.isBlank()) {
            dto.setActivityTypeCode(activityTypeCode.trim());
        }
        TimeLogDto created = timeLogService.create(dto);
        String projectName = projectService.findById(created.getProjectId())
                .map(ProjectDto::getName)
                .orElse("?");
        Map<String, Object> timeLogMap = new java.util.HashMap<>();
        timeLogMap.put("id", created.getId().toString());
        timeLogMap.put("projectId", created.getProjectId().toString());
        timeLogMap.put("projectName", projectName);
        timeLogMap.put("durationMinutes", created.getDurationMinutes());
        timeLogMap.put("note", created.getNote() != null ? created.getNote() : "");
        timeLogMap.put("billable", Boolean.TRUE.equals(created.getBillable()));
        timeLogMap.put("loggedAt", created.getLoggedAt().toString());
        if (created.getActivityTypeCode() != null) {
            timeLogMap.put("activityTypeCode", created.getActivityTypeCode());
            timeLogMap.put("activityTypeLabel", created.getActivityTypeLabel() != null ? created.getActivityTypeLabel() : "");
            timeLogMap.put("dailyRateCents", created.getDailyRateCents() != null ? created.getDailyRateCents() : 0);
        }
        String llm = "Logged " + created.getDurationMinutes() + " min on " + projectName + (created.getNote() != null && !created.getNote().isBlank() ? ": " + created.getNote() : ".");
        return toDualResult(llm, Map.of("time_log", timeLogMap));
    }

    private String executeGetRecentLogs(JsonNode args) {
        Integer limit = getInt(args, "limit");
        int limitVal = limit != null && limit > 0 ? Math.min(limit, 50) : 20;
        List<TimeLogDto> logs = timeLogService.findRecentLogs(limitVal);
        List<ProjectDto> projects = projectService.findAll();
        var projectMap = projects.stream().collect(Collectors.toMap(p -> p.getId().toString(), p -> p.getName()));

        List<Map<String, Object>> entries = new ArrayList<>();
        for (TimeLogDto log : logs) {
            Map<String, Object> e = new java.util.HashMap<>();
            e.put("id", log.getId().toString());
            e.put("projectId", log.getProjectId().toString());
            e.put("projectName", projectMap.getOrDefault(log.getProjectId().toString(), "?"));
            e.put("durationMinutes", log.getDurationMinutes());
            e.put("note", log.getNote() != null ? log.getNote() : "");
            e.put("billable", Boolean.TRUE.equals(log.getBillable()));
            e.put("loggedAt", log.getLoggedAt().toString());
            if (log.getActivityTypeCode() != null) {
                e.put("activityTypeCode", log.getActivityTypeCode());
                e.put("activityTypeLabel", log.getActivityTypeLabel() != null ? log.getActivityTypeLabel() : "");
                e.put("dailyRateCents", log.getDailyRateCents() != null ? log.getDailyRateCents() : 0);
            }
            entries.add(e);
        }
        String llm = "## Recent logs (" + entries.size() + ")\n"
                + entries.stream()
                .limit(15)
                .map(e -> "- " + e.get("projectName") + ": " + e.get("durationMinutes") + " min" + (e.get("loggedAt") != null ? " (" + e.get("loggedAt") + ")" : ""))
                .collect(Collectors.joining("\n"))
                + (entries.size() > 15 ? "\n... and " + (entries.size() - 15) + " more" : "");
        return toDualResult(llm, Map.of("time_logs", entries));
    }

    private String executeGetTimeLogsForPeriod(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (startStr == null || endStr == null) {
            return toDualResult("Error: start and end (ISO-8601) are required. Call get_current_datetime for bounds.", Map.of("error", "start and end (ISO-8601) are required"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        String projectIdStr = getText(args, "projectId");
        UUID projectId = projectIdStr != null && !projectIdStr.isBlank() ? resolveProjectId(projectIdStr) : null;

        List<TimeLogDto> logs = timeLogService.findLogsForPeriod(start, end, projectId);
        List<ProjectDto> projects = projectService.findAll();
        var projectMap = projects.stream().collect(Collectors.toMap(p -> p.getId().toString(), p -> p.getName()));

        List<Map<String, Object>> entries = new ArrayList<>();
        for (TimeLogDto log : logs) {
            Map<String, Object> e = new java.util.HashMap<>();
            e.put("id", log.getId().toString());
            e.put("projectId", log.getProjectId().toString());
            e.put("projectName", projectMap.getOrDefault(log.getProjectId().toString(), "?"));
            e.put("durationMinutes", log.getDurationMinutes());
            e.put("note", log.getNote() != null ? log.getNote() : "");
            e.put("billable", Boolean.TRUE.equals(log.getBillable()));
            e.put("loggedAt", log.getLoggedAt().toString());
            if (log.getActivityTypeCode() != null) {
                e.put("activityTypeCode", log.getActivityTypeCode());
                e.put("activityTypeLabel", log.getActivityTypeLabel() != null ? log.getActivityTypeLabel() : "");
                e.put("dailyRateCents", log.getDailyRateCents() != null ? log.getDailyRateCents() : 0);
            }
            entries.add(e);
        }
        String llm = "## Time logs (" + entries.size() + ") for period\n"
                + entries.stream()
                .limit(10)
                .map(e -> "- " + e.get("projectName") + ": " + e.get("durationMinutes") + " min")
                .collect(Collectors.joining("\n"))
                + (entries.size() > 10 ? "\n... and " + (entries.size() - 10) + " more" : "");
        return toDualResult(llm, Map.of("time_logs", entries));
    }

    private String executeSumTimeByProject(JsonNode args) {
        String projectIdStr = getText(args, "projectId");
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (projectIdStr == null || startStr == null || endStr == null) {
            return toDualResult("Error: projectId, start, and end are required.", Map.of("error", "projectId, start, and end are required"));
        }
        UUID projectId = resolveProjectId(projectIdStr);
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        int minutes = analyticsService.sumTimeByProject(projectId, start, end);
        double hours = Math.round(minutes / 6.0) / 10.0;
        Map<String, Object> data = Map.of("totalMinutes", minutes, "totalHours", hours);
        return toDualResult("Total: " + minutes + " min (" + hours + " h) for the project in the period.", data);
    }

    private String executeSumTimeForPeriod(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (startStr == null || endStr == null) {
            return toDualResult("Error: start and end (ISO-8601) are required. Call get_current_datetime for bounds.", Map.of("error", "start and end (ISO-8601) are required"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        int minutes = analyticsService.sumTimeForPeriod(start, end);
        double hours = Math.round(minutes / 6.0) / 10.0;
        Map<String, Object> data = Map.of("totalMinutes", minutes, "totalHours", hours);
        return toDualResult("Total: " + minutes + " min (" + hours + " h) in the period.", data);
    }

    private String executeSumBillableTimeForPeriod(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (startStr == null || endStr == null) {
            return toDualResult("Error: start and end (ISO-8601) are required.", Map.of("error", "start and end (ISO-8601) are required"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        int minutes = analyticsService.sumBillableTimeForPeriod(start, end);
        double hours = Math.round(minutes / 6.0) / 10.0;
        Map<String, Object> data = Map.of("totalMinutes", minutes, "totalHours", hours);
        return toDualResult("Billable: " + minutes + " min (" + hours + " h).", data);
    }

    private String executeSumNonBillableTimeForPeriod(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        if (startStr == null || endStr == null) {
            return toDualResult("Error: start and end (ISO-8601) are required.", Map.of("error", "start and end (ISO-8601) are required"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        int minutes = analyticsService.sumNonBillableTimeForPeriod(start, end);
        double hours = Math.round(minutes / 6.0) / 10.0;
        Map<String, Object> data = Map.of("totalMinutes", minutes, "totalHours", hours);
        return toDualResult("Non-billable: " + minutes + " min (" + hours + " h).", data);
    }

    private String executeGetCurrentDatetime() {
        ZonedDateTime now = ZonedDateTime.now(DEFAULT_ZONE);
        String startOfToday = AnalyticsService.startOfDay(DEFAULT_ZONE).toString();
        String endOfToday = AnalyticsService.endOfDay(DEFAULT_ZONE).toString();
        String startOfWeek = AnalyticsService.startOfWeek(DEFAULT_ZONE).toString();
        String endOfWeek = AnalyticsService.endOfWeek(DEFAULT_ZONE).toString();
        String startOfMonth = AnalyticsService.startOfMonth(DEFAULT_ZONE).toString();
        String endOfMonth = AnalyticsService.endOfMonth(DEFAULT_ZONE).toString();
        Map<String, Object> data = Map.of(
                "iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "timezone", DEFAULT_ZONE.getId(),
                "startOfToday", startOfToday,
                "endOfToday", endOfToday,
                "startOfWeek", startOfWeek,
                "endOfWeek", endOfWeek,
                "startOfMonth", startOfMonth,
                "endOfMonth", endOfMonth);
        String llm = "Current datetime (UTC): " + now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + ". Today: " + startOfToday + " to " + endOfToday
                + ". Week: " + startOfWeek + " to " + endOfWeek
                + ". Month: " + startOfMonth + " to " + endOfMonth + ".";
        return toDualResult(llm, data);
    }

    private String executeGetTimeAggregatedForChart(JsonNode args) {
        String startStr = getText(args, "start");
        String endStr = getText(args, "end");
        String groupBy = getText(args, "groupBy");
        if (startStr == null || endStr == null) {
            return toDualResult("Error: start and end (ISO-8601) are required. Call get_current_datetime first.", Map.of("error", "start and end (ISO-8601) are required"));
        }
        if (groupBy == null || groupBy.isBlank()) {
            return toDualResult("Error: groupBy is required. Use day_and_project, day_and_billable, project_only, or billable_vs_non_billable.", Map.of("error", "groupBy is required (day_and_project, day_and_billable, project_only, or billable_vs_non_billable)"));
        }
        Instant start = Instant.parse(startStr);
        Instant end = Instant.parse(endStr);
        var result = analyticsService.getTimeAggregatedForChart(start, end, groupBy, DEFAULT_ZONE);
        String llm = "Chart data ready. Pass categories and series to propose_chart.";
        return toDualResult(llm, result);
    }

    private String executeProposeChart(JsonNode args) {
        return toDualResult("Chart proposed for display.", Map.of("status", "ok"));
    }

    private String executeProposeEntries(JsonNode args) {
        return toDualResult("Entries proposed for display.", Map.of("status", "ok"));
    }

    private String executeUpdateTimeLog(JsonNode args) {
        String idStr = getText(args, "id");
        if (idStr == null || idStr.isBlank()) {
            return toDualResult("Error: id is required. Use entry id from get_recent_logs, get_time_logs_for_period, or [Context].", Map.of("error", "id is required"));
        }
        UUID id = UUID.fromString(idStr.trim());
        TimeLogDto patch = TimeLogDto.builder().id(id).build();
        Integer durationMinutes = getInt(args, "durationMinutes");
        if (durationMinutes != null && durationMinutes > 0) {
            patch.setDurationMinutes(durationMinutes);
        }
        String note = getText(args, "note");
        if (note != null) {
            patch.setNote(note);
        }
        String loggedAtStr = getText(args, "loggedAt");
        if (loggedAtStr != null && !loggedAtStr.isBlank()) {
            patch.setLoggedAt(Instant.parse(loggedAtStr));
        }
        String projectIdStr = getText(args, "projectId");
        if (projectIdStr != null && !projectIdStr.isBlank()) {
            patch.setProjectId(resolveProjectId(projectIdStr));
        }
        Boolean billableArg = getBoolean(args, "billable");
        if (billableArg != null) {
            patch.setBillable(billableArg);
        }
        String activityTypeCode = getText(args, "activityTypeCode");
        if (activityTypeCode != null) {
            patch.setActivityTypeCode(activityTypeCode.isBlank() ? "" : activityTypeCode.trim());
        }
        TimeLogDto updated = timeLogService.update(id, patch);
        String projectName = projectService.findById(updated.getProjectId())
                .map(ProjectDto::getName)
                .orElse("?");
        Map<String, Object> timeLogMap = new java.util.HashMap<>();
        timeLogMap.put("id", updated.getId().toString());
        timeLogMap.put("projectId", updated.getProjectId().toString());
        timeLogMap.put("projectName", projectName);
        timeLogMap.put("durationMinutes", updated.getDurationMinutes());
        timeLogMap.put("note", updated.getNote() != null ? updated.getNote() : "");
        timeLogMap.put("billable", Boolean.TRUE.equals(updated.getBillable()));
        timeLogMap.put("loggedAt", updated.getLoggedAt().toString());
        if (updated.getActivityTypeCode() != null) {
            timeLogMap.put("activityTypeCode", updated.getActivityTypeCode());
            timeLogMap.put("activityTypeLabel", updated.getActivityTypeLabel() != null ? updated.getActivityTypeLabel() : "");
            timeLogMap.put("dailyRateCents", updated.getDailyRateCents() != null ? updated.getDailyRateCents() : 0);
        }
        return toDualResult("Updated entry: " + projectName + ", " + updated.getDurationMinutes() + " min.", Map.of("time_log", timeLogMap));
    }

    private String executeDeleteTimeLog(JsonNode args) {
        String idStr = getText(args, "id");
        if (idStr == null || idStr.isBlank()) {
            return toDualResult("Error: id is required. Use entry id from get_recent_logs, get_time_logs_for_period, or [Context].", Map.of("error", "id is required"));
        }
        UUID id = UUID.fromString(idStr.trim());
        timeLogService.deleteById(id);
        return toDualResult("Entry deleted.", Map.of("status", "deleted"));
    }

    private String getText(JsonNode args, String key) {
        JsonNode n = args != null ? args.get(key) : null;
        return n != null && n.isTextual() ? n.asText() : (n != null ? n.asText() : null);
    }

    private Integer getInt(JsonNode args, String key) {
        JsonNode n = args != null ? args.get(key) : null;
        return n != null && n.isNumber() ? n.intValue() : null;
    }

    private Boolean getBoolean(JsonNode args, String key) {
        JsonNode n = args != null ? args.get(key) : null;
        if (n == null) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isTextual()) return Boolean.parseBoolean(n.asText());
        return null;
    }

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Resolves a project identifier to a UUID.
     * Accepts either a valid UUID string or a project name (resolved via search).
     */
    private UUID resolveProjectId(String projectIdOrName) {
        if (projectIdOrName == null || projectIdOrName.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        String trimmed = projectIdOrName.trim();
        if (UUID_PATTERN.matcher(trimmed).matches()) {
            return UUID.fromString(trimmed);
        }
        List<ProjectDto> matches = projectService.searchByName(trimmed);
        if (matches.isEmpty()) {
            List<ProjectDto> closeMatches = projectService.findCloseMatchesByName(trimmed, 1);
            if (closeMatches.size() == 1) {
                return closeMatches.get(0).getId();
            }
            throw new IllegalArgumentException("No project found matching '" + projectIdOrName + "'");
        }
        if (matches.size() > 1) {
            log.debug("Multiple projects match '{}', using first: {}", projectIdOrName, matches.get(0).getName());
        }
        return matches.get(0).getId();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"Serialization failed\"}";
        }
    }

    /**
     * Builds dual output for the LLM and the app: "llm" is sent to the model (readable summary);
     * "data" is the structured payload for the client (trace, display). See AGENT_DESIGN.md.
     */
    private String toDualResult(String llmContent, Object dataPayload) {
        try {
            Map<String, Object> out = new java.util.HashMap<>();
            out.put("llm", llmContent != null ? llmContent : "");
            out.put("data", dataPayload != null ? dataPayload : Map.of());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"llm\":\"Serialization failed\",\"data\":{}}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
