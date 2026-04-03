-- Hibernate maps Kotlin/Java Int to INTEGER; V12 used SMALLINT and fails schema validation.
ALTER TABLE projects
    ALTER COLUMN card_color_index TYPE INTEGER USING card_color_index::integer;
