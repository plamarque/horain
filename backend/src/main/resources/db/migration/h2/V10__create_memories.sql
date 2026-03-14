-- Agent long-term memory: facts per user (preferences, disambiguation, etc.).
-- Same structure as PostgreSQL; H2-compatible syntax.

CREATE TABLE memories (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    memory_key VARCHAR(255) NOT NULL,
    memory_value TEXT,
    fact_text TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_memories_user_kind_key UNIQUE (user_id, kind, memory_key)
);

CREATE INDEX idx_memories_user_kind ON memories(user_id, kind);
CREATE INDEX idx_memories_user_expires ON memories(user_id, expires_at);
