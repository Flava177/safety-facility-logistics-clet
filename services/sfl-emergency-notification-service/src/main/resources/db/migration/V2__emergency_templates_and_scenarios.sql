-- S174-01 operational records: notification templates and emergency scenarios.
CREATE TABLE emergency_notification.notification_templates (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    template_code VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    channels TEXT NOT NULL,
    break_glass_eligible BOOLEAN NOT NULL DEFAULT false,
    lifecycle VARCHAR(20) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_code UNIQUE (site_code, template_code),
    CONSTRAINT ck_template_lifecycle CHECK (lifecycle IN ('ACTIVE','INACTIVE','SUSPENDED','ARCHIVED'))
);
CREATE INDEX ix_template_site ON emergency_notification.notification_templates(site_code, lifecycle);

CREATE TABLE emergency_notification.emergency_scenarios (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    scenario_code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    default_template_id UUID,
    break_glass_eligible BOOLEAN NOT NULL DEFAULT false,
    lifecycle VARCHAR(20) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_scenario_code UNIQUE (site_code, scenario_code),
    CONSTRAINT ck_scenario_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_scenario_lifecycle CHECK (lifecycle IN ('ACTIVE','INACTIVE','SUSPENDED','ARCHIVED'))
);
CREATE INDEX ix_scenario_site ON emergency_notification.emergency_scenarios(site_code, lifecycle);
