package com.horain.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.analytics.AnalyticsService
import com.horain.dto.ActivityTypeDto
import com.horain.dto.ProjectDto
import com.horain.dto.TimeLogDto
import com.horain.llm.ToolCallRequest
import com.horain.llm.ToolCallResult
import com.horain.service.ActivityTypeService
import com.horain.service.MemoryService
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import com.horain.time.ServerTemporalContextService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern

/**
 * Executes tool calls requested by the LLM.
 * Dispatches to ProjectService, TimeLogService, and AnalyticsService.
 */
@Service
class ToolExecutorService(
    private val projectService: ProjectService,
    private val timeLogService: TimeLogService,
    private val activityTypeService: ActivityTypeService,
    private val analyticsService: AnalyticsService,
    private val memoryService: MemoryService,
    private val objectMapper: ObjectMapper,
    private val serverTemporalContextService: ServerTemporalContextService
) {

    fun execute(request: ToolCallRequest): ToolCallResult {
        return try {
            val args = parseArgs(request.arguments)
            val result = when (request.name) {
                ToolRegistry.LIST_PROJECTS -> executeListProjects()
                ToolRegistry.SEARCH_PROJECT -> executeSearchProject(args)
                ToolRegistry.CREATE_PROJECT -> executeCreateProject(args)
                ToolRegistry.UPDATE_PROJECT -> executeUpdateProject(args)
                ToolRegistry.DELETE_PROJECT -> executeDeleteProject(args)
                ToolRegistry.LIST_ACTIVITY_TYPES -> executeListActivityTypes()
                ToolRegistry.CREATE_ACTIVITY_TYPE -> executeCreateActivityType(args)
                ToolRegistry.UPDATE_ACTIVITY_TYPE -> executeUpdateActivityType(args)
                ToolRegistry.DELETE_ACTIVITY_TYPE -> executeDeleteActivityType(args)
                ToolRegistry.CREATE_TIME_LOG -> executeCreateTimeLog(args)
                ToolRegistry.GET_RECENT_LOGS -> executeGetRecentLogs(args)
                ToolRegistry.GET_TIME_LOGS_FOR_PERIOD -> executeGetTimeLogsForPeriod(args)
                ToolRegistry.SEARCH_TIME_LOGS -> executeSearchTimeLogs(args)
                ToolRegistry.SUM_TIME_BY_PROJECT -> executeSumTimeByProject(args)
                ToolRegistry.SUM_TIME_FOR_PERIOD -> executeSumTimeForPeriod(args)
                ToolRegistry.SUM_BILLABLE_TIME_FOR_PERIOD -> executeSumBillableTimeForPeriod(args)
                ToolRegistry.SUM_NON_BILLABLE_TIME_FOR_PERIOD -> executeSumNonBillableTimeForPeriod(args)
                ToolRegistry.GET_CURRENT_DATETIME -> executeGetCurrentDatetime()
                ToolRegistry.GET_TIME_AGGREGATED_FOR_CHART -> executeGetTimeAggregatedForChart(args)
                ToolRegistry.PROPOSE_CHART -> executeProposeChart(args)
                ToolRegistry.PROPOSE_ENTRIES -> executeProposeEntries(args)
                ToolRegistry.UPDATE_TIME_LOG -> executeUpdateTimeLog(args)
                ToolRegistry.DELETE_TIME_LOG -> executeDeleteTimeLog(args)
                ToolRegistry.STORE_MEMORY -> executeStoreMemory(args)
                ToolRegistry.GET_MEMORIES -> executeGetMemories(args)
                ToolRegistry.FORGET_MEMORY -> executeForgetMemory(args)
                else -> toDualResult("Error: Unknown tool ${request.name}", mapOf("error" to "Unknown tool: ${request.name}"))
            }
            ToolCallResult(request.id, result)
        } catch (e: Exception) {
            log.warn("Tool execution failed: {} - {}", request.name, e.message)
            ToolCallResult(request.id, toDualResult("Error: ${e.message}", mapOf("error" to (e.message ?: "error"))))
        }
    }

    private fun parseArgs(arguments: String?): JsonNode {
        return try {
            if (arguments.isNullOrBlank()) {
                objectMapper.createObjectNode()
            } else {
                objectMapper.readTree(arguments)
            }
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
    }

    private fun executeListProjects(): String {
        val projects = projectService.findAll()
        val list = projects.map { p ->
            mapOf(
                "id" to p.id.toString(),
                "name" to p.name,
                "description" to (p.description ?: ""),
                "billable" to (p.billable == true)
            )
        }
        val llm = "## Projects (${list.size})\n" +
            list.joinToString("\n") { m ->
                "- ${m["name"]} (id: ${m["id"]})" + if (m["billable"] == true) ", billable" else ""
            }
        return toDualResult(llm, mapOf("projects" to list))
    }

    private fun executeSearchProject(args: JsonNode): String {
        val name = getText(args, "name")
        if (name.isNullOrBlank()) {
            return toDualResult(
                "Error: name is required. Provide the project name to search for.",
                mapOf("error" to "name is required")
            )
        }
        val matches = projectService.searchByName(name)
        val list = projectsToMaps(matches)
        val data = mutableMapOf<String, Any?>("matching_projects" to list)
        if (matches.isEmpty()) {
            val closeMatches = projectService.findCloseMatchesByName(name, CLOSE_MATCH_MAX)
            if (closeMatches.isNotEmpty()) {
                data["close_matches"] = projectsToMaps(closeMatches)
                val closeNames = projectsToMaps(closeMatches).joinToString(", ") { it["name"] as String }
                val llm = "No exact match for \"$name\". Close matches: $closeNames. Propose the first one and ask the user to confirm before logging."
                return toDualResult(llm, data)
            }
        }
        val llm = if (list.isEmpty()) {
            "No projects found for \"$name\"."
        } else {
            "## Matching projects (${list.size})\n" +
                list.joinToString("\n") { m -> "- ${m["name"]} (id: ${m["id"]})" }
        }
        return toDualResult(llm, data)
    }

    private fun projectsToMaps(projects: List<ProjectDto>): List<Map<String, Any?>> =
        projects.map { p ->
            mapOf(
                "id" to p.id.toString(),
                "name" to p.name,
                "description" to (p.description ?: ""),
                "billable" to (p.billable == true)
            )
        }

    private fun executeCreateProject(args: JsonNode): String {
        val name = getText(args, "name")
        if (name.isNullOrBlank()) {
            return toDualResult("Error: name is required. Provide the project name.", mapOf("error" to "name is required"))
        }
        val description = getText(args, "description")
        val billable = getBoolean(args, "billable")
        val dto = ProjectDto.builder()
            .name(name)
            .description(description)
            .billable(billable ?: true)
            .build()
        val created = projectService.create(dto)
        val projectMap = mapOf(
            "id" to created.id.toString(),
            "name" to created.name,
            "description" to (created.description ?: ""),
            "billable" to (created.billable == true)
        )
        val llm = "Created project \"${created.name}\" (id: ${created.id})."
        return toDualResult(llm, mapOf("project" to projectMap))
    }

    private fun executeUpdateProject(args: JsonNode): String {
        val idStr = getText(args, "id")
        if (idStr.isNullOrBlank()) {
            return toDualResult("Error: id is required. Use project UUID or name.", mapOf("error" to "id is required"))
        }
        val projectId = resolveProjectId(idStr)
        val patch = ProjectDto.builder().id(projectId).build()
        val name = getText(args, "name")
        if (!name.isNullOrBlank()) {
            patch.name = name.trim()
        }
        val description = getText(args, "description")
        if (description != null) {
            patch.description = description
        }
        val billableArg = getBoolean(args, "billable")
        if (billableArg != null) {
            patch.billable = billableArg
        }
        if (patch.name == null && patch.description == null && patch.billable == null) {
            return toDualResult(
                "Error: Provide at least one of name, description, or billable.",
                mapOf("error" to "At least one of name, description or billable must be provided")
            )
        }
        val updated = projectService.update(projectId, patch)
        val projectMap = mapOf(
            "id" to updated.id.toString(),
            "name" to updated.name,
            "description" to (updated.description ?: ""),
            "billable" to (updated.billable == true)
        )
        return toDualResult("Updated project \"${updated.name}\".", mapOf("project" to projectMap))
    }

    private fun executeDeleteProject(args: JsonNode): String {
        val idStr = getText(args, "id")
        if (idStr.isNullOrBlank()) {
            return toDualResult("Error: id is required. Use project UUID or name.", mapOf("error" to "id is required"))
        }
        val projectId = resolveProjectId(idStr)
        projectService.deleteById(projectId)
        return toDualResult("Project deleted.", mapOf("status" to "deleted"))
    }

    private fun executeListActivityTypes(): String {
        val types = activityTypeService.findAll()
        val list = types.map { a ->
            mapOf(
                "code" to a.code,
                "label" to (a.label ?: ""),
                "dailyRateCents" to (a.dailyRateCents ?: 0),
                "description" to (a.description ?: "")
            )
        }
        val llm = "## Activity types (${list.size})\n" +
            list.joinToString("\n") { m -> "- ${m["code"]}: ${m["label"]} (${m["dailyRateCents"]} cents/day)" }
        return toDualResult(llm, mapOf("activity_types" to list))
    }

    private fun executeCreateActivityType(args: JsonNode): String {
        val code = getText(args, "code")
        val label = getText(args, "label")
        val dailyRateCents = getInt(args, "dailyRateCents")
        if (code.isNullOrBlank()) {
            return toDualResult(
                "Error: code is required. Provide the activity type code (e.g. DEV, AI).",
                mapOf("error" to "code is required")
            )
        }
        if (dailyRateCents == null || dailyRateCents < 0) {
            return toDualResult(
                "Error: dailyRateCents must be a non-negative integer (0 for non-billable TJM, e.g. 40000 for 400 €).",
                mapOf("error" to "dailyRateCents must be a non-negative integer")
            )
        }
        val dto = ActivityTypeDto()
        dto.code = code.trim().uppercase()
        dto.label = label?.trim() ?: ""
        dto.dailyRateCents = dailyRateCents
        val description = getText(args, "description")
        if (description != null) dto.description = description
        val created = activityTypeService.create(dto)
        val at = mapOf(
            "code" to created.code,
            "label" to (created.label ?: ""),
            "dailyRateCents" to (created.dailyRateCents ?: 0),
            "description" to (created.description ?: "")
        )
        val euros = (created.dailyRateCents ?: 0) / 100
        val llm = "Created activity type ${created.code}: ${created.label} ($euros €/day)."
        return toDualResult(llm, mapOf("activity_type" to at))
    }

    private fun executeUpdateActivityType(args: JsonNode): String {
        val code = getText(args, "code")
        if (code.isNullOrBlank()) {
            return toDualResult(
                "Error: code is required. Use the activity type code to update.",
                mapOf("error" to "code is required")
            )
        }
        val patch = ActivityTypeDto()
        val label = getText(args, "label")
        if (label != null) patch.label = label
        val dailyRateCents = getInt(args, "dailyRateCents")
        if (dailyRateCents != null) patch.dailyRateCents = dailyRateCents
        val description = getText(args, "description")
        if (description != null) patch.description = description
        val updated = activityTypeService.update(code.trim().uppercase(), patch)
        val at = mapOf(
            "code" to updated.code,
            "label" to (updated.label ?: ""),
            "dailyRateCents" to (updated.dailyRateCents ?: 0),
            "description" to (updated.description ?: "")
        )
        return toDualResult("Updated activity type ${updated.code}.", mapOf("activity_type" to at))
    }

    private fun executeDeleteActivityType(args: JsonNode): String {
        val code = getText(args, "code")
        if (code.isNullOrBlank()) {
            return toDualResult(
                "Error: code is required. Use the activity type code to delete.",
                mapOf("error" to "code is required")
            )
        }
        val c = code.trim().uppercase()
        activityTypeService.deleteByCode(c)
        return toDualResult("Activity type $c deleted.", mapOf("status" to "deleted"))
    }

    private fun executeCreateTimeLog(args: JsonNode): String {
        val projectIdStr = getText(args, "projectId")
        val durationMinutes = getInt(args, "durationMinutes")
        if (projectIdStr.isNullOrBlank()) {
            return toDualResult(
                "Error: projectId is required. Get it from list_projects or search_project.",
                mapOf("error" to "projectId is required")
            )
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            return toDualResult(
                "Error: durationMinutes must be a positive integer.",
                mapOf("error" to "durationMinutes must be a positive integer")
            )
        }
        val projectId = resolveProjectId(projectIdStr)
        val note = getText(args, "note")
        val loggedAtStr = getText(args, "loggedAt")
        val loggedAt = if (!loggedAtStr.isNullOrBlank()) Instant.parse(loggedAtStr) else Instant.now()
        val billableArg = getBoolean(args, "billable")
        val activityTypeCode = getText(args, "activityTypeCode")
        val dto = TimeLogDto.builder()
            .projectId(projectId)
            .durationMinutes(durationMinutes)
            .note(note)
            .billable(billableArg)
            .loggedAt(loggedAt)
            .build()
        if (!activityTypeCode.isNullOrBlank()) {
            dto.activityTypeCode = activityTypeCode.trim()
        }
        val created = timeLogService.create(dto)
        val projectOpt = projectService.findById(created.projectId!!)
        val projectName = projectOpt.map { it.name }.orElse("?")
        val timeLogMap = mutableMapOf<String, Any?>(
            "id" to created.id.toString(),
            "projectId" to created.projectId.toString(),
            "projectName" to projectName,
            "durationMinutes" to created.durationMinutes,
            "note" to (created.note ?: ""),
            "billable" to (created.billable == true),
            "loggedAt" to created.loggedAt.toString()
        )
        projectOpt.orElse(null)?.cardColorIndex?.let { timeLogMap["projectCardColorIndex"] = it }
        if (created.activityTypeCode != null) {
            timeLogMap["activityTypeCode"] = created.activityTypeCode
            timeLogMap["activityTypeLabel"] = created.activityTypeLabel ?: ""
            timeLogMap["dailyRateCents"] = created.dailyRateCents ?: 0
        }
        val notePart = if (!created.note.isNullOrBlank()) ": ${created.note}" else "."
        val llm = "Logged ${created.durationMinutes} min on $projectName$notePart"
        return toDualResult(llm, mapOf("time_log" to timeLogMap))
    }

    private fun timeLogEntryMap(
        log: TimeLogDto,
        projectMap: Map<String, String?>,
        projectCardColorMap: Map<String, Int?>
    ): Map<String, Any?> {
        val pid = log.projectId.toString()
        val e = mutableMapOf<String, Any?>(
            "id" to log.id.toString(),
            "projectId" to pid,
            "projectName" to (projectMap[pid] ?: "?"),
            "durationMinutes" to log.durationMinutes,
            "note" to (log.note ?: ""),
            "billable" to (log.billable == true),
            "loggedAt" to log.loggedAt.toString()
        )
        projectCardColorMap[pid]?.let { e["projectCardColorIndex"] = it }
        if (log.activityTypeCode != null) {
            e["activityTypeCode"] = log.activityTypeCode
            e["activityTypeLabel"] = log.activityTypeLabel ?: ""
            e["dailyRateCents"] = log.dailyRateCents ?: 0
        }
        return e
    }

    private fun executeGetRecentLogs(args: JsonNode): String {
        val limit = getInt(args, "limit")
        val limitVal = if (limit != null && limit > 0) minOf(limit, 50) else 20
        val logs = timeLogService.findRecentLogs(limitVal)
        val projects = projectService.findAll()
        val projectMap = projects.associate { it.id.toString() to it.name }
        val projectCardColorMap = projects.associate { it.id.toString() to it.cardColorIndex }
        val entries = logs.map { timeLogEntryMap(it, projectMap, projectCardColorMap) }
        val llm = "## Recent logs (${entries.size})\n" +
            entries.take(15).joinToString("\n") { e ->
                "- ${e["projectName"]}: ${e["durationMinutes"]} min" +
                    (if (e["loggedAt"] != null) " (${e["loggedAt"]})" else "")
            } +
            if (entries.size > 15) "\n... and ${entries.size - 15} more" else ""
        return toDualResult(llm, mapOf("time_logs" to entries))
    }

    private fun executeGetTimeLogsForPeriod(args: JsonNode): String {
        val startStr = getText(args, "start")
        val endStr = getText(args, "end")
        if (startStr == null || endStr == null) {
            return toDualResult(
                "Error: start and end (ISO-8601) are required. Use the Current server time block in the system message or call get_current_datetime for bounds.",
                mapOf("error" to "start and end (ISO-8601) are required")
            )
        }
        val start = Instant.parse(startStr)
        val end = Instant.parse(endStr)
        val projectIdStr = getText(args, "projectId")
        val projectId = if (!projectIdStr.isNullOrBlank()) resolveProjectId(projectIdStr) else null
        val logs = timeLogService.findLogsForPeriod(start, end, projectId)
        val projects = projectService.findAll()
        val projectMap = projects.associate { it.id.toString() to it.name }
        val projectCardColorMap = projects.associate { it.id.toString() to it.cardColorIndex }
        val entries = logs.map { timeLogEntryMap(it, projectMap, projectCardColorMap) }
        val llm = "## Time logs (${entries.size}) for period\n" +
            entries.take(10).joinToString("\n") { e -> "- ${e["projectName"]}: ${e["durationMinutes"]} min" } +
            if (entries.size > 10) "\n... and ${entries.size - 10} more" else ""
        return toDualResult(llm, mapOf("time_logs" to entries))
    }

    private fun executeSearchTimeLogs(args: JsonNode): String {
        val query = getText(args, "query")
        if (query.isNullOrBlank()) {
            return toDualResult(
                "Error: query is required. Provide a keyword or phrase to search in notes and project names.",
                mapOf("error" to "query is required")
            )
        }
        val limit = getInt(args, "limit")
        val limitVal = if (limit != null && limit > 0) minOf(limit, 50) else 20
        val logs = timeLogService.findLogsByKeyword(query.trim(), limitVal)
        val projects = projectService.findAll()
        val projectMap = projects.associate { it.id.toString() to it.name }
        val projectCardColorMap = projects.associate { it.id.toString() to it.cardColorIndex }
        val entries = logs.map { timeLogEntryMap(it, projectMap, projectCardColorMap) }
        val llm = "## Search results for \"${query.trim()}\" (${entries.size})\n" +
            entries.take(10).joinToString("\n") { e ->
                val noteStr = e["note"] as? String ?: ""
                "- ${e["projectName"]}: ${e["durationMinutes"]} min" +
                    (if (noteStr.isNotBlank()) " — $noteStr" else "")
            } +
            if (entries.size > 10) "\n... and ${entries.size - 10} more" else ""
        return toDualResult(llm, mapOf("time_logs" to entries))
    }

    private fun executeSumTimeByProject(args: JsonNode): String {
        val projectIdStr = getText(args, "projectId")
        val startStr = getText(args, "start")
        val endStr = getText(args, "end")
        if (projectIdStr == null || startStr == null || endStr == null) {
            return toDualResult(
                "Error: projectId, start, and end are required.",
                mapOf("error" to "projectId, start, and end are required")
            )
        }
        val projectId = resolveProjectId(projectIdStr)
        val start = Instant.parse(startStr)
        val end = Instant.parse(endStr)
        val minutes = analyticsService.sumTimeByProject(projectId, start, end)
        val hours = kotlin.math.round(minutes / 6.0) / 10.0
        return toDualResult(
            "Total: $minutes min ($hours h) for the project in the period.",
            mapOf("totalMinutes" to minutes, "totalHours" to hours)
        )
    }

    private fun executeSumTimeForPeriod(args: JsonNode): String {
        val startStr = getText(args, "start")
        val endStr = getText(args, "end")
        if (startStr == null || endStr == null) {
            return toDualResult(
                "Error: start and end (ISO-8601) are required. Use the Current server time block in the system message or call get_current_datetime for bounds.",
                mapOf("error" to "start and end (ISO-8601) are required")
            )
        }
        val start = Instant.parse(startStr)
        val end = Instant.parse(endStr)
        val minutes = analyticsService.sumTimeForPeriod(start, end)
        val hours = kotlin.math.round(minutes / 6.0) / 10.0
        return toDualResult(
            "Total: $minutes min ($hours h) in the period.",
            mapOf("totalMinutes" to minutes, "totalHours" to hours)
        )
    }

    private fun executeSumBillableTimeForPeriod(args: JsonNode): String {
        val startStr = getText(args, "start")
        val endStr = getText(args, "end")
        if (startStr == null || endStr == null) {
            return toDualResult(
                "Error: start and end (ISO-8601) are required.",
                mapOf("error" to "start and end (ISO-8601) are required")
            )
        }
        val start = Instant.parse(startStr)
        val end = Instant.parse(endStr)
        val minutes = analyticsService.sumBillableTimeForPeriod(start, end)
        val hours = kotlin.math.round(minutes / 6.0) / 10.0
        return toDualResult(
            "Billable: $minutes min ($hours h).",
            mapOf("totalMinutes" to minutes, "totalHours" to hours)
        )
    }

    private fun executeSumNonBillableTimeForPeriod(args: JsonNode): String {
        val startStr = getText(args, "start")
        val endStr = getText(args, "end")
        if (startStr == null || endStr == null) {
            return toDualResult(
                "Error: start and end (ISO-8601) are required.",
                mapOf("error" to "start and end (ISO-8601) are required")
            )
        }
        val start = Instant.parse(startStr)
        val end = Instant.parse(endStr)
        val minutes = analyticsService.sumNonBillableTimeForPeriod(start, end)
        val hours = kotlin.math.round(minutes / 6.0) / 10.0
        return toDualResult(
            "Non-billable: $minutes min ($hours h).",
            mapOf("totalMinutes" to minutes, "totalHours" to hours)
        )
    }

    private fun executeGetCurrentDatetime(): String {
        val snapshot = serverTemporalContextService.snapshot()
        return toDualResult(snapshot.toLlmSummary(), snapshot.toDataMap())
    }

    private fun executeGetTimeAggregatedForChart(args: JsonNode): String {
        val startStr = getText(args, "start")
        val endStr = getText(args, "end")
        val groupBy = getText(args, "groupBy")
        if (startStr == null || endStr == null) {
            return toDualResult(
                "Error: start and end (ISO-8601) are required. Use the Current server time block or call get_current_datetime.",
                mapOf("error" to "start and end (ISO-8601) are required")
            )
        }
        if (groupBy.isNullOrBlank()) {
            return toDualResult(
                "Error: groupBy is required. Use day_and_project, day_and_billable, project_only, or billable_vs_non_billable.",
                mapOf("error" to "groupBy is required (day_and_project, day_and_billable, project_only, or billable_vs_non_billable)")
            )
        }
        val start = Instant.parse(startStr)
        val end = Instant.parse(endStr)
        val zone = serverTemporalContextService.defaultZone()
        val result = analyticsService.getTimeAggregatedForChart(start, end, groupBy, zone)
        val llm = try {
            "Chart data ready. Pass the following categories and series exactly to propose_chart (do not invent data): " +
                objectMapper.writeValueAsString(result)
        } catch (_: Exception) {
            "Chart data ready. Pass categories and series from the data payload to propose_chart."
        }
        return toDualResult(llm, result)
    }

    private fun executeProposeChart(args: JsonNode): String =
        toDualResult("Chart proposed for display.", mapOf("status" to "ok"))

    private fun executeProposeEntries(args: JsonNode): String =
        toDualResult("Entries proposed for display.", mapOf("status" to "ok"))

    private fun executeUpdateTimeLog(args: JsonNode): String {
        val idStr = getText(args, "id")
        if (idStr.isNullOrBlank()) {
            return toDualResult(
                "Error: id is required. Use entry id from get_recent_logs, get_time_logs_for_period, search_time_logs, or [Context].",
                mapOf("error" to "id is required")
            )
        }
        val id = UUID.fromString(idStr.trim())
        val patch = TimeLogDto.builder().id(id).build()
        val durationMinutes = getInt(args, "durationMinutes")
        if (durationMinutes != null && durationMinutes > 0) {
            patch.durationMinutes = durationMinutes
        }
        val note = getText(args, "note")
        if (note != null) {
            patch.note = note
        }
        val loggedAtStr = getText(args, "loggedAt")
        if (!loggedAtStr.isNullOrBlank()) {
            patch.loggedAt = Instant.parse(loggedAtStr)
        }
        val projectIdStr = getText(args, "projectId")
        if (!projectIdStr.isNullOrBlank()) {
            patch.projectId = resolveProjectId(projectIdStr)
        }
        val billableArg = getBoolean(args, "billable")
        if (billableArg != null) {
            patch.billable = billableArg
        }
        val activityTypeCode = getText(args, "activityTypeCode")
        if (activityTypeCode != null) {
            patch.activityTypeCode = if (activityTypeCode.isBlank()) "" else activityTypeCode.trim()
        }
        val updated = timeLogService.update(id, patch)
        val projectOpt = projectService.findById(updated.projectId!!)
        val projectName = projectOpt.map { it.name }.orElse("?")
        val timeLogMap = mutableMapOf<String, Any?>(
            "id" to updated.id.toString(),
            "projectId" to updated.projectId.toString(),
            "projectName" to projectName,
            "durationMinutes" to updated.durationMinutes,
            "note" to (updated.note ?: ""),
            "billable" to (updated.billable == true),
            "loggedAt" to updated.loggedAt.toString()
        )
        projectOpt.orElse(null)?.cardColorIndex?.let { timeLogMap["projectCardColorIndex"] = it }
        if (updated.activityTypeCode != null) {
            timeLogMap["activityTypeCode"] = updated.activityTypeCode
            timeLogMap["activityTypeLabel"] = updated.activityTypeLabel ?: ""
            timeLogMap["dailyRateCents"] = updated.dailyRateCents ?: 0
        }
        return toDualResult(
            "Updated entry: $projectName, ${updated.durationMinutes} min.",
            mapOf("time_log" to timeLogMap)
        )
    }

    private fun executeDeleteTimeLog(args: JsonNode): String {
        val idStr = getText(args, "id")
        if (idStr.isNullOrBlank()) {
            return toDualResult(
                "Error: id is required. Use entry id from get_recent_logs, get_time_logs_for_period, search_time_logs, or [Context].",
                mapOf("error" to "id is required")
            )
        }
        val id = UUID.fromString(idStr.trim())
        timeLogService.deleteById(id)
        return toDualResult("Entry deleted.", mapOf("status" to "deleted"))
    }

    private fun executeStoreMemory(args: JsonNode): String {
        val kind = getText(args, "kind")
        val memoryKey = getText(args, "memoryKey")
        val value = getText(args, "value")
        val factText = getText(args, "factText")
        val ttlSeconds = getInt(args, "ttlSeconds")
        if (kind.isNullOrBlank()) {
            return toDualResult(
                "Error: kind is required (project_disambiguation, typo, default_project, preference, explicit_fact).",
                mapOf("error" to "kind is required")
            )
        }
        if (memoryKey.isNullOrBlank()) {
            return toDualResult("Error: memoryKey is required.", mapOf("error" to "memoryKey is required"))
        }
        if (factText.isNullOrBlank()) {
            return toDualResult("Error: factText is required.", mapOf("error" to "factText is required"))
        }
        val ttl = if (ttlSeconds != null && ttlSeconds > 0) ttlSeconds.toLong() else null
        val userId = memoryService.getDefaultUserId()
        memoryService.save(userId, kind.trim(), memoryKey.trim(), value ?: "", factText.trim(), ttl)
        return toDualResult(
            "Memory stored: $kind / $memoryKey.",
            mapOf("status" to "stored", "kind" to kind, "memoryKey" to memoryKey)
        )
    }

    private fun executeGetMemories(args: JsonNode): String {
        val kind = getText(args, "kind")
        val userId = memoryService.getDefaultUserId()
        val memories = if (!kind.isNullOrBlank()) {
            memoryService.findActiveByUserIdAndKind(userId, kind)
        } else {
            memoryService.findActiveByUserId(userId)
        }
        if (memories.isEmpty()) {
            val suffix = if (!kind.isNullOrBlank()) " (kind=$kind)" else ""
            return toDualResult("No stored memories.$suffix", mapOf("memories" to emptyList<Any>()))
        }
        val llm = StringBuilder("## Stored memories (${memories.size})\n")
        val list = mutableListOf<Map<String, Any?>>()
        for (m in memories) {
            llm.append("- ").append(m.factText).append("\n")
            list.add(
                mapOf(
                    "kind" to m.kind,
                    "memoryKey" to m.memoryKey,
                    "factText" to (m.factText ?: "")
                )
            )
        }
        return toDualResult(llm.toString().trim(), mapOf("memories" to list))
    }

    private fun executeForgetMemory(args: JsonNode): String {
        val kind = getText(args, "kind")
        val memoryKey = getText(args, "memoryKey")
        if (kind.isNullOrBlank()) {
            return toDualResult(
                "Error: kind is required to forget memories.",
                mapOf("error" to "kind is required")
            )
        }
        val userId = memoryService.getDefaultUserId()
        memoryService.forget(userId, kind.trim(), memoryKey)
        return if (!memoryKey.isNullOrBlank()) {
            toDualResult(
                "Forgot memory: $kind / $memoryKey.",
                mapOf("status" to "forgotten", "kind" to kind, "memoryKey" to memoryKey)
            )
        } else {
            toDualResult(
                "Forgot all memories of kind: $kind.",
                mapOf("status" to "forgotten", "kind" to kind)
            )
        }
    }

    private fun getText(args: JsonNode?, key: String): String? {
        val n = args?.get(key) ?: return null
        return if (n.isTextual) n.asText() else n.asText()
    }

    private fun getInt(args: JsonNode?, key: String): Int? {
        val n = args?.get(key) ?: return null
        return if (n.isNumber) n.asInt() else null
    }

    private fun getBoolean(args: JsonNode?, key: String): Boolean? {
        val n = args?.get(key) ?: return null
        return when {
            n.isBoolean -> n.asBoolean()
            n.isTextual -> n.asText().toBoolean()
            else -> null
        }
    }

    private fun resolveProjectId(projectIdOrName: String): UUID {
        if (projectIdOrName.isBlank()) {
            throw IllegalArgumentException("projectId is required")
        }
        val trimmed = projectIdOrName.trim()
        if (UUID_PATTERN.matcher(trimmed).matches()) {
            return UUID.fromString(trimmed)
        }
        val matches = projectService.searchByName(trimmed)
        if (matches.isEmpty()) {
            val closeMatches = projectService.findCloseMatchesByName(trimmed, 1)
            if (closeMatches.size == 1) {
                return closeMatches[0].id!!
            }
            throw IllegalArgumentException("No project found matching '$projectIdOrName'")
        }
        if (matches.size > 1) {
            log.debug("Multiple projects match '{}', using first: {}", projectIdOrName, matches[0].name)
        }
        return matches[0].id!!
    }

    /**
     * Builds dual output for the LLM and the app: "llm" is sent to the model (readable summary);
     * "data" is the structured payload for the client (trace, display). See AGENT_DESIGN.md.
     */
    private fun toDualResult(llmContent: String?, dataPayload: Any?): String {
        return try {
            val out = mutableMapOf<String, Any?>(
                "llm" to (llmContent ?: ""),
                "data" to (dataPayload ?: emptyMap<String, Any?>())
            )
            objectMapper.writeValueAsString(out)
        } catch (_: Exception) {
            """{"llm":"Serialization failed","data":{}}"""
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ToolExecutorService::class.java)
        private const val CLOSE_MATCH_MAX = 3
        private val UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
