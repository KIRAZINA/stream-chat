-- Stream chat settings
CREATE TABLE stream_settings (
                                 id BIGSERIAL PRIMARY KEY,
                                 stream_id BIGINT UNIQUE NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
                                 slow_mode_enabled BOOLEAN DEFAULT FALSE,
                                 slow_mode_seconds INTEGER DEFAULT 0,
                                 followers_only_mode BOOLEAN DEFAULT FALSE,
                                 followers_only_duration_minutes INTEGER DEFAULT 0,
                                 subscribers_only_mode BOOLEAN DEFAULT FALSE,
                                 emote_only_mode BOOLEAN DEFAULT FALSE,
                                 max_message_length INTEGER DEFAULT 500,
                                 profanity_filter_enabled BOOLEAN DEFAULT TRUE,
                                 link_protection_enabled BOOLEAN DEFAULT TRUE,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Blocked words/phrases
CREATE TABLE blocked_words (
                               id BIGSERIAL PRIMARY KEY,
                               stream_id BIGINT REFERENCES streams(id) ON DELETE CASCADE,
                               word VARCHAR(100) NOT NULL,
                               is_regex BOOLEAN DEFAULT FALSE,
                               is_global BOOLEAN DEFAULT FALSE,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_blocked_words_stream_id ON blocked_words(stream_id);

-- Emotes
CREATE TABLE emotes (
                        id BIGSERIAL PRIMARY KEY,
                        stream_id BIGINT REFERENCES streams(id) ON DELETE CASCADE,
                        code VARCHAR(50) NOT NULL,
                        image_url VARCHAR(500) NOT NULL,
                        is_global BOOLEAN DEFAULT FALSE,
                        created_by BIGINT REFERENCES users(id),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(stream_id, code)
);

CREATE INDEX idx_emotes_stream_id ON emotes(stream_id);
CREATE INDEX idx_emotes_code ON emotes(code);