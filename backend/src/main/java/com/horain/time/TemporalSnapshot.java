package com.horain.time;

import com.horain.analytics.AnalyticsService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Server-side "now" and period bounds for the LLM (tool output and injected system block).
 * Built from a {@link ZonedDateTime} anchor so tests can use a fixed instant.
 */
public record TemporalSnapshot(
        String iso,
        String timezoneId,
        String startOfToday,
        String endOfToday,
        String startOfWeek,
        String endOfWeek,
        String startOfMonth,
        String endOfMonth
) {

    /**
     * Computes bounds for the calendar day containing {@code now} in {@code zone}.
     */
    public static TemporalSnapshot fromZoned(ZonedDateTime now, ZoneId zone) {
        LocalDate day = now.toLocalDate();
        String iso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String tz = zone.getId();
        String startOfToday = AnalyticsService.startOfDay(zone, day).toString();
        String endOfToday = AnalyticsService.endOfDay(zone, day).toString();
        String startOfWeek = AnalyticsService.startOfWeek(zone, day).toString();
        String endOfWeek = AnalyticsService.endOfWeek(zone, day).toString();
        String startOfMonth = AnalyticsService.startOfMonth(zone, day).toString();
        String endOfMonth = AnalyticsService.endOfMonth(zone, day).toString();
        return new TemporalSnapshot(iso, tz, startOfToday, endOfToday, startOfWeek, endOfWeek, startOfMonth, endOfMonth);
    }

    /**
     * Same wording as {@code get_current_datetime} tool LLM payload (parity for prompts).
     */
    public String toLlmSummary() {
        return "Current datetime (" + timezoneId + "): " + iso
                + ". Today: " + startOfToday + " to " + endOfToday
                + ". Week: " + startOfWeek + " to " + endOfWeek
                + ". Month: " + startOfMonth + " to " + endOfMonth + ".";
    }

    public Map<String, Object> toDataMap() {
        return Map.of(
                "iso", iso,
                "timezone", timezoneId,
                "startOfToday", startOfToday,
                "endOfToday", endOfToday,
                "startOfWeek", startOfWeek,
                "endOfWeek", endOfWeek,
                "startOfMonth", startOfMonth,
                "endOfMonth", endOfMonth);
    }
}
