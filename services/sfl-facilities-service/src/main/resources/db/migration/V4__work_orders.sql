CREATE TABLE facilities.work_orders (
    id UUID PRIMARY KEY,
    work_order_number VARCHAR(40) NOT NULL UNIQUE,
    facility_fault_id UUID NOT NULL REFERENCES facilities.facility_faults(id),
    fault_number VARCHAR(40) NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    location_code VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(40) NOT NULL,
    assigned_to VARCHAR(160),
    closure_notes VARCHAR(2000),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    assigned_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_work_orders_facility_fault
    ON facilities.work_orders (facility_fault_id);
CREATE INDEX ix_work_orders_site_status_created
    ON facilities.work_orders (site_code, status, created_at DESC);
CREATE INDEX ix_work_orders_assigned_to
    ON facilities.work_orders (assigned_to);