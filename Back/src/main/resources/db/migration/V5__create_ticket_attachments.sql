CREATE TABLE ticket_attachments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    uploader_id BIGINT NOT NULL REFERENCES users(id),
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(80) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ticket_attachments_ticket_created ON ticket_attachments(ticket_id, created_at);
