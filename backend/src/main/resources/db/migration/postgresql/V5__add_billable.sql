-- Add billable flag: projects default to billable; time entries inherit from project at creation and can be overridden per entry.
ALTER TABLE projects ADD COLUMN billable BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE time_logs ADD COLUMN billable BOOLEAN NOT NULL DEFAULT true;
