-- S174-02/04 delivery fan-out, provider delivery receipts and recipient acknowledgements (idempotent).
CREATE TABLE emergency_notification.notification_channels (
    id UUID PRIMARY KEY,
    activation_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    channel_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    target_count INTEGER NOT NULL DEFAULT 0,
    sent_count INTEGER NOT NULL DEFAULT 0,
    delivered_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    acknowledged_count INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_activation_channel UNIQUE (activation_id, channel_type)
);
CREATE INDEX ix_channel_activation ON emergency_notification.notification_channels(activation_id);

CREATE TABLE emergency_notification.delivery_receipts (
    id UUID PRIMARY KEY,
    activation_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    channel_type VARCHAR(30) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    provider_message_id VARCHAR(160) NOT NULL,
    recipient_ref VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT uq_delivery_receipt UNIQUE (activation_id, provider, provider_message_id),
    CONSTRAINT ck_delivery_status CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED','EXPIRED'))
);
CREATE INDEX ix_delivery_activation ON emergency_notification.delivery_receipts(activation_id, occurred_at DESC);

CREATE TABLE emergency_notification.acknowledgements (
    id UUID PRIMARY KEY,
    activation_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    channel_type VARCHAR(30),
    recipient_ref VARCHAR(200) NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT uq_acknowledgement UNIQUE (activation_id, recipient_ref)
);
CREATE INDEX ix_ack_activation ON emergency_notification.acknowledgements(activation_id, acknowledged_at DESC);
