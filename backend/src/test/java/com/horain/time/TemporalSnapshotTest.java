package com.horain.time;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalSnapshotTest {

    @Test
    void fromZoned_matchesAnalyticsBoundsForUtcMonday() {
        ZoneId utc = ZoneId.of("UTC");
        ZonedDateTime anchor = ZonedDateTime.parse("2025-03-10T12:00:00Z");
        TemporalSnapshot s = TemporalSnapshot.fromZoned(anchor, utc);

        assertThat(s.timezoneId()).isEqualTo("UTC");
        assertThat(s.iso()).contains("2025-03-10T12:00:00");
        assertThat(s.startOfToday()).isEqualTo("2025-03-10T00:00:00Z");
        assertThat(s.endOfToday()).isEqualTo("2025-03-11T00:00:00Z");
        assertThat(s.startOfWeek()).isEqualTo("2025-03-10T00:00:00Z");
        assertThat(s.endOfWeek()).isEqualTo("2025-03-17T00:00:00Z");
        assertThat(s.startOfMonth()).isEqualTo("2025-03-01T00:00:00Z");
        assertThat(s.endOfMonth()).isEqualTo("2025-04-01T00:00:00Z");
    }

    @Test
    void toLlmSummary_containsSameBoundsAsDataMap() {
        ZoneId utc = ZoneId.of("UTC");
        TemporalSnapshot s = TemporalSnapshot.fromZoned(ZonedDateTime.parse("2025-03-10T12:00:00Z"), utc);

        String llm = s.toLlmSummary();
        assertThat(llm).startsWith("Current datetime (UTC):");
        assertThat(llm).contains(s.startOfToday());
        assertThat(llm).contains(s.endOfMonth());

        assertThat(s.toDataMap()).containsEntry("timezone", "UTC");
        assertThat(s.toDataMap()).containsEntry("iso", s.iso());
        assertThat(s.toDataMap()).containsEntry("endOfMonth", s.endOfMonth());
    }
}
