-- Replace CASCADE with RESTRICT to prevent accidental mass deletion of time logs.
-- Project deletion fails if the project has any associated time log entries.

ALTER TABLE time_logs DROP CONSTRAINT fk_time_logs_project;
ALTER TABLE time_logs ADD CONSTRAINT fk_time_logs_project
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE RESTRICT;
