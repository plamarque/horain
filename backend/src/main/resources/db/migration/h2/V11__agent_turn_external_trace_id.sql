-- Optional external observability run id (e.g. LangSmith run UUID).
ALTER TABLE agent_turn ADD COLUMN external_trace_id VARCHAR(255);

CREATE INDEX idx_agent_turn_external_trace_id ON agent_turn(external_trace_id);
