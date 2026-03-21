package com.horain.analytics

import com.horain.dto.TimeLogDto
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Analytics service for time log queries.
 * Provides total duration by project, by period, recent activity, and summaries.
 */
@Service
class AnalyticsService(
    private val timeLogService: TimeLogService,
    private val projectService: ProjectService
) {

    fun sumTimeForPeriod(start: Instant, end: Instant): Int =
        timeLogService.sumDurationForPeriod(start, end)

    fun sumTimeByProject(projectId: UUID, start: Instant, end: Instant): Int =
        timeLogService.sumDurationByProject(projectId, start, end)

    fun sumBillableTimeForPeriod(start: Instant, end: Instant): Int =
        timeLogService.sumDurationForPeriodByBillable(start, end, true)

    fun sumNonBillableTimeForPeriod(start: Instant, end: Instant): Int =
        timeLogService.sumDurationForPeriodByBillable(start, end, false)

    fun getRecentLogs(limit: Int): List<TimeLogDto> =
        timeLogService.findRecentLogs(limit)

    fun getTimeLogsForPeriod(start: Instant, end: Instant, projectId: UUID?): List<TimeLogDto> =
        timeLogService.findLogsForPeriod(start, end, projectId)

    /**
     * Aggregates time logs for chart display.
     *
     * @param groupBy "day_and_project" for stacked bar (days on x-axis, projects as series),
     *                "project_only" for pie (distribution by project),
     *                "billable_vs_non_billable" for pie or bar (Facturé vs Non facturé)
     * @return Map with "categories" (List of strings) and "series" (List of {name, data})
     *         Values in data are hours (decimal).
     */
    fun getTimeAggregatedForChart(
        start: Instant,
        end: Instant,
        groupBy: String,
        zone: ZoneId
    ): Map<String, Any?> {
        val logs = timeLogService.findLogsForPeriod(start, end, null)
        val projectNames = projectService.findAll().associate { it.id!! to it.name!! }

        if (groupBy == "billable_vs_non_billable") {
            val billableMinutes = timeLogService.sumDurationForPeriodByBillable(start, end, true)
            val nonBillableMinutes = timeLogService.sumDurationForPeriodByBillable(start, end, false)
            val categories = listOf("Facturé", "Non facturé")
            val data = listOf(
                roundHours(billableMinutes),
                roundHours(nonBillableMinutes)
            )
            return mapOf(
                "categories" to categories,
                "series" to listOf(mapOf("name" to "Heures", "data" to data))
            )
        }

        if (groupBy == "day_and_billable") {
            val dayToBillableNonBillable = sortedMapOf<LocalDate, IntArray>()
            for (log in logs) {
                val day = log.loggedAt!!.atZone(zone).toLocalDate()
                val pair = dayToBillableNonBillable.getOrPut(day) { intArrayOf(0, 0) }
                if (log.billable == true) {
                    pair[0] += log.durationMinutes!!
                } else {
                    pair[1] += log.durationMinutes!!
                }
            }
            val days = dayToBillableNonBillable.keys.toList()
            val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)
            val categories = days.map { d -> d.format(dayFormat) }
            val billableHours = mutableListOf<Double>()
            val nonBillableHours = mutableListOf<Double>()
            for (d in days) {
                val pair = dayToBillableNonBillable[d]!!
                billableHours.add(roundHours(pair[0]))
                nonBillableHours.add(roundHours(pair[1]))
            }
            val series: List<Map<String, Any?>> = listOf(
                mapOf("name" to "Facturables", "data" to billableHours),
                mapOf("name" to "Non Facturables", "data" to nonBillableHours)
            )
            return mapOf("categories" to categories, "series" to series)
        }

        if (groupBy == "project_only") {
            val projectToMinutes = linkedMapOf<UUID, Int>()
            for (log in logs) {
                projectToMinutes.merge(log.projectId!!, log.durationMinutes!!, Int::plus)
            }
            val categories = mutableListOf<String>()
            val data = mutableListOf<Double>()
            for ((pid, minutes) in projectToMinutes) {
                categories.add(projectNames[pid] ?: "?")
                data.add(roundHours(minutes))
            }
            return mapOf(
                "categories" to categories,
                "series" to listOf(mapOf("name" to "Heures", "data" to data))
            )
        }

        if (groupBy == "day_and_project") {
            val dayToProjectToMinutes = sortedMapOf<LocalDate, MutableMap<UUID, Int>>()
            for (log in logs) {
                val day = log.loggedAt!!.atZone(zone).toLocalDate()
                val byProject = dayToProjectToMinutes.getOrPut(day) { mutableMapOf() }
                byProject.merge(log.projectId!!, log.durationMinutes!!, Int::plus)
            }
            val days = dayToProjectToMinutes.keys.toList()
            val allProjects = logs.map { it.projectId!! }.toSet()
            val projectsOrdered = allProjects.sortedWith { a, b ->
                val totalA = logs.filter { it.projectId == a }.sumOf { it.durationMinutes!! }
                val totalB = logs.filter { it.projectId == b }.sumOf { it.durationMinutes!! }
                totalB.compareTo(totalA)
            }
            val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)
            val categories = days.map { d -> d.format(dayFormat) }
            val series = mutableListOf<Map<String, Any?>>()
            for (projectId in projectsOrdered) {
                val data = mutableListOf<Double>()
                for (day in days) {
                    val minutes = dayToProjectToMinutes[day]?.get(projectId) ?: 0
                    data.add(roundHours(minutes))
                }
                series.add(mapOf("name" to (projectNames[projectId] ?: "?"), "data" to data))
            }
            return mapOf("categories" to categories, "series" to series)
        }

        return mapOf("categories" to emptyList<String>(), "series" to emptyList<Any>())
    }

    companion object {
        private fun roundHours(minutes: Int): Double =
            (minutes / 6.0).roundToInt() / 10.0

        /**
         * Start of today in the given timezone (inclusive).
         */
        @JvmStatic
        fun startOfDay(zone: ZoneId): Instant =
            startOfDay(zone, LocalDate.now(zone))

        /**
         * Start of the given calendar day in the timezone (inclusive).
         */
        @JvmStatic
        fun startOfDay(zone: ZoneId, day: LocalDate): Instant =
            day.atStartOfDay(zone).toInstant()

        /**
         * End of today in the given timezone (exclusive, i.e. start of next day).
         */
        @JvmStatic
        fun endOfDay(zone: ZoneId): Instant =
            endOfDay(zone, LocalDate.now(zone))

        /**
         * End of the given calendar day in the timezone (exclusive).
         */
        @JvmStatic
        fun endOfDay(zone: ZoneId, day: LocalDate): Instant =
            day.plusDays(1).atStartOfDay(zone).toInstant()

        /**
         * Start of the week (Monday) in the given timezone.
         */
        @JvmStatic
        fun startOfWeek(zone: ZoneId): Instant =
            startOfWeek(zone, LocalDate.now(zone))

        /**
         * Start of the ISO week (Monday) containing [day] in the given timezone.
         */
        @JvmStatic
        fun startOfWeek(zone: ZoneId, day: LocalDate): Instant {
            val monday = day.with(DayOfWeek.MONDAY)
            return monday.atStartOfDay(zone).toInstant()
        }

        /**
         * End of the week (Sunday 23:59:59.999) - exclusive bound for next week start.
         */
        @JvmStatic
        fun endOfWeek(zone: ZoneId): Instant =
            endOfWeek(zone, LocalDate.now(zone))

        /**
         * End of the ISO week (exclusive) containing [day].
         */
        @JvmStatic
        fun endOfWeek(zone: ZoneId, day: LocalDate): Instant =
            startOfWeek(zone, day).plus(java.time.Duration.ofDays(7))

        /**
         * Start of the month in the given timezone.
         */
        @JvmStatic
        fun startOfMonth(zone: ZoneId): Instant =
            startOfMonth(zone, LocalDate.now(zone))

        /**
         * Start of the month containing [day].
         */
        @JvmStatic
        fun startOfMonth(zone: ZoneId, day: LocalDate): Instant {
            val first = day.withDayOfMonth(1)
            return first.atStartOfDay(zone).toInstant()
        }

        /**
         * End of the month (exclusive).
         */
        @JvmStatic
        fun endOfMonth(zone: ZoneId): Instant =
            endOfMonth(zone, LocalDate.now(zone))

        /**
         * End of the month containing [day] (exclusive).
         */
        @JvmStatic
        fun endOfMonth(zone: ZoneId, day: LocalDate): Instant {
            val first = day.withDayOfMonth(1)
            return first.plusMonths(1).atStartOfDay(zone).toInstant()
        }
    }
}
