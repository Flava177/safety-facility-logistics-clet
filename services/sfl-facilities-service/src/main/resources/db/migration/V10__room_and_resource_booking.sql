-- =============================================================================================
-- S159 Room and Resource Booking.
--
-- Six new tables on the S152 estate. Nothing existing is altered: unlike S153, which inherited a
-- pre-platform faults spine, booking is new work and has no rows to preserve.
--
-- The one thing in this file that is not routine is the exclusion constraint on facilities.bookings.
-- Everything else could be got right in Java and checked in a test. That cannot: it is the only
-- mechanism that holds when two people press Request in the same second, and the application-level
-- conflict check exists to produce a readable message rather than to make the rule true.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- btree_gist.
--
-- A GIST exclusion constraint can index the range overlap operator (&&) out of the box. It cannot
-- index UUID equality without this extension, and "the same room" is the other half of the rule —
-- without it the constraint would have to forbid two overlapping bookings anywhere in the estate.
--
-- Trusted since PostgreSQL 13, so the database owner can install it; no superuser is required. If
-- this line fails, the deployment is on an older server or a managed service with an extension
-- allow-list, and the module cannot offer its central guarantee. Failing here, loudly, at migration
-- time is the right outcome: the alternative is a service that starts and double-books.
-- ---------------------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Booking references: BK-CLET-HQ-000123. Sortable, sayable over a radio, unique without
-- coordination. Same reasoning as the fault and work-order sequences in V9.
CREATE SEQUENCE IF NOT EXISTS facilities.booking_reference_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- bookable_resources
--
-- Deliberately separate from facility_assets. An asset is fixed plant whose condition feeds a
-- space's readiness; a resource is portable and its scarcity is the point. asset_id links the two
-- where the same object appears in both, and is a value rather than a foreign key because a
-- projector can be retired from the asset register while its resource row is still being booked.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bookable_resources (
    id               UUID PRIMARY KEY,
    site_code        VARCHAR(40)  NOT NULL,
    resource_code    VARCHAR(80)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    category         VARCHAR(30)  NOT NULL,
    description      VARCHAR(2000),
    -- One row for a set of forty chairs, not forty rows. A quantity of exactly one is what makes a
    -- resource exclusive, and exclusivity is what the constraint below can enforce.
    quantity         INTEGER      NOT NULL,
    home_room_id     UUID REFERENCES facilities.facility_rooms (id),
    asset_id         UUID,
    requires_setup   BOOLEAN      NOT NULL DEFAULT FALSE,
    lifecycle_status VARCHAR(20)  NOT NULL,
    created_by       VARCHAR(160) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    record_version   BIGINT       NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)  NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ck_bookable_resources_quantity CHECK (quantity >= 1)
);

-- Partial: an archived resource releases its code, matching RecordLifecycleStatus.occupiesIdentifier.
CREATE UNIQUE INDEX IF NOT EXISTS ux_bookable_resources_code
    ON facilities.bookable_resources (site_code, resource_code)
    WHERE lifecycle_status <> 'ARCHIVED';
CREATE INDEX IF NOT EXISTS ix_bookable_resources_site
    ON facilities.bookable_resources (site_code, category);

-- ---------------------------------------------------------------------------------------------
-- bookings
--
-- occupied_from and occupied_to are the booked window widened by the setup and teardown buffers.
-- They are derived state, stored, and that is the whole mechanism: an exclusion constraint has to
-- range over columns on this table and cannot call a Java method or follow a join.
--
-- They are not GENERATED ALWAYS columns, which would have been tidier and is not available:
-- timestamptz - interval is STABLE rather than IMMUTABLE in PostgreSQL, because adding days or
-- months depends on the session time zone, and a stored generated column may only use immutable
-- expressions. So the application writes them and ck_bookings_occupied below catches it if it ever
-- stops — a change that widened the buffers but forgot these columns would otherwise let the next
-- booking start while the chairs were still being moved, silently.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bookings (
    id                    UUID PRIMARY KEY,
    booking_reference     VARCHAR(40)  NOT NULL,
    site_code             VARCHAR(40)  NOT NULL,
    room_id               UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code             VARCHAR(80)  NOT NULL,
    purpose               VARCHAR(20)  NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    description           VARCHAR(4000),
    starts_at             TIMESTAMPTZ  NOT NULL,
    ends_at               TIMESTAMPTZ  NOT NULL,
    setup_minutes         INTEGER      NOT NULL DEFAULT 0,
    teardown_minutes      INTEGER      NOT NULL DEFAULT 0,
    occupied_from         TIMESTAMPTZ  NOT NULL,
    occupied_to           TIMESTAMPTZ  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    expected_attendees    INTEGER      NOT NULL DEFAULT 0,
    requested_by          VARCHAR(160) NOT NULL,
    requested_for         VARCHAR(200),
    requested_at          TIMESTAMPTZ  NOT NULL,
    -- Resolved from configuration when the booking was made and stored, so a rule changed while a
    -- booking sits in the queue does not retrospectively make it un-approvable.
    approval_required     BOOLEAN      NOT NULL DEFAULT FALSE,
    approval_id           UUID,
    confirmed_at          TIMESTAMPTZ,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    closure_reason        VARCHAR(2000),
    -- A flag beside the status, not a state. A confirmed booking on a space that has just been
    -- blocked is still a confirmed booking; somebody has it in their diary.
    readiness_hold_reason VARCHAR(30),
    readiness_held_at     TIMESTAMPTZ,
    -- Set when somebody with the override permission booked into a space readiness refused. Its
    -- presence is the audit trail.
    override_reason       VARCHAR(2000),
    lifecycle_status      VARCHAR(20)  NOT NULL,
    created_by            VARCHAR(160) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    last_modified_by      VARCHAR(160) NOT NULL,
    last_modified_at      TIMESTAMPTZ  NOT NULL,
    record_version        BIGINT       NOT NULL DEFAULT 0,
    source_channel        VARCHAR(40)  NOT NULL,
    correlation_id        VARCHAR(120),

    CONSTRAINT ck_bookings_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_bookings_buffers CHECK (setup_minutes >= 0 AND teardown_minutes >= 0),
    CONSTRAINT ck_bookings_occupied CHECK (occupied_from <= starts_at AND occupied_to >= ends_at),
    CONSTRAINT ck_bookings_attendees CHECK (expected_attendees >= 0),
    -- The hold and its timestamp are one fact. Either both or neither.
    CONSTRAINT ck_bookings_hold CHECK ((readiness_hold_reason IS NULL) = (readiness_held_at IS NULL)),

    -- =========================================================================================
    -- The rule the module exists to enforce.
    --
    --   '[)'  half-open. A booking ending at 10:00 and one starting at 10:00 do NOT overlap.
    --         Get this wrong one way and every back-to-back lecture reports a phantom clash until
    --         people stop trusting the check; wrong the other way and the hall is double-booked on
    --         the hour, which is exactly when lectures change over. Written explicitly rather than
    --         left to tstzrange's default, which somebody could otherwise assume.
    --
    --   occupied_*  the widened window, not the booked one. Booking against starts_at/ends_at would
    --         let the next booking begin during the teardown.
    --
    --   WHERE  the three statuses that hold the space, matching BookingStatus.holdsTheSpace().
    --         REQUESTED is among them on purpose: a request holds the room, so the second person to
    --         ask is refused immediately rather than the approver being handed a clash to arbitrate.
    --         If that set changes in Java it must change here. There is no compiler to catch it, so
    --         S159MandatoryScenariosTest asserts the two agree.
    -- =========================================================================================
    CONSTRAINT ux_bookings_no_double_booking EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(occupied_from, occupied_to, '[)') WITH &&
    ) WHERE (status IN ('REQUESTED', 'CONFIRMED', 'IN_USE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bookings_reference
    ON facilities.bookings (booking_reference);
CREATE INDEX IF NOT EXISTS ix_bookings_site_window
    ON facilities.bookings (site_code, occupied_from);
CREATE INDEX IF NOT EXISTS ix_bookings_requested_by
    ON facilities.bookings (requested_by, starts_at);
-- Partial: the availability query and the reconciliation sweep only ever read live rows, and the
-- terminal ones outnumber them within a term.
CREATE INDEX IF NOT EXISTS ix_bookings_live
    ON facilities.bookings (room_id, occupied_from)
    WHERE status IN ('REQUESTED', 'CONFIRMED', 'IN_USE');
-- The no-show sweep reads exactly this, and the set drains as fast as it fills.
CREATE INDEX IF NOT EXISTS ix_bookings_no_show_candidates
    ON facilities.bookings (starts_at)
    WHERE status = 'CONFIRMED' AND started_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_bookings_on_hold
    ON facilities.bookings (site_code, starts_at)
    WHERE readiness_hold_reason IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- booking_resource_allocations
--
-- The window is copied from the booking rather than joined, for the same reason as above: the
-- constraint has to range over columns here. Moving a booking therefore moves its allocations, in
-- one transaction, in BookingApplicationService.reschedule.
--
-- is_exclusive is copied from the resource's quantity at allocation time. An exclusion constraint
-- can say "these two rows may not overlap"; it cannot say "the quantities of the overlapping rows
-- must sum to no more than forty". So the two cases are enforced in different places, and this
-- column is what tells them apart:
--
--   quantity = 1  (the one projector)   -> the database refuses the second allocation, under
--                                          concurrency, without the application being involved.
--   quantity > 1  (forty chairs)        -> BookingConflictPolicy does the arithmetic.
--
-- The gap is real and recorded rather than papered over: two concurrent requests for the last
-- twenty of forty chairs can both succeed. That is a chair shortage found at setup, not a hall
-- double-booked at examination time, and closing it would mean serialising every booking in the
-- estate behind one lock.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.booking_resource_allocations (
    id                    UUID PRIMARY KEY,
    booking_id            UUID         NOT NULL REFERENCES facilities.bookings (id),
    resource_id           UUID         NOT NULL REFERENCES facilities.bookable_resources (id),
    resource_code         VARCHAR(80)  NOT NULL,
    site_code             VARCHAR(40)  NOT NULL,
    starts_at             TIMESTAMPTZ  NOT NULL,
    ends_at               TIMESTAMPTZ  NOT NULL,
    setup_minutes         INTEGER      NOT NULL DEFAULT 0,
    teardown_minutes      INTEGER      NOT NULL DEFAULT 0,
    occupied_from         TIMESTAMPTZ  NOT NULL,
    occupied_to           TIMESTAMPTZ  NOT NULL,
    quantity              INTEGER      NOT NULL,
    is_exclusive          BOOLEAN      NOT NULL,
    released_with_booking BOOLEAN      NOT NULL DEFAULT FALSE,
    allocated_by          VARCHAR(160) NOT NULL,
    allocated_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_booking_allocations_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_booking_allocations_occupied
        CHECK (occupied_from <= starts_at AND occupied_to >= ends_at),
    CONSTRAINT ck_booking_allocations_quantity CHECK (quantity >= 1),

    CONSTRAINT ux_booking_allocations_exclusive EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(occupied_from, occupied_to, '[)') WITH &&
    ) WHERE (released_with_booking = FALSE AND is_exclusive = TRUE)
);

CREATE INDEX IF NOT EXISTS ix_booking_allocations_booking
    ON facilities.booking_resource_allocations (booking_id);
CREATE INDEX IF NOT EXISTS ix_booking_allocations_live
    ON facilities.booking_resource_allocations (resource_id, occupied_from)
    WHERE released_with_booking = FALSE;

-- ---------------------------------------------------------------------------------------------
-- booking_approvals
--
-- A record rather than a status. A booking that was approved and one that never needed approving
-- are different facts a single CONFIRMED status cannot tell apart; the presence of a row here is
-- the difference.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.booking_approvals (
    id          UUID PRIMARY KEY,
    booking_id  UUID         NOT NULL REFERENCES facilities.bookings (id),
    site_code   VARCHAR(40)  NOT NULL,
    decision    VARCHAR(20)  NOT NULL,
    reason      VARCHAR(2000),
    decided_by  VARCHAR(160) NOT NULL,
    decided_at  TIMESTAMPTZ  NOT NULL,
    -- Asymmetric on purpose: the requester of a rejected booking has to be told something, and
    -- nobody has ever needed an explanation for being given the room they asked for.
    CONSTRAINT ck_booking_approvals_reason CHECK (decision <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS ix_booking_approvals_booking
    ON facilities.booking_approvals (booking_id, decided_at);

-- ---------------------------------------------------------------------------------------------
-- booking_setup_tasks
--
-- Deliberately NOT S153 work orders. Routing a twenty-minute chair rearrangement through the CMMS
-- would give it an escalation ladder and a closure-evidence gate, fill the maintenance queue with
-- turnarounds, and put the failed standby generator on page four.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.booking_setup_tasks (
    id           UUID PRIMARY KEY,
    booking_id   UUID         NOT NULL REFERENCES facilities.bookings (id),
    room_id      UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    site_code    VARCHAR(40)  NOT NULL,
    description  VARCHAR(500) NOT NULL,
    due_by       TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    assigned_to  VARCHAR(160),
    completed_by VARCHAR(160),
    completed_at TIMESTAMPTZ,
    notes        VARCHAR(2000),
    -- A skipped task must say why, or it cannot be told from one nobody got to.
    CONSTRAINT ck_booking_setup_tasks_skip CHECK (status <> 'SKIPPED' OR notes IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS ix_booking_setup_tasks_booking
    ON facilities.booking_setup_tasks (booking_id);
-- The turnaround queue, ordered by when the room is needed rather than when the task was raised.
CREATE INDEX IF NOT EXISTS ix_booking_setup_tasks_queue
    ON facilities.booking_setup_tasks (site_code, due_by)
    WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------------------------
-- booking_no_shows
--
-- Written by the sweep alongside the status change rather than inferred later from a NO_SHOW
-- booking, because it captures what the status cannot: the room-time the booking took out of the
-- diary. Reconstructing that from bookings after the fact means re-deriving it every time somebody
-- asks, and archiving a booking would silently change the answer.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.booking_no_shows (
    id                  UUID PRIMARY KEY,
    booking_id          UUID         NOT NULL REFERENCES facilities.bookings (id),
    booking_reference   VARCHAR(40)  NOT NULL,
    site_code           VARCHAR(40)  NOT NULL,
    room_id             UUID         NOT NULL,
    room_code           VARCHAR(80)  NOT NULL,
    purpose             VARCHAR(20)  NOT NULL,
    window_start        TIMESTAMPTZ  NOT NULL,
    window_end          TIMESTAMPTZ  NOT NULL,
    minutes_held_unused BIGINT       NOT NULL,
    requested_by        VARCHAR(160) NOT NULL,
    recorded_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_booking_no_shows_minutes CHECK (minutes_held_unused >= 0)
);

-- NO_SHOW is terminal, so a booking can only be swept once. This is the belt to that braces: a
-- second sweep that somehow reached the same booking would fail rather than double-count somebody.
CREATE UNIQUE INDEX IF NOT EXISTS ux_booking_no_shows_booking
    ON facilities.booking_no_shows (booking_id);
CREATE INDEX IF NOT EXISTS ix_booking_no_shows_requester
    ON facilities.booking_no_shows (requested_by, recorded_at);
CREATE INDEX IF NOT EXISTS ix_booking_no_shows_site
    ON facilities.booking_no_shows (site_code, recorded_at);

-- ---------------------------------------------------------------------------------------------
-- Default S159 runtime configuration.
--
-- Inserted so the module works on a fresh database rather than refusing every booking because
-- nobody ran a seed script. Site-scoped values override these; see BookingConfiguration.
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('booking.approval.purposes', 'EXAMINATION,EVENT', 'STRING',
     'Booking purposes that always require an approver.'),
    ('booking.approval.duration-threshold', 'PT8H', 'DURATION',
     'A booking longer than this requires an approver whatever its purpose.'),
    ('booking.approval.all-in-examination-mode', 'true', 'BOOLEAN',
     'Require approval for every booking while the site is in examination mode.'),
    ('booking.no-show.grace', 'PT20M', 'DURATION',
     'How long after its start a confirmed booking may go unused before it is a no-show.'),
    ('booking.horizon.days', '365', 'INTEGER', 'How far ahead a booking may be made.'),
    ('booking.setup.default-minutes', '0', 'INTEGER', 'Default room setup buffer.'),
    ('booking.teardown.default-minutes', '0', 'INTEGER', 'Default room teardown buffer.'),
    ('booking.setup.examination-minutes', '30', 'INTEGER',
     'Setup buffer for an examination, which needs the layout changing.'),
    ('booking.teardown.examination-minutes', '30', 'INTEGER',
     'Teardown buffer for an examination, which needs the layout changing back.'),
    ('booking.sweep.batch', '200', 'INTEGER', 'Rows processed per reconciliation or no-show sweep.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);
