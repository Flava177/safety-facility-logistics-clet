-- =====================================================================================
-- SRS-SFL-S168fuel-02/04 - Evidence at the pump, and a price the driver does not choose
--
-- The problem this migration exists to answer, stated plainly: a driver buys GHS 100 of fuel, agrees
-- with the attendant to record GHS 120, and pockets the difference. Nothing in the platform could see
-- it. Both numbers that produce the total - litres and price per litre - were typed by the person
-- being reimbursed, and the only cross-check was COST_VARIANCE, which compares a transaction to the
-- previous one *for the same vehicle*. That catches a sudden jump. It does not catch a steady 20%
-- overstatement, and it treats a genuine national price rise as fraud.
--
-- Two additions, each attacking a different half of the collusion.
--
-- 1. THE PUMP PHOTOGRAPH (`pump_evidence_id`)
--
--    The receipt says what the vendor was willing to write down. The pump meter says what the pump
--    dispensed. Where those two disagree is exactly where collusion lives, and no amount of arithmetic
--    on the recorded figures can reveal it - only a second, independent observation can. That is why
--    this is a separate column and not "another receipt": it is a different witness.
--
-- 2. THE POSTED PRICE (`fuel_posted_prices`)
--
--    Fuel has a posted price. It is printed on the forecourt sign, it is the same for everyone at that
--    station that day, and it is not the driver's to decide - so the platform should hold it, and the
--    driver should enter what they paid rather than what a litre costs. Litres then falls out of the
--    arithmetic instead of being asserted.
--
--    What that buys, beyond the obvious: every volumetric control the policy already has - tank
--    capacity, consumption range, daily and monthly litre limits - starts biting on overstated *money*.
--    Before, inflating the amount paid touched nothing volumetric, because litres was a separate field
--    the driver could leave honest. Now the two are locked together: claiming 20% more money claims
--    20% more fuel, and 20% more fuel than the tank holds is a rule failure the platform already knows
--    how to raise.
--
--    Effective-dated for the same reason the policy is: a transaction must be judged against the price
--    that was posted when it happened, not the price posted today. Prices change weekly here.
--
-- No prices are seeded. An invented reference price is worse than none - it would produce confident
-- anomalies about a market that does not exist - so the rule reports "no reference price on file" and
-- passes until somebody records the real ones.
-- =====================================================================================

ALTER TABLE fleet_logistics.fuel_transactions
    ADD COLUMN pump_evidence_id UUID;

COMMENT ON COLUMN fleet_logistics.fuel_transactions.pump_evidence_id IS
    'Photograph of the pump meter reading. The independent witness to receipt_evidence_id.';

CREATE TABLE fleet_logistics.fuel_posted_prices (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    -- Upper-cased on write, matching how FuelPolicy normalises its approved vendor set, so the join
    -- between "vendors we allow" and "prices we know" is exact rather than nearly.
    vendor VARCHAR(160) NOT NULL,
    fuel_product VARCHAR(60) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    -- Where the figure came from. A price typed by a fleet administrator and a price delivered by a
    -- provider feed carry different weight in a dispute, and after the fact nobody can tell them
    -- apart without this.
    source VARCHAR(40) NOT NULL,
    notes VARCHAR(500),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fuel_posted_price_positive CHECK (unit_price > 0),
    CONSTRAINT ck_fuel_posted_price_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- The lookup is always "the price for this vendor and product at this instant", so the index is
-- ordered to serve exactly that and nothing else.
CREATE INDEX ix_fuel_posted_price_lookup
    ON fleet_logistics.fuel_posted_prices (site_code, vendor, fuel_product, effective_from DESC);

-- Two open-ended prices for one vendor and product would make "the price in force" ambiguous, and an
-- ambiguous reference price is worse than no reference price: the anomaly it raises depends on row
-- order. Partial unique index rather than a constraint, because it must apply only to the open row.
CREATE UNIQUE INDEX uq_fuel_posted_price_open
    ON fleet_logistics.fuel_posted_prices (site_code, vendor, fuel_product)
    WHERE effective_to IS NULL;
