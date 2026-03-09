-- Add created_at to time_logs (when the user created the entry).
-- logged_at remains the activity date (what to display).
-- For existing rows, created_at = logged_at (approximation).

ALTER TABLE time_logs ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
UPDATE time_logs SET created_at = logged_at;
ALTER TABLE time_logs ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE time_logs ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
