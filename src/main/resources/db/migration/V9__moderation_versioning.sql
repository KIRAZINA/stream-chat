-- 2.4 Moderation versioning: optimistic-lock columns for idempotent upserts.
-- Existing rows are backfilled to 0 so Hibernate's @Version update semantics
-- hold for pre-existing data. New inserts get 0 automatically from Hibernate.
ALTER TABLE banned_users ADD COLUMN version BIGINT;
ALTER TABLE timed_out_users ADD COLUMN version BIGINT;
UPDATE banned_users SET version = 0 WHERE version IS NULL;
UPDATE timed_out_users SET version = 0 WHERE version IS NULL;