-- S174-01 operational records: audience groups and recipient zones.
CREATE TABLE emergency_notification.audience_groups (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    group_code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    directory_reference VARCHAR(200),
    recipient_count INTEGER NOT NULL DEFAULT 0,
    lifecycle VARCHAR(20) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_audience_code UNIQUE (site_code, group_code),
    CONSTRAINT ck_audience_lifecycle CHECK (lifecycle IN ('ACTIVE','INACTIVE','SUSPENDED','ARCHIVED')),
    CONSTRAINT ck_audience_count CHECK (recipient_count >= 0)
);
CREATE INDEX ix_audience_site ON emergency_notification.audience_groups(site_code, lifecycle);

CREATE TABLE emergency_notification.recipient_zones (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    zone_code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    location_reference VARCHAR(200),
    lifecycle VARCHAR(20) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_zone_code UNIQUE (site_code, zone_code),
    CONSTRAINT ck_zone_lifecycle CHECK (lifecycle IN ('ACTIVE','INACTIVE','SUSPENDED','ARCHIVED'))
);
CREATE INDEX ix_zone_site ON emergency_notification.recipient_zones(site_code, lifecycle);
