package com.horain.time

import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.jvm.JvmField

/**
 * Single source for server clock and period bounds used by the agent (tool + injected prompt block).
 */
@Service
class ServerTemporalContextService {

    fun defaultZone(): ZoneId = DEFAULT_ZONE

    fun snapshot(): TemporalSnapshot =
        TemporalSnapshot.fromZoned(ZonedDateTime.now(DEFAULT_ZONE), DEFAULT_ZONE)

    /**
     * Appended at the end of the system message so static instructions keep a longer shared prefix for caching.
     */
    fun buildPromptBlock(): String =
        "\n\n## Current server time (refreshed each request)\n" + snapshot().toLlmSummary()

    companion object {
        @JvmField
        val DEFAULT_ZONE: ZoneId = ZoneId.of("UTC")
    }
}
