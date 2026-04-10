ALTER TABLE chat_messages
    ADD COLUMN reply_to_message_id BIGINT;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_reply_to_message
    FOREIGN KEY (reply_to_message_id) REFERENCES chat_messages(id);

CREATE INDEX idx_chat_messages_stream_deleted_created_at
    ON chat_messages (stream_id, is_deleted, created_at);
