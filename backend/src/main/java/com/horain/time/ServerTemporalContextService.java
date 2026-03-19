package com.horain.time;

import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single source for server clock and period bounds used by the agent (tool + injected prompt block).
 */
@Service
public class ServerTemporalContextService {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    public ZoneId defaultZone() {
        return DEFAULT_ZONE;
    }

    public TemporalSnapshot snapshot() {
        return TemporalSnapshot.fromZoned(ZonedDateTime.now(DEFAULT_ZONE), DEFAULT_ZONE);
    }

    /**
     * Appended at the end of the system message so static instructions keep a longer shared prefix for caching.
     */
    public String buildPromptBlock() {
        return "\n\n## Current server time (refreshed each request)\n" + snapshot().toLlmSummary();
    }
}
