CREATE TABLE fleet_logistics.fuel_import_batches (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    source_system VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_rows INTEGER NOT NULL,
    accepted_rows INTEGER NOT NULL,
    rejected_rows INTEGER NOT NULL,
    submitted_by VARCHAR(160) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    CONSTRAINT uq_fuel_import_file UNIQUE (site_code, source_system, file_hash)
);

CREATE TABLE fleet_logistics.fuel_import_rows (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES fleet_logistics.fuel_import_batches(id),
    row_number INTEGER NOT NULL,
    provider_transaction_id VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    transaction_id UUID,
    error_code VARCHAR(80),
    error_message VARCHAR(1000),
    raw_record JSONB NOT NULL,
    CONSTRAINT uq_fuel_import_row UNIQUE (batch_id, row_number)
);
