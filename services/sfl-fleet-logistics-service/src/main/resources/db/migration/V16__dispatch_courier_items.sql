-- S171 Mailroom / Courier and Dispatch Tracking — courier item register (inbound and outbound).
CREATE TABLE fleet_logistics.courier_items (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    item_number VARCHAR(60) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    item_type VARCHAR(40) NOT NULL,
    sensitivity VARCHAR(20) NOT NULL,
    chain_of_custody_required BOOLEAN NOT NULL,
    origin VARCHAR(200) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    sender VARCHAR(200),
    recipient VARCHAR(200),
    assigned_handler VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    acknowledged_by VARCHAR(160),
    acknowledged_at TIMESTAMPTZ,
    acknowledgement_evidence_id UUID,
    distribution_reference VARCHAR(300),
    misroute_reason VARCHAR(500),
    undelivered BOOLEAN NOT NULL DEFAULT false,
    exception_reason VARCHAR(500),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_courier_item_number UNIQUE (site_code, item_number),
    CONSTRAINT ck_courier_item_direction CHECK (direction IN ('INBOUND','OUTBOUND'))
);

CREATE INDEX ix_courier_item_site_date ON fleet_logistics.courier_items(site_code, created_at DESC);
CREATE INDEX ix_courier_item_status ON fleet_logistics.courier_items(site_code, status, created_at);
CREATE INDEX ix_courier_item_sensitivity ON fleet_logistics.courier_items(site_code, sensitivity, status);
CREATE INDEX ix_courier_item_handler ON fleet_logistics.courier_items(site_code, assigned_handler);
-- Undelivered-inbound sweep queue: inbound items still open past their window.
CREATE INDEX ix_courier_item_inbound_open ON fleet_logistics.courier_items(site_code, direction, status, created_at)
    WHERE direction = 'INBOUND';
