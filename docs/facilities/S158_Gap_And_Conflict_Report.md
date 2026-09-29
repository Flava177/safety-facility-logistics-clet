# S158 Space Planning & Move Management - gap and conflict report

What is built, what is not, what running it found, and what somebody has to decide before this goes
live. Honest by design: an unrecorded gap is one that will be discovered by a user.

---

## 1. Phase 1 conflict: S152 had no allocation register for S158 to sit under

The SRS describes S158 as "a scenario layer under CAFM/IWMS (S152) ... which remains the authoritative
current-state register", and makes committing a scenario either "a direct S152 update for a
like-for-like reassignment" or, via S176, an update at handover. Phase 1 S152 never built that
register: `FacilityRoom` carries only `costCentre`, a single string, with no concept of which
organisational unit occupies the space, at what headcount, or since when.

This is a real gap between the SRS's model and the Phase 1 codebase, not a Phase 2 oversight - the
shared foundation commit (435f11a) that six Phase 2 systems stand on does not add it either, because it
is specific to S158's need, not general estate infrastructure.

**What was done, under the brief's explicit extra-scope allowance:** a current-state allocation
register, `facilities.space_allocations`, was added to **S152/masterdata**, not to S158 - a register
S158 itself owned would be exactly the "parallel register" the SRS rules out. It has its own port
(`SpaceAllocationRepository`), its own service (`SpaceAllocationService`, audited
`SPACE_ALLOCATION_APPLIED`, authorised by `FACILITIES_SPACE_PLAN_COMMIT`), its own JPA adapter and
in-memory test double, and holds S158's scenario id **by value** (`source_scenario_id`, no foreign
key) - S152 does not depend on S158, matching the platform's own no-cross-module-FK rule. No existing
masterdata class was changed. `S152`'s architecture test boundary (masterdata does not depend on
readiness or the dashboard) is unaffected; a new `SpacePlanningArchitectureTest` rule instead holds that
S158 never reaches masterdata's persistence directly, only through the service.

This is flagged, not silently resolved, because it is a real design decision a Phase 2 reviewer should
see: S152's scope grew mid-build to make S158 buildable at all, on the SRS's own terms.

## 2. HRMS (S140) is not available - how units are identified without it

The SRS's Integrations row says S158 is "populated from HRMS (S140) per the mapping." HRMS is not built
anywhere in this codebase (Phase 1 or Phase 2) and is not one of the six Phase 2 IFIMP systems, so there
is no unit directory, no unit code list and no validation source for "which organisational units exist"
anywhere SFL can reach.

**What was done:** `allocatedUnit` (on a scenario line and on a register row) is free text, entered by
the planning officer modelling the scenario or the unit head submitting a space-change request, capped
at 200 characters, never validated against a controlled list. This means:

- Two scenarios can allocate "Registry" and "REGISTRY" as different units - nothing catches it.
- There is no way to ask "everything Bursary occupies across the estate" reliably by anything but an
  exact string match.
- Dashboard "compliance by unit" (`SpacePlanningDashboardService.complianceByUnit`) rolls up by the
  literal string, so the same typo problem shows there too.

This is a real limitation and is a straightforward remediation once HRMS - or even a manually
maintained unit list somewhere in SFL - exists: swap the `String` for a validated reference. Nothing in
the domain model needs to change shape; `allocatedUnit` would become an id resolved to a display name
at the edge, and the register/scenario tables would gain a real foreign key or held-by-value reference
depending on where the unit list eventually lives.

## 3. CAD floorplans are not integrated

Named as a "Dependency" in the SRS's Integrations row. Nothing in this build reads, stores or renders a
floorplan; every space is identified purely by its S152 room code, exactly as S159 booking already
does. A scenario comparison or the dashboard answers "which rooms" in a table, never on a plan. This is
consistent with S152 itself, which also has no floorplan capability - S158 does not regress anything
that existed, it simply does not add the one the SRS names as a dependency.

## 4. The utilisation metric the SRS does not define

SRS-SFL-S158-03's acceptance criterion - "booked at under 20% of capacity over a full reporting
period" - names a threshold but not a metric. "Booked" could mean frequency (how often), occupancy (how
full when used), or their product; the SRS text does not say, and the two readings disagree sharply (a
room used for half its available hours at 40% of capacity is "40% booked" under a naive frequency-only
reading, but 20% under the product this build uses).

**What was done:** `UtilisationPolicy` defines `utilisation rate = frequency rate x occupancy rate`,
documented in full in both the class javadoc and the runbook, precisely so this choice is visible and
arguable rather than buried in arithmetic. `occupancy rate` is built from the booker's own
`expectedAttendees` at booking time, because S159 has no occupancy sensor and records no actual head
count for an ordinary room - so "how full" is an estimate by the person who booked, not a measurement.
A reviewer who prefers a different metric (frequency alone, or a different persistence window for the
planned/actual gap) changes one class; nothing else in the module encodes the choice.

## 5. Two error codes added, unmapped in `FacilitiesApiExceptionHandler`

Appended at the end of the S158 block in `FacilitiesErrorCode` (the file this build may not otherwise
edit beyond that block):

- `SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE` - S176's intake could not be reached.
- `SPACE_UTILISATION_SOURCE_UNAVAILABLE` - S159 could not be read for reconciliation.

Both are "standard integration-unavailable handling" per S158-03's own Error States note ("None beyond
standard integration-unavailable handling under the platform's external-interface NFRs"). Neither is
mapped in `FacilitiesApiExceptionHandler` (out of this build's file boundary), so both currently answer
HTTP 400 by that handler's fallback. Both should map to **503 Service Unavailable** at merge - a
dependency being unreachable is not a caller error, and 400 would make a client retry with different
input, which cannot fix it.

## 6. Two audit actions added

Appended at the end of the S158 block in `AuditAction`: `OCCUPANCY_OVERRIDE_REQUESTED` and
`OCCUPANCY_OVERRIDE_WITHDRAWN`. The pre-seeded block already had `OCCUPANCY_OVERRIDE_RECORDED` for the
approval; these two cover the request half of the two-person act and the automatic withdrawal when an
overridden room's allocation changes underneath it, neither of which the seeded block anticipated.

## 7. No vendor-inbound forged-message test - by design, not by omission

The brief asks for "one vendor-inbound forged-message test where applicable." S158 has no vendor or
device integration of any kind - it is a pure Build module per the classification document ("no vendor
product fits this narrow, institution-specific role well") - so there is no inbound channel for
`VendorMessageVerifier` to guard and nothing to write a forged-message test against. Recorded here so
the omission reads as a decision, not an oversight.

## 8. S176 is built concurrently, in another worktree

Every `PHYSICAL_WORKS` commit and every "hand to construction" space-change request calls S176's real
`ConstructionProjectIntake` contract in-process (`S176ConstructionHandoffAdapter`), per ADR 0009's rule
that Phase 2 modules meet through the provider's published contract. Until that worktree merges, the
contract is served by the `UnbuiltConstructionProjectIntake` scaffold, which throws
`IllegalStateException` - this build's adapter catches exactly that and surfaces
`SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE` rather than a raw 500. `S158MandatoryScenariosTest` exercises
the real path against a controllable fake (`FakeConstructionHandoffPort`), not the scaffold, so the
scaffold's behaviour is exercised only implicitly (any `IllegalStateException`/`UnsupportedOperationException`
from the intake is caught the same way). This module also **provides**
`spaceplanning.application.contract.ScenarioHandover` for S176 to call at handover; the scaffold
(`UnbuiltScenarioHandover`) is deleted as required and `ScenarioHandoverService` implements it for
real, proven by `S158MandatoryScenariosTest`'s handover tests calling it exactly as S176 will.

## 9. Events are recorded, not delivered

Consistent with every other Phase 2 IFIMP module (see `S152_Event_Contracts.md` and ADR 0009 §6): this
service has no outbox drainer, so every `sfl.ifimp.*` event this module publishes sits in
`facilities.outbox_messages` as `PENDING` until one is built. Not a gap specific to S158.

## 10. Test-suite verification: a shared-infrastructure caveat

Every S158-scoped test passes reliably and repeatedly: `S158MandatoryScenariosTest` (26 tests across
all four requirements), `SpacePlanningArchitectureTest` (4), `SpacePlanningRowLevelSecurityTest` (3),
`SpaceScenarioControllerTest` (3), `OccupancyCompliancePolicyTest` (5) and `UtilisationPolicyTest` (7) -
45 tests, 0 failures, run standalone or filtered by `-Dtest='*SpacePlanning*,S158*,*OccupancyCompliance*,*UtilisationPolicy*'`.

The **whole-service** suite (`mvn test` with no filter, ~456 tests) was run repeatedly during this
build and, on every run, showed **0 test failures** but between 1 and 16 **errors**, always
`ApplicationContext` load failures against Postgres ("sorry, too many clients already" / "remaining
connection slots are reserved for roles with the SUPERUSER attribute"), never an `AssertionError`, and
never in the same test class twice in a row - `OptimisticLockingIntegrationTest`,
`FacilitiesRowLevelSecurityTest`, `JpaReadinessRepositoryAdapterTest` and `FacilitiesOutboxDrainerTest`
each took a turn across different runs. This reproduces **identically with every `spaceplanning` test
file excluded from the run**, which was done specifically to rule out this module as the cause: the
shared `sfl-facilities-e2e-postgres` container (`max_connections = 100`, unchanged) is being hit
concurrently by up to six worktrees' test suites during this Phase 2 build pass, and each distinct
`@SpringBootTest` configuration in this module's own (pre-existing) suite opens its own HikariCP pool
that stays open for the life of the JVM. This is capacity contention across the six parallel builds, not
a defect this build introduced or can fix from inside one module - it needs either a higher
`max_connections` on the shared container or the six suites not running concurrently, both outside this
build's file boundary and scope. Flagged for whoever merges the six worktrees: re-run the whole suite
once only one build's tests are running against the container at a time.
