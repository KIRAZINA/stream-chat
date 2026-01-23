-- Moderation logs
CREATE TABLE moderation_logs (
                                 id BIGSERIAL PRIMARY KEY,
                                 stream_id BIGINT NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
                                 moderator_id BIGINT NOT NULL REFERENCES users(id),
                                 target_user_id BIGINT NOT NULL REFERENCES users(id),
                                 action_type VARCHAR(20) NOT NULL,
                                 reason TEXT,
                                 duration_seconds INTEGER,
                                 expires_at TIMESTAMP,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_moderation_logs_stream_id ON moderation_logs(stream_id);
CREATE INDEX idx_moderation_logs_target_user ON moderation_logs(target_user_id);
CREATE INDEX idx_moderation_logs_expires_at ON moderation_logs(expires_at);

-- Banned users
CREATE TABLE banned_users (
                              id BIGSERIAL PRIMARY KEY,
                              stream_id BIGINT NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
                              user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              banned_by BIGINT NOT NULL REFERENCES users(id),
                              reason TEXT,
                              is_permanent BOOLEAN DEFAULT TRUE,
                              expires_at TIMESTAMP,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE(stream_id, user_id)
);

CREATE INDEX idx_banned_users_stream_user ON banned_users(stream_id, user_id);
CREATE INDEX idx_banned_users_expires_at ON banned_users(expires_at);

-- Timed out users
CREATE TABLE timed_out_users (
                                 id BIGSERIAL PRIMARY KEY,
                                 stream_id BIGINT NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
                                 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 timed_out_by BIGINT NOT NULL REFERENCES users(id),
                                 reason TEXT,
                                 duration_seconds INTEGER NOT NULL,
                                 expires_at TIMESTAMP NOT NULL,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_timed_out_users_stream_user ON timed_out_users(stream_id, user_id);
CREATE INDEX idx_timed_out_users_expires_at ON timed_out_users(expires_at);