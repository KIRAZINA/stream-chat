-- Users table
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(50) UNIQUE NOT NULL,
                       email VARCHAR(100) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       display_name VARCHAR(100),
                       color VARCHAR(7) DEFAULT '#000000',
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- Streams table
CREATE TABLE streams (
                         id BIGSERIAL PRIMARY KEY,
                         stream_key VARCHAR(100) UNIQUE NOT NULL,
                         user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         title VARCHAR(200),
                         description TEXT,
                         is_live BOOLEAN DEFAULT FALSE,
                         viewer_count INTEGER DEFAULT 0,
                         started_at TIMESTAMP,
                         ended_at TIMESTAMP,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_streams_user_id ON streams(user_id);
CREATE INDEX idx_streams_is_live ON streams(is_live);
CREATE INDEX idx_streams_stream_key ON streams(stream_key);

-- Chat messages table
CREATE TABLE chat_messages (
                               id BIGSERIAL PRIMARY KEY,
                               stream_id BIGINT NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
                               user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               username VARCHAR(50) NOT NULL,
                               content TEXT NOT NULL,
                               message_type VARCHAR(20) DEFAULT 'CHAT',
                               is_deleted BOOLEAN DEFAULT FALSE,
                               deleted_by BIGINT REFERENCES users(id),
                               deleted_at TIMESTAMP,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_messages_stream_id ON chat_messages(stream_id);
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at DESC);

-- User roles in streams
CREATE TABLE user_stream_roles (
                                   id BIGSERIAL PRIMARY KEY,
                                   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   stream_id BIGINT NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
                                   role VARCHAR(20) NOT NULL,
                                   granted_by BIGINT REFERENCES users(id),
                                   granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   UNIQUE(user_id, stream_id, role)
);

CREATE INDEX idx_user_stream_roles_user_stream ON user_stream_roles(user_id, stream_id);

-- User badges
CREATE TABLE user_badges (
                             id BIGSERIAL PRIMARY KEY,
                             user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                             stream_id BIGINT REFERENCES streams(id) ON DELETE CASCADE,
                             badge_type VARCHAR(20) NOT NULL,
                             granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_badges_user_id ON user_badges(user_id);