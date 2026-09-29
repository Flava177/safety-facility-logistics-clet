# S176 Construction Project Management - gap and conflict report

Every place the SRS conflicts with the Phase 1/2 codebase, is silent, or depends on something not
built, stated plainly with what was done about it. Nothing here is silently resolved.

## Depends on something not built

**S164 Permit-to-Work is not built.** SRS-SFL-S176-01 requires "at least one linked Permit-to-Work
(S164) where the work type requires one" before a project starts. S164 is a separate Phase 2 SSEMP
system, not built in this pass or anywhere in this workspace. S176 defines the expected event payload
for `sfl.ssemp.permit-issued.v1`/`-suspended.v1`/`-extended.v1`/`-closed.v1` (documented in
`S176_Event_Contracts.md`), builds a local projection (`construction_permit_projection`) fed by an
`IntegrationEventHandler`, and links permits into it by reference. Because nothing publishes those
events, **every project whose configured work type requires a permit is correctly refused the start**
- fail-closed, not a defect. Flagged loudly in the runbook so it is not mistaken for a broken consumer
when S164 ships.

**S158 Space Planning is being built in a separate worktree.** S176 is both a provider
(`ConstructionProjectIntake`, for S158's "hand to Construction Project Management when physical works
are required" hand-off) and a consumer (`ScenarioHandover`, for confirming a committed scenario at
handover), through the interfaces only. Until S158 is merged: `ConstructionProjectIntake` works fully
(it is S176's own contract); `ScenarioHandover.find` always answers empty, so every handover with a
committed scenario records `scenarioConfirmation = UNRESOLVED`, and the handover still completes -
S152's register update is correct and authoritative regardless of S158's state. A retry endpoint
(`PATCH .../handover/scenario-confirmation`) exists for once S158 ships.

**S160a Access Control / S160 Visitor Management have no inbound consumer.** SRS-SFL-S176-02 says
site-access requests "resolve through Access Control (S160a) and Visitor Management (S160)." Both are
SSEMP systems in another deployable and neither consumes anything from S176. A local access grant is
recorded, checked against compliance locally (refusing on lapse), and published as
`sfl.ifimp.contractor-site-access-requested.v1` / `-suspended.v1`. Every grant is stored
`enforcement = RECORDED_NOT_ENFORCED`; the runbook, the dashboard's integration status, and a `WARN`
log on every suspension all say so. **The suspension is recorded, not enforced at the door**, until an
SSEMP consumer exists - stated exactly as the brief requires.

**S133 Vendor Master, S140 HRMS, S223 MDM are not integrated.** Contractor vendor references, HRMS
competency data and funding-source references are all held by value, entered by hand, and never
resolved or verified against those systems. `dashboard/integrations` reports each `NOT_INTEGRATED`
rather than silently treating a typed reference as validated.

**S136 Contract Lifecycle Management / S141 Payments (payment certification) are out of scope this
pass.** The SRS system summary names "payments via the Payment System (S141) per the mapping" among
S176's integrations, and the master-mapping module description ("Payment System (S141)") implies
payment certification is part of the system. This build implements the four functional requirements
(S176-01..04) as specified; certifying and releasing payment against a variation or a milestone is not
one of them and is not built. Reported `OUT_OF_SCOPE` on the dashboard, not silently absent.

## Where the SRS is silent, and the interpretation taken

**How many current permits does a project need?** SRS-SFL-S176-01 says "at least one linked
Permit-to-Work (S164) where the work type requires one" - singular "one," ambiguous against a project
declaring several permit-requiring work types. Interpreted as **one current permit per permit-requiring
work type**, not one permit covering the whole project: a hot-work permit does not authorise entry to a
confined space. Recorded in `ProjectStartPolicy`'s Javadoc and pinned by
`ProjectStartPolicyTest.each_permit_requiring_work_type_needs_its_own_current_permit`.

**What counts toward the variation-escalation threshold?** SRS-SFL-S176-03 says "cumulative variations
beyond a configured percentage of original budget" without saying gross or net of savings, or whether a
cost-reducing variation can itself need escalation. Interpreted as: **net of every approved variation's
signed delta**, against the baseline the accountable approver actually signed (not a since-revised
baseline); a cost-reducing variation never needs escalation on its own; while one variation is held for
escalation, every other pending variation on the project - including a further saving - is blocked
until it is resolved, because the ordinary approver would otherwise be deciding against a budget
position the escalated approver has not yet accepted. Recorded in `VariationEscalationPolicy`'s
Javadoc.

**When can a handed-over project close?** SRS-SFL-S176-04's workflow says the project closes "when
liability items are resolved," which is silent on whether closure may happen before the
defects-liability period itself has run its course. Interpreted as **no**: closing mid-period would
stop new defects being raised against a contractor still liable for them, so `ProjectClosurePolicy`
refuses closure until both are true - every item closed or deferred, and the liability period has
ended. This is a plain `INVALID_STATE_TRANSITION`, not one of the two named error states, because it is
a timing rule rather than the "items open" error the SRS names.

**Who may raise a defects-liability item?** Not named by role in the SRS. Given to
`FACILITIES_PROJECT_HANDOVER` (the Facilities Officer, whose user story is S176-04's) or, failing that,
the project's own manager - not opened to every role holding `FACILITIES_PROJECT_READ`, since raising
one commits an S153 work order.

**What does "responsible contractor(s)" require at start?** SRS-SFL-S176-01 lists "the responsible
contractor(s)" among what a project records, but does not state it as a start-gate condition the way it
states approval and permits. S176 refuses the start with `PROJECT_CONTRACTOR_UNASSIGNED` if none is
assigned - works with nobody accountable for them are not the documented scope the requirement is
protecting. This is an S176-appended error code (see below), not a pre-seeded SRS one.

## Codebase conflicts and pre-existing gaps noticed while building

**No forged-message (NFR-SEC2) test applies to this system.** S176 has no vendor-inbound endpoint - no
BMS/IoT, metering or scan-hardware traffic reaches this module, unlike S156/S157. The per-system
forged-message rejection test the house checklist and NFR-SEC2 ask for is not applicable here and is
recorded as such rather than a stub test standing in for it.

**`FACILITIES_PROJECT_HANDOVER` doubles as "who may raise a defect."** The permission was granted
(foundation commit) for handover; this build additionally gates defect-raising on it rather than
introducing a new permission, because both acts belong to the same Facilities Officer user story in
S176-04 and a new permission for one sub-step would be ceremony. Flagged in case a future review wants
them split.

## What was appended, for the merge

**AuditAction** (end of the S176 block): `PROJECT_CANCELLED`, `PROJECT_MILESTONE_ACHIEVED`,
`PROJECT_CONTRACTOR_ASSIGNED`, `PROJECT_PERMIT_STATUS_CHANGED`, `PROJECT_SCENARIO_CONFIRMATION_RECORDED`.

**FacilitiesErrorCode** (end of the S176 block): `PROJECT_CONTRACTOR_UNASSIGNED` - "No responsible
contractor is recorded against the project; works cannot start." **Not mapped in
`FacilitiesApiExceptionHandler`** (out of this module's file boundary); falls back to HTTP 400 until
mapped at merge - list it for UNPROCESSABLE_ENTITY (422), alongside the other S176 gate refusals.

## Files touched outside this module's boundary, and why

- `shared/domain/audit/AuditAction.java` - appended five constants at the end of the pre-seeded S176
  block only (listed above).
- `shared/domain/error/FacilitiesErrorCode.java` - appended one constant at the end of the pre-seeded
  S176 block only (listed above).

No other file outside `gh.edu.clet.sfl.facilities.construction` (main or test) was modified. The
pre-existing `UnbuiltConstructionProjectIntake` scaffold was deleted, as instructed, on becoming the
provider of `ConstructionProjectIntake`.
