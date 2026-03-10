-- Enforce project name uniqueness (business rule: no two projects with the same name).
ALTER TABLE projects ADD CONSTRAINT uq_projects_name UNIQUE (name);
