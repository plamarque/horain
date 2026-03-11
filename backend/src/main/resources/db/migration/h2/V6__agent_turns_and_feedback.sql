-- Agent conversation trace, user feedback, and eval backlog for improvement pipeline.

CREATE TABLE agent_turn (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    conversation_id UUID NOT NULL,
    turn_index INTEGER NOT NULL,
    user_message TEXT NOT NULL,
    assistant_message TEXT,
    tool_calls_json TEXT,
    tool_results_json TEXT,
    ui_payload_json TEXT,
    system_prompt_version VARCHAR(50),
    model VARCHAR(255),
    status VARCHAR(50),
    history_snapshot_json TEXT,
    context_entries_json TEXT,
    latency_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_turn_conversation_turn ON agent_turn(conversation_id, turn_index);
CREATE INDEX idx_agent_turn_created_at ON agent_turn(created_at DESC);

CREATE TABLE agent_feedback (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    turn_id UUID NOT NULL,
    rating VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_feedback_turn FOREIGN KEY (turn_id) REFERENCES agent_turn(id) ON DELETE CASCADE,
    CONSTRAINT uq_agent_feedback_turn UNIQUE (turn_id)
);

CREATE INDEX idx_agent_feedback_turn_id ON agent_feedback(turn_id);

CREATE TABLE eval_backlog (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    turn_id UUID NOT NULL,
    eval_family VARCHAR(100),
    expected_behavior TEXT,
    assertion_type VARCHAR(50),
    severity VARCHAR(20),
    status VARCHAR(50),
    notes TEXT,
    promoted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_backlog_turn FOREIGN KEY (turn_id) REFERENCES agent_turn(id) ON DELETE CASCADE
);

CREATE INDEX idx_eval_backlog_status ON eval_backlog(status);
CREATE INDEX idx_eval_backlog_turn_id ON eval_backlog(turn_id);
