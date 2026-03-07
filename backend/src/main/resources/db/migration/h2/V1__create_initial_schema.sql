-- Initial schema for Horain (H2 in-memory for local dev).
-- Same structure as PostgreSQL; uses H2-compatible functions.

CREATE TABLE projects (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255)
);

CREATE TABLE time_logs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL,
    duration_minutes INTEGER NOT NULL,
    note VARCHAR(2000),
    logged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    CONSTRAINT fk_time_logs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT chk_duration_positive CHECK (duration_minutes > 0)
);

CREATE INDEX idx_time_logs_project_id ON time_logs(project_id);
CREATE INDEX idx_time_logs_logged_at ON time_logs(logged_at DESC);
CREATE INDEX idx_projects_name ON projects(name);
