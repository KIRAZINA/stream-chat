-- Phase 3: Production-ready features

-- 1. Audit logs for compliance and security
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    actor_username VARCHAR(50) NOT NULL,
    stream_id BIGINT,
    target_user_id BIGINT,
    target_username VARCHAR(50),
    action_type VARCHAR(50) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_stream ON audit_logs(stream_id);

-- 2. Pinned messages for announcements
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN DEFAULT FALSE;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS pinned_by BIGINT;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_pinned_by
    FOREIGN KEY (pinned_by) REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_chat_messages_pinned
    ON chat_messages(stream_id, is_pinned)
    WHERE is_pinned = TRUE;

-- 3. Message deduplication idempotency key
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) UNIQUE;

CREATE INDEX IF NOT EXISTS idx_chat_messages_idempotency
    ON chat_messages(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 4. User reputation score for trust levels
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reputation_score INTEGER DEFAULT 0;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS trust_level VARCHAR(20) DEFAULT 'NEW';

CREATE INDEX IF NOT EXISTS idx_users_reputation
    ON users(reputation_score DESC);

-- 5. Message delivery tracking for replay
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS redis_sequence_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_chat_messages_sequence
    ON chat_messages(stream_id, redis_sequence_id)
    WHERE redis_sequence_id IS NOT NULL;
