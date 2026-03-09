-- Replace CASCADE with RESTRICT to prevent accidental mass deletion of time logs.
-- Project deletion fails if the project has any associated time log entries.

ALTER TABLE time_logs DROP CONSTRAINT IF EXISTS time_logs_project_id_fkey;
ALTER TABLE time_logs ADD CONSTRAINT time_logs_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE RESTRICT;
