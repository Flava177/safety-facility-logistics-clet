-- S174 runtime configuration store and seeded SLA/threshold/scheduling defaults + integration placeholders.
CREATE TABLE emergency_notification.runtime_configuration (
    id UUID PRIMARY KEY,
    config_key VARCHAR(160) NOT NULL,
    site_code VARCHAR(40),
    config_value VARCHAR(500) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    updated_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_runtime_config_key UNIQUE (config_key, site_code)
);

INSERT INTO emergency_notification.runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, updated_by, updated_at)
VALUES
    (gen_random_uuid(), 'emergency.ack.sla', NULL, 'PT30M', 'DURATION', 'Unacknowledged-recipient escalation window.', 'system', now()),
    (gen_random_uuid(), 'emergency.dashboard.freshness-threshold', NULL, 'PT5M', 'DURATION', 'Dashboard stale-data threshold.', 'system', now()),
    (gen_random_uuid(), 'emergency.fast-lane.target', NULL, 'PT2S', 'DURATION', 'Fast-lane detection->notification target (NFR-S2, TBC).', 'system', now()),
    (gen_random_uuid(), 'emergency.scheduling.escalation-enabled', NULL, 'true', 'BOOLEAN', 'Enable unacknowledged-recipient escalation sweep.', 'system', now()),
    (gen_random_uuid(), 'emergency.scheduling.dashboard-enabled', NULL, 'true', 'BOOLEAN', 'Enable dashboard snapshot refresh.', 'system', now()),
    (gen_random_uuid(), 'emergency.scheduling.stale-integration-enabled', NULL, 'true', 'BOOLEAN', 'Enable stale-integration detection.', 'system', now()),
    (gen_random_uuid(), 'emergency.integration.SIMULATOR.enabled', NULL, 'true', 'BOOLEAN', 'Allowlist the simulator provider for inbound callbacks.', 'system', now()),
    (gen_random_uuid(), 'emergency.integration.SIMULATOR.secret', NULL, 'sfl-emergency-simulator-secret', 'STRING', 'HMAC secret placeholder for the simulator provider.', 'system', now())
ON CONFLICT DO NOTHING;
