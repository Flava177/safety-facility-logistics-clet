-- S171 dispatch manifest and manifest items. Optional S166 trip/vehicle/driver soft references (no FK).
CREATE TABLE fleet_logistics.dispatches (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    manifest_number VARCHAR(60) NOT NULL,
    route VARCHAR(300) NOT NULL,
    assigned_handler VARCHAR(160) NOT NULL,
    destination_centre VARCHAR(200),
    examination_context VARCHAR(200),
    trip_id UUID,
    vehicle_id UUID,
    driver_id UUID,
    item_count INTEGER NOT NULL DEFAULT 0,
    seal_ids TEXT,
    status VARCHAR(30) NOT NULL,
    dispatched_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    reconciled_at TIMESTAMPTZ,
    closure_reason VARCHAR(500),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_dispatch_manifest_number UNIQUE (site_code, manifest_number),
    CONSTRAINT ck_dispatch_item_count CHECK (item_count >= 0)
);

CREATE TABLE fleet_logistics.dispatch_manifest_items (
    id UUID PRIMARY KEY,
    dispatch_id UUID NOT NULL,
    courier_item_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    sequence_no INTEGER NOT NULL,
    expected_seal_id VARCHAR(80),
    expected_quantity INTEGER NOT NULL DEFAULT 1,
    return_status VARCHAR(30),
    returned_at TIMESTAMPTZ,
    return_seal_state VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_manifest_item UNIQUE (dispatch_id, courier_item_id),
    CONSTRAINT uq_manifest_item_sequence UNIQUE (dispatch_id, sequence_no),
    CONSTRAINT ck_manifest_item_quantity CHECK (expected_quantity > 0)
);

CREATE INDEX ix_dispatch_site_date ON fleet_logistics.dispatches(site_code, created_at DESC);
CREATE INDEX ix_dispatch_status ON fleet_logistics.dispatches(site_code, status, created_at);
CREATE INDEX ix_dispatch_trip ON fleet_logistics.dispatches(trip_id);
CREATE INDEX ix_dispatch_centre ON fleet_logistics.dispatches(site_code, destination_centre);
CREATE INDEX ix_manifest_item_dispatch ON fleet_logistics.dispatch_manifest_items(dispatch_id, sequence_no);
CREATE INDEX ix_manifest_item_courier ON fleet_logistics.dispatch_manifest_items(courier_item_id);
-- Outstanding-return sweep queue: dispatched manifest items not yet returned.
CREATE INDEX ix_manifest_item_return ON fleet_logistics.dispatch_manifest_items(site_code, return_status);
