package com.horain.time

import com.horain.analytics.AnalyticsService
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.JvmRecord

/**
 * Server-side "now" and period bounds for the LLM (tool output and injected system block).
 * Built from a [ZonedDateTime] anchor so tests can use a fixed instant.
 */
@JvmRecord
data class TemporalSnapshot(
    val iso: String,
    val timezoneId: String,
    val startOfToday: String,
    val endOfToday: String,
    val startOfWeek: String,
    val endOfWeek: String,
    val startOfMonth: String,
    val endOfMonth: String
) {

    /**
     * Same wording as `get_current_datetime` tool LLM payload (parity for prompts).
     */
    fun toLlmSummary(): String =
        "Current datetime ($timezoneId): $iso" +
            ". Today: $startOfToday to $endOfToday" +
            ". Week: $startOfWeek to $endOfWeek" +
            ". Month: $startOfMonth to $endOfMonth."

    fun toDataMap(): Map<String, Any?> = mapOf(
        "iso" to iso,
        "timezone" to timezoneId,
        "startOfToday" to startOfToday,
        "endOfToday" to endOfToday,
        "startOfWeek" to startOfWeek,
        "endOfWeek" to endOfWeek,
        "startOfMonth" to startOfMonth,
        "endOfMonth" to endOfMonth
    )

    companion object {
        /**
         * Computes bounds for the calendar day containing [now] in [zone].
         */
        @JvmStatic
        fun fromZoned(now: ZonedDateTime, zone: ZoneId): TemporalSnapshot {
            val day = now.toLocalDate()
            val iso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val tz = zone.id
            val startOfToday = AnalyticsService.startOfDay(zone, day).toString()
            val endOfToday = AnalyticsService.endOfDay(zone, day).toString()
            val startOfWeek = AnalyticsService.startOfWeek(zone, day).toString()
            val endOfWeek = AnalyticsService.endOfWeek(zone, day).toString()
            val startOfMonth = AnalyticsService.startOfMonth(zone, day).toString()
            val endOfMonth = AnalyticsService.endOfMonth(zone, day).toString()
            return TemporalSnapshot(
                iso, tz, startOfToday, endOfToday, startOfWeek, endOfWeek, startOfMonth, endOfMonth
            )
        }
    }
}
