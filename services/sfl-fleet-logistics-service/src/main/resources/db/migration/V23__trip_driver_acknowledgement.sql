-- =====================================================================================
-- SRS-SFL-S166-02 — the assigned driver's answer to a trip.
--
-- A driver could see a trip and could not respond to it. Every transition on the workflow was a
-- dispatcher's: assign, start, hold, cancel, close. So the assignment was a one-way instruction, and
-- the question a dispatcher actually needs answered before the vehicle is due out — *has the driver
-- seen this, and can they take it?* — had no place to live. It was being answered by telephone.
--
-- **Four columns, not two statuses.** The tempting implementation adds ACKNOWLEDGED and DEFERRED to
-- `trips.status`. That would put the driver's answer into the same enum as the trip's lifecycle, and
-- they are independent: a confirmed trip and an unanswered one are both assigned, both hold their
-- vehicle, both appear in the same readiness calculation. Every consumer of `status` — the transition
-- policy, the GiST exclusion constraint on live statuses, the dashboard counters — would have had to
-- learn two values that tell it nothing about the lifecycle. See `TripAcknowledgementState` for the
-- longer form of this argument.
--
-- **Deferral does not unassign.** The trip stays ASSIGNED to the driver who deferred, and keeps its
-- vehicle booking. A driver dropping their own trip is not a driver's decision to make alone: it
-- leaves a vehicle held and a job nobody is watching. The deferral is the signal a dispatcher acts on.
-- =====================================================================================

ALTER TABLE fleet_logistics.trips
    ADD COLUMN acknowledgement_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN acknowledgement_reason VARCHAR(1000),
    ADD COLUMN acknowledged_at TIMESTAMPTZ,
    ADD COLUMN acknowledged_by VARCHAR(160);

-- The enum, enforced in the database as well as the domain. `ddl-auto: validate` checks that a column
-- exists and has the right type; it cannot check that only three strings ever reach it, and a bad
-- value written by a migration or a manual fix would fail on read rather than on write.
ALTER TABLE fleet_logistics.trips
    ADD CONSTRAINT ck_fleet_trips_acknowledgement_state
        CHECK (acknowledgement_state IN ('PENDING', 'CONFIRMED', 'DEFERRED'));

-- A deferral says why. This is the invariant `TripAcknowledgement` enforces in the constructor,
-- repeated here because the constructor only guards the rows this application writes.
ALTER TABLE fleet_logistics.trips
    ADD CONSTRAINT ck_fleet_trips_deferral_reason
        CHECK (acknowledgement_state <> 'DEFERRED' OR acknowledgement_reason IS NOT NULL);

-- An answered trip records who answered and when; an unanswered one records neither.
ALTER TABLE fleet_logistics.trips
    ADD CONSTRAINT ck_fleet_trips_acknowledged_attribution
        CHECK ((acknowledgement_state = 'PENDING'
                    AND acknowledged_at IS NULL AND acknowledged_by IS NULL)
               OR (acknowledgement_state <> 'PENDING'
                    AND acknowledged_at IS NOT NULL AND acknowledged_by IS NOT NULL));

-- The dispatcher's question: which of tomorrow's assignments has nobody answered for. Partial, because
-- answered trips are the majority and are never the subject of this query.
CREATE INDEX ix_fleet_trips_awaiting_acknowledgement
    ON fleet_logistics.trips (site_code, planned_start)
    WHERE acknowledgement_state = 'PENDING' AND status = 'ASSIGNED';

-- The DEFAULT stays on the column. Existing rows are backfilled by it, and it is what makes an
-- INSERT that predates this feature — there are several in the test fixtures — still legal.
COMMENT ON COLUMN fleet_logistics.trips.acknowledgement_state IS
    'The assigned driver''s answer: PENDING, CONFIRMED or DEFERRED. Independent of status; a deferred '
    'trip is still ASSIGNED and still holds its vehicle.';
