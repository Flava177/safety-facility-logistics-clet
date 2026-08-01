ALTER TABLE fleet_logistics.fuel_policies
    ADD COLUMN cost_variance_tolerance NUMERIC(5,4) NOT NULL DEFAULT 0.3000,
    ADD COLUMN repeated_pattern_window_hours INTEGER NOT NULL DEFAULT 720,
    ADD COLUMN repeated_pattern_threshold INTEGER NOT NULL DEFAULT 3;

ALTER TABLE fleet_logistics.fuel_policies
    ADD CONSTRAINT ck_fuel_policy_versioned_thresholds CHECK (
        cost_variance_tolerance >= 0
        AND repeated_pattern_window_hours > 0
        AND repeated_pattern_threshold > 0
    );
