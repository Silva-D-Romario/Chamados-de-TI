CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    message VARCHAR(220) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notifications_ticket_recipient_type UNIQUE (ticket_id, recipient_id, type)
);

CREATE INDEX idx_notifications_recipient_created_at
    ON notifications(recipient_id, created_at DESC);
