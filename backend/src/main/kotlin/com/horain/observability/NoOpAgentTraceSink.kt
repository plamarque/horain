package com.horain.observability

class NoOpAgentTraceSink : AgentTraceSink {
    override fun onTurnCompleted(event: TurnCompletedEvent) {
        // no-op
    }

    override fun onFeedback(event: FeedbackEvent) {
        // no-op
    }
}
