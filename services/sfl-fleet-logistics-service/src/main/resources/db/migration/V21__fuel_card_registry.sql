-- SRS-SFL-S168fuel-04: fuel-card management.
--
-- The requirement had no code at all. `fuel_transactions.masked_card_reference` has been captured since
-- V10 as a bare string with nothing behind it, so the platform could show you which card was used and
-- could not answer any question worth asking about it: is that card ours, is it still active, is it
-- the card assigned to that vehicle, and has it already spent its month. Anti-fraud control was the
-- stated purpose of S168_fuel in the C9 mapping, and a card reference nobody can validate is not one.
--
-- **The masked reference is the key, and the full number is never stored.** A fuel card number is
-- payment data; this platform has no business holding one, and the mapping puts the card platform
-- outside SFL. What is stored is the same masked form the provider already sends on every transaction
-- (`****1234`), which is enough to match a transaction to a card and not enough to use one.
--
-- Assignment is to a vehicle, optionally narrowed to a driver. A card assigned to a vehicle and used
-- to fill a different vehicle is the single most common fuel fraud, and it becomes detectable the
-- moment there is a row to compare against.

CREATE TABLE fleet_logistics.fuel_cards (
    id                    UUID PRIMARY KEY,
    site_code             VARCHAR(40)  NOT NULL,
    masked_reference      VARCHAR(40)  NOT NULL,
    provider              VARCHAR(120) NOT NULL,
    vehicle_id            UUID,
    driver_id             UUID,
    status                VARCHAR(20)  NOT NULL,
    issued_on             DATE         NOT NULL,
    expires_on            DATE,
    -- Per-card ceilings. Null means "the site policy decides", so a card only overrides deliberately.
    daily_limit           NUMERIC(12, 2),
    monthly_limit         NUMERIC(12, 2),
    per_transaction_limit NUMERIC(12, 2),
    suspension_reason     VARCHAR(500),
    notes                 VARCHAR(1000),
    created_by            VARCHAR(160) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    last_modified_by      VARCHAR(160) NOT NULL,
    last_modified_at      TIMESTAMPTZ  NOT NULL,
    source_channel        VARCHAR(40)  NOT NULL,
    audit_correlation_id  VARCHAR(120),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_card_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED')),
    -- A suspended card without a reason is a card nobody can explain to the person holding it.
    CONSTRAINT ck_fuel_card_suspension CHECK (status <> 'SUSPENDED' OR suspension_reason IS NOT NULL),
    CONSTRAINT ck_fuel_card_expiry CHECK (expires_on IS NULL OR expires_on >= issued_on),
    CONSTRAINT ck_fuel_card_limits CHECK (
        (daily_limit IS NULL OR daily_limit > 0)
        AND (monthly_limit IS NULL OR monthly_limit > 0)
        AND (per_transaction_limit IS NULL OR per_transaction_limit > 0))
);

-- One live card per masked reference per site. Cancelled cards stay for history — a transaction from
-- three years ago still has to resolve to the card that made it — so the uniqueness is partial.
CREATE UNIQUE INDEX ux_fuel_card_reference_live
    ON fleet_logistics.fuel_cards (site_code, masked_reference)
    WHERE status <> 'CANCELLED';

-- The reconciliation lookup: given a transaction's masked reference, which card is this?
CREATE INDEX ix_fuel_card_lookup
    ON fleet_logistics.fuel_cards (site_code, masked_reference, status);

-- "Which cards is this vehicle carrying?" — the question asked when a vehicle is disposed of.
CREATE INDEX ix_fuel_card_vehicle
    ON fleet_logistics.fuel_cards (vehicle_id)
    WHERE vehicle_id IS NOT NULL;
