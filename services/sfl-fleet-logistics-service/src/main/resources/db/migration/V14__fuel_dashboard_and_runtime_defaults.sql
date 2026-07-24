CREATE VIEW fleet_logistics.fuel_dashboard_summary AS
SELECT site_code,
       COUNT(*) AS transaction_count,
       COALESCE(SUM(quantity), 0) AS fuel_volume,
       COALESCE(SUM(total_cost), 0) AS fuel_spend,
       COUNT(*) FILTER (WHERE status = 'RECONCILED') AS reconciled_count,
       COUNT(*) FILTER (WHERE status = 'EXCEPTION') AS exception_count,
       MAX(last_modified_at) AS source_updated_at
FROM fleet_logistics.fuel_transactions
GROUP BY site_code;

INSERT INTO fleet_logistics.fleet_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, updated_by, updated_at)
VALUES
    (gen_random_uuid(), 'fuel.scheduling.reconciliation-enabled', NULL, 'true', 'BOOLEAN', 'Enable fuel reconciliation sweep.', now(), 'system', now()),
    (gen_random_uuid(), 'fuel.scheduling.receipt-enabled', NULL, 'true', 'BOOLEAN', 'Enable missing receipt sweep.', now(), 'system', now()),
    (gen_random_uuid(), 'fuel.scheduling.logbook-enabled', NULL, 'true', 'BOOLEAN', 'Enable overdue logbook sweep.', now(), 'system', now()),
    (gen_random_uuid(), 'fuel.dashboard.freshness-threshold', NULL, 'PT15M', 'DURATION', 'Fuel dashboard stale-data threshold.', now(), 'system', now())
ON CONFLICT DO NOTHING;
