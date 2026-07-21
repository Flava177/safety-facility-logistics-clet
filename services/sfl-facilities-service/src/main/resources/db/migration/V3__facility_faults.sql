CREATE TABLE facilities.facility_faults (
    id UUID PRIMARY KEY,
    fault_number VARCHAR(40) NOT NULL UNIQUE,
    site_code VARCHAR(40) NOT NULL,
    location_code VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    category VARCHAR(120),
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reported_by VARCHAR(160) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    work_order_id UUID
);

CREATE INDEX ix_facilities_facility_faults_site_status
    ON facilities.facility_faults (site_code, status);
CREATE INDEX ix_facilities_facility_faults_work_order
    ON facilities.facility_faults (work_order_id);