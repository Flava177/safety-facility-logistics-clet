-- =====================================================================================
-- SRS-SFL-S168fuel-01 - A site may record more than one manual fuel purchase
--
-- `uq_fuel_provider_transaction` was declared UNIQUE NULLS NOT DISTINCT over
-- (site_code, source_system, provider_transaction_id) in V10. For provider feeds that is exactly
-- right: a provider's own transaction reference is the thing that makes a re-delivered message
-- recognisable, and two rows carrying the same one are the same purchase arriving twice.
--
-- For a manual capture it is a trap. A driver at a pump has no provider transaction reference - there
-- is no integration involved - so the column is NULL, and NULLS NOT DISTINCT means PostgreSQL treats
-- every NULL as equal to every other. The first manual purchase at a site therefore *consumed* the
-- key (site, 'MANUAL', NULL), and the second was refused with a duplicate key violation. One manual
-- fuel transaction per site, ever.
--
-- The defect predates this migration: any operator who left the optional "provider transaction
-- reference" field blank hit it. It became certain rather than occasional when the capture form
-- stopped asking for the field at all, which is the correct thing for that form to do - the field
-- belonged to the integration and meant nothing to a driver.
--
-- NULLS DISTINCT - PostgreSQL's default - is the behaviour that was always wanted. It keeps the
-- constraint doing its real job (two provider records with the same reference are still refused) and
-- stops it policing rows that carry no reference at all. It also matches what the application already
-- believes: `FuelRepository.findProviderTransaction` returns empty immediately for a null reference,
-- so the duplicate check was never meant to apply to these rows.
--
-- No data migration is needed. Relaxing a uniqueness rule cannot invalidate rows that satisfied the
-- stricter one.
-- =====================================================================================

ALTER TABLE fleet_logistics.fuel_transactions
    DROP CONSTRAINT uq_fuel_provider_transaction;

ALTER TABLE fleet_logistics.fuel_transactions
    ADD CONSTRAINT uq_fuel_provider_transaction
        UNIQUE NULLS DISTINCT (site_code, source_system, provider_transaction_id);

COMMENT ON CONSTRAINT uq_fuel_provider_transaction ON fleet_logistics.fuel_transactions IS
    'Recognises a re-delivered provider record. NULLS DISTINCT: manual captures carry no provider '
    'reference and must not collide with each other.';
