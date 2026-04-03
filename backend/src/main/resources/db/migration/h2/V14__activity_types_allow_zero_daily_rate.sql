-- Allow TJM 0 for non-billable activity natures (internal training, etc.).
ALTER TABLE activity_types DROP CONSTRAINT IF EXISTS activity_types_daily_rate_cents_check;
ALTER TABLE activity_types ADD CONSTRAINT activity_types_daily_rate_cents_check CHECK (daily_rate_cents >= 0);
