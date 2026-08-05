CREATE TABLE asset_visibility.asset_references (
    id UUID PRIMARY KEY,
    asset_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(60) NOT NULL,
    status VARCHAR(40) NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    location_type VARCHAR(40) NOT NULL,
    location_reference VARCHAR(120) NOT NULL,
    custodian_reference VARCHAR(160),
    external_reference VARCHAR(160),
    evidence_reference VARCHAR(180),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_asset_references_site_category_status
    ON asset_visibility.asset_references (site_code, category, status);
CREATE INDEX ix_asset_references_location
    ON asset_visibility.asset_references (site_code, location_type, location_reference);
CREATE INDEX ix_asset_references_custodian
    ON asset_visibility.asset_references (custodian_reference);