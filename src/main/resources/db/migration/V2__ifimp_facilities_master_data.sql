CREATE TABLE ifimp.sites (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ifimp.buildings (
    id UUID PRIMARY KEY,
    site_id UUID NOT NULL REFERENCES ifimp.sites(id),
    site_code VARCHAR(40) NOT NULL,
    building_code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_buildings_site_code_building_code UNIQUE (site_code, building_code)
);

CREATE INDEX ix_buildings_site_code
    ON ifimp.buildings (site_code);

CREATE TABLE ifimp.facility_floors (
    id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES ifimp.buildings(id),
    site_code VARCHAR(40) NOT NULL,
    floor_code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    level_number INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_facility_floors_building_floor_code UNIQUE (building_id, floor_code)
);

CREATE INDEX ix_facility_floors_building
    ON ifimp.facility_floors (building_id, level_number, floor_code);

CREATE TABLE ifimp.facility_rooms (
    id UUID PRIMARY KEY,
    floor_id UUID NOT NULL REFERENCES ifimp.facility_floors(id),
    site_code VARCHAR(40) NOT NULL,
    room_code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    room_type VARCHAR(80),
    capacity INTEGER,
    readiness_status VARCHAR(32) NOT NULL,
    readiness_notes VARCHAR(1000),
    readiness_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_facility_rooms_capacity_non_negative CHECK (capacity IS NULL OR capacity >= 0),
    CONSTRAINT uq_facility_rooms_site_room_code UNIQUE (site_code, room_code)
);

CREATE INDEX ix_facility_rooms_site_readiness
    ON ifimp.facility_rooms (site_code, readiness_status);

CREATE TABLE ifimp.zones (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    zone_code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    purpose VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_zones_site_zone_code UNIQUE (site_code, zone_code)
);

CREATE INDEX ix_zones_site_code
    ON ifimp.zones (site_code);

CREATE TABLE ifimp.device_references (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    device_code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    room_id UUID REFERENCES ifimp.facility_rooms(id),
    location_code VARCHAR(120),
    vendor VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_device_references_site_device_code UNIQUE (site_code, device_code)
);

CREATE INDEX ix_device_references_site_type_status
    ON ifimp.device_references (site_code, type, status);
CREATE INDEX ix_device_references_room
    ON ifimp.device_references (room_id);
