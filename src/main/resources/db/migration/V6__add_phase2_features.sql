-- Add indexes for AutoMod and retention features
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at
    ON chat_messages (created_at);

CREATE INDEX IF NOT EXISTS idx_moderation_logs_action_type
    ON moderation_logs (action_type);

CREATE INDEX IF NOT EXISTS idx_moderation_logs_created_at
    ON moderation_logs (created_at);
