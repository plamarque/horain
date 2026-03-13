-- Activity types (natures) with daily rate (TJM, 8h). Optional per time_log.
CREATE TABLE activity_types (
    code VARCHAR(50) PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    daily_rate_cents INTEGER NOT NULL CHECK (daily_rate_cents > 0)
);

INSERT INTO activity_types (code, label, daily_rate_cents) VALUES
    ('DEV', 'Développement', 40000),
    ('AI', 'Expertise IA', 100000),
    ('MARK', 'Marketing', 700000);

ALTER TABLE time_logs ADD COLUMN activity_type_code VARCHAR(50);
ALTER TABLE time_logs ADD CONSTRAINT fk_time_logs_activity_type
    FOREIGN KEY (activity_type_code) REFERENCES activity_types(code) ON DELETE SET NULL;

CREATE INDEX idx_time_logs_activity_type_code ON time_logs(activity_type_code);
