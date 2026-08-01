-- =====================================================================================
-- SRS-SFL-S166-01 — bind a driver profile reference to the identity that signs in as it.
--
-- **The defect this closes.** "A driver sees only their own trips" was already written into the code,
-- in three places, all of them comparing the trip's driver `staff_reference` against
-- `ActorContext.actorId()`. That comparison can only ever be true under header authentication, where
-- `X-SFL-User` is echoed straight through and the caller therefore *chooses* the staff reference. With
-- a real token `actorId()` is `jwt.getSubject()` — a Keycloak user UUID. The two are different kinds
-- of string and will never match, so the moment A1 turned authentication on, every driver's per-record
-- check began failing closed against them and every supervising role kept seeing everything. The rule
-- read as implemented; it was enforced against nobody.
--
-- **Why a column rather than a claim.** Putting the staff reference into the token as a custom claim
-- would work and was the cheaper option. It was not taken because it puts fleet's join key inside the
-- identity provider: every new IdP, every realm re-import and every migration to a different product
-- then has to reproduce a mapper it has no reason to know about, and when it does not, the failure is
-- this same silent one. A column here means the binding is SFL's own data, visible in the register,
-- auditable, and changeable without touching the IdP.
--
-- **Zitadel.** `principal_subject` holds whatever the token's subject claim holds. Keycloak's `sub` is
-- a UUID; Zitadel's is a numeric string. Both fit, and the switch is a re-binding of existing rows
-- rather than a schema change — which is the point of storing the subject rather than a username or
-- an email, both of which change when a person is renamed or leaves.
--
-- **The backfill preserves today's behaviour exactly.** Under `sfl.security.enabled=false` the actor id
-- *is* the staff reference, so seeding `principal_subject` from `staff_reference` leaves local
-- development working as it does now. Under a real token nothing matches until an administrator binds
-- the profile, and an unbound driver sees no trips — fail-closed, deliberately, because the alternative
-- (an unbound driver sees all trips in their site) is the exact leak this exists to prevent.
-- =====================================================================================

ALTER TABLE fleet_logistics.driver_profile_references
    ADD COLUMN principal_subject VARCHAR(160);

-- Length is a CHECK rather than the column type doing double duty, matching the platform convention:
-- CHAR(n) is rejected by Hibernate schema validation, and a bare VARCHAR(n) gives a truncation error
-- instead of a constraint violation a caller can be told about.
ALTER TABLE fleet_logistics.driver_profile_references
    ADD CONSTRAINT ck_fleet_drivers_principal_subject_length
        CHECK (principal_subject IS NULL OR char_length(principal_subject) BETWEEN 1 AND 160);

-- One identity, one active driver profile. Without this a person bound to two active profiles would
-- see the union of both drivers' trips, and which one they "are" would depend on row order.
--
-- NULL is excluded rather than treated as a value: most profiles are unbound (a driver reference
-- exists for people who never sign in to SFL at all), and Postgres would otherwise let only one of
-- them stay NULL.
CREATE UNIQUE INDEX ux_fleet_drivers_principal_subject_active
    ON fleet_logistics.driver_profile_references (principal_subject)
    WHERE principal_subject IS NOT NULL AND lifecycle_status <> 'ARCHIVED';

-- The lookup runs on every trip list a driver opens.
CREATE INDEX ix_fleet_drivers_principal_subject
    ON fleet_logistics.driver_profile_references (principal_subject)
    WHERE principal_subject IS NOT NULL;

-- See the header: this is what keeps header-authenticated local development behaving as it did.
UPDATE fleet_logistics.driver_profile_references
   SET principal_subject = staff_reference
 WHERE principal_subject IS NULL
   AND lifecycle_status <> 'ARCHIVED';

COMMENT ON COLUMN fleet_logistics.driver_profile_references.principal_subject IS
    'The identity provider subject claim this driver profile belongs to. NULL means nobody signs in as '
    'this driver; such a profile is assignable but its holder sees no trips of their own.';
