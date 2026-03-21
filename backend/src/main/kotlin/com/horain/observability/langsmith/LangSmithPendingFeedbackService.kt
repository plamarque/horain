package com.horain.observability.langsmith

import com.horain.observability.FeedbackEvent
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Buffers feedback until the LangSmith run id is persisted (async export may finish after the user taps feedback).
 */
class LangSmithPendingFeedbackService(
    private val apiClient: LangSmithApiClient,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(LangSmithPendingFeedbackService::class.java)
    private val pending = ConcurrentHashMap<UUID, MutableList<FeedbackEvent>>()

    fun enqueue(turnId: UUID, event: FeedbackEvent) {
        pending.computeIfAbsent(turnId) { mutableListOf() }.add(event)
        if (pending.size > 2000) {
            log.warn("LangSmith pending feedback map is large ({}); some keys may be stale", pending.size)
        }
    }

    fun flushAfterRunCreated(turnId: UUID, runId: String) {
        val list = pending.remove(turnId) ?: return
        for (e in list) {
            try {
                apiClient.postFeedback(runId, e)
                meterRegistry.counter(
                    "horain.observability.export",
                    "kind", "feedback",
                    "result", "success"
                ).increment()
            } catch (ex: Exception) {
                log.warn("LangSmith flush feedback failed: {}", ex.message)
                meterRegistry.counter(
                    "horain.observability.export",
                    "kind", "feedback",
                    "result", "failure"
                ).increment()
            }
        }
    }
}
