ALTER TABLE fleet_logistics.fuel_anomaly_cases
    DROP CONSTRAINT uq_fuel_anomaly_rule;

ALTER TABLE fleet_logistics.fuel_anomaly_cases
    ADD CONSTRAINT uq_fuel_anomaly_rule
    UNIQUE NULLS NOT DISTINCT (transaction_id, logbook_id, trip_id, anomaly_type);
