package com.horain.observability.langsmith

import com.horain.agent.AgentTurnService
import com.horain.observability.AgentTraceSink
import com.horain.observability.FeedbackEvent
import com.horain.observability.ObservabilityProperties
import com.horain.observability.TurnCompletedEvent
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Sends completed turns and feedback to LangSmith (async, best-effort).
 */
class LangSmithTraceSink(
    private val props: ObservabilityProperties,
    private val apiClient: LangSmithApiClient,
    private val agentTurnService: AgentTurnService,
    private val pendingFeedback: LangSmithPendingFeedbackService,
    private val meterRegistry: MeterRegistry,
    private val executor: Executor
) : AgentTraceSink {

    private val log = LoggerFactory.getLogger(LangSmithTraceSink::class.java)

    override fun onTurnCompleted(event: TurnCompletedEvent) {
        if (props.langsmith.apiKey.isBlank()) {
            return
        }
        executor.execute {
            try {
                val runId = UUID.randomUUID()
                val returnedId = apiClient.createRun(runId, event)
                try {
                    apiClient.createReasoningChildRuns(returnedId, event)
                } catch (e: Exception) {
                    log.warn("LangSmith reasoning child runs failed: {}", e.message)
                }
                try {
                    apiClient.createToolChildRuns(returnedId, event)
                } catch (e: Exception) {
                    log.warn("LangSmith tool child runs failed: {}", e.message)
                }
                agentTurnService.updateExternalTraceId(event.turnId, returnedId)
                pendingFeedback.flushAfterRunCreated(event.turnId, returnedId)
                meterRegistry.counter(
                    "horain.observability.export",
                    "kind", "turn",
                    "result", "success"
                ).increment()
            } catch (e: Exception) {
                log.warn("LangSmith turn export failed: {}", e.message)
                meterRegistry.counter(
                    "horain.observability.export",
                    "kind", "turn",
                    "result", "failure"
                ).increment()
            }
        }
    }

    override fun onFeedback(event: FeedbackEvent) {
        if (props.langsmith.apiKey.isBlank()) {
            return
        }
        executor.execute {
            try {
                val turn = agentTurnService.findById(event.turnId) ?: return@execute
                val ext = turn.externalTraceId
                if (ext.isNullOrBlank()) {
                    pendingFeedback.enqueue(event.turnId, event)
                } else {
                    apiClient.postFeedback(ext, event)
                    meterRegistry.counter(
                        "horain.observability.export",
                        "kind", "feedback",
                        "result", "success"
                    ).increment()
                }
            } catch (e: Exception) {
                log.warn("LangSmith feedback export failed: {}", e.message)
                meterRegistry.counter(
                    "horain.observability.export",
                    "kind", "feedback",
                    "result", "failure"
                ).increment()
            }
        }
    }
}
