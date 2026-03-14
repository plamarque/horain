-- Agent long-term memory: facts per user (preferences, disambiguation, etc.).
-- Consolidation: upsert on (user_id, kind, memory_key). TTL via expires_at.

CREATE TABLE memories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    memory_key VARCHAR(255) NOT NULL,
    memory_value TEXT,
    fact_text TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_memories_user_kind_key UNIQUE (user_id, kind, memory_key)
);

CREATE INDEX idx_memories_user_kind ON memories(user_id, kind);
CREATE INDEX idx_memories_user_expires ON memories(user_id, expires_at);
