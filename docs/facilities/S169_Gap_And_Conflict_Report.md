# S169 Cleaning & Janitorial Schedule Management - gap and conflict report

What is built, what is not, and what a reviewer has to decide before this is reported as complete.
Honest by design: an unrecorded gap is one somebody discovers in production.

---

## 1. Vendor Master (S133) is not integrated

The SRS names S133 as the source of the cleaning vendor base (SRS-SFL-S169-03). S133 is not built
anywhere in this programme and exposes no interface this module could call.

**What is built instead:** `VendorMasterPort`, implemented by `RecordedVendorMasterAdapter`, which
resolves a vendor reference only against `facilities.cleaning_vendor_master_references` - a local table
an authorised user maintains by hand (`POST /vendor-master-references`). Every scorecard response and
every known-reference listing carries `vendorMasterStatus`, a plain-English statement that S133 is not
integrated. `CLEANING_VENDOR_NOT_FOUND` is raised for any reference not recorded there.

**What a reviewer must not do:** report cleaning vendor onboarding as "integrated with S133" in any
status document. It is a manual register with an honest label, standing in for an integration that does
not exist yet. When S133 is built, only `RecordedVendorMasterAdapter` changes - the port, the
registration workflow and every test against it stay as they are.

## 2. The mobile checklist client does not exist

The SRS's S169 Integrations row names "mobile checklists" as a dependency. No such client is built in
this programme; every field interaction in this deliverable - marking an item done, attaching photo
evidence, starting or completing a task - is an API call under `/api/v1/facilities/cleaning/**`. A
cleaning crew today would need a bespoke thin client (even a browser form) driving those endpoints, or
this module has no way for a clean to actually be recorded.

This is not a defect in the build; it is the boundary the brief drew (S169 delivers the API, not the
device). It is called out here because it is easy to read "checklist execution with photo evidence" as
implying a device UI exists, and it does not.

## 3. Photo storage is references only - there is no object store

`PhotoEvidence` (and the `cleaning_task_checklist_items` columns behind it) is a reference string plus
a SHA-256 content hash, never image bytes. This build has no object store anywhere in the platform; the
reference points at wherever the (non-existent, see §2) mobile client's own storage puts the file. Two
consequences worth a reviewer's attention:

- **A photo reference cannot be verified by this service.** The hash proves that whatever bytes the
  client hashed match what it later claims to have stored; it does not prove the referenced object
  still exists, or ever did. There is no fetch-and-verify step anywhere in this build.
- **There is no recovery path for a lost photo.** If the referenced storage loses the object, the
  checklist item still reads as "done, photo attached" from this service's point of view - the runbook
  says so plainly rather than implying a repair procedure that does not exist.

## 4. Design decisions the SRS left open, and what was chosen

The SRS states the requirement and the acceptance criterion; several implementation questions below it
were not specified. Each was resolved with a stated reason, and each is flaggable if CLET's actual
janitorial operation disagrees.

- **What "the contracted response time" measures, for SRS-SFL-S169-03's acceptance criterion.** Chosen:
  the time from a reactive request being raised to its assignee starting work (not to assignment, which
  can happen instantly and would make "response time" measure nothing; not to completion, which is the
  separate completion-time term). A task still unstarted when its contracted response time passes is
  already in breach - the sweep records it without waiting for someone to start.
- **Whether cleaning capacity (S169-04) is one shared pool per site, or reactive/routine/event work each
  draws from separate crews.** Chosen: one shared pool - "configurable crews per site" reads most
  naturally as the total janitorial headcount available at any moment, and a room-level overlap rule
  (no two commitments on the same room) applies regardless of origin. The consequence: heavy routine
  scheduling at a site can, in principle, leave S173 with less capacity than expected. If CLET's
  janitorial staffing genuinely partitions "routine crew" from "event crew," this needs a second
  configuration key and a second peak-concurrency count, not a shared one.
- **Whether photo-required is per checklist item or a global toggle.** Chosen: per item, set when a
  checklist template is authored (`ChecklistTemplate.Item.photoRequired`) - "a configurable subset of
  items" reads as per-item, and a single site-wide flag could not express "photograph the toilets, not
  the corridor."
- **What counts as "repeated" for a low-rating flag (SRS-SFL-S169-02).** Chosen: a count and a window,
  both configured (`cleaning.feedback.low-rating-repeat-count`, default 3; `cleaning.feedback.low-
  rating-window`, default 30 days) - the SRS does not name a number, and Configuration Without Code
  (NFR 23.8) is the platform's stated answer to exactly this kind of gap.

## 5. Known gaps in the built behaviour

- **A capacity reservation S173 forgets to release is never auto-released.** `release` is idempotent and
  correct when called, but nothing in this build times out an unreleased `RESERVED` reservation whose
  event never happens. A coordinator-side crash before the release call leaves the room's cleaning
  capacity committed indefinitely. A scheduled reconciliation (e.g., release anything still `RESERVED`
  well past its window) was judged out of scope for this pass and is the most likely next addition.
- **A booking-origin cleaning task cancelled by hand does not automatically reappear** unless the
  booking itself is later confirmed or rescheduled again (which re-raises a missing live task as a side
  effect). There is no dedicated "re-raise this booking's cleaning" endpoint; the runbook names the
  manual workaround (raise an ad-hoc task).
- **The routine generation sweep is estate-wide and unpaged within one run.** For every active schedule
  it walks every room the schedule covers and every occurrence in the horizon, in one transaction (via
  `support.raise`, which itself is several statements). At CLET's actual estate size this has not been
  measured; a very large horizon or a great many overlapping schedules could make one sweep run long
  enough to matter. `cleaning.schedule.horizon-days` and `cleaning.sweep.batch` are both configurable,
  but the sweep itself does not yet chunk its own work across multiple transactions.
- **No endpoint deletes a checklist template or a schedule outright.** A schedule is deactivated
  (`active=false`); a template is superseded by a new version. This is a deliberate choice (nothing
  that already referenced a version should have it vanish), stated here because "delete" is a
  reasonable first thing an operator looks for and will not find.

## 6. Cross-service dependencies that do not yet consume or publish

- **S173 (Event Logistics & Set-Up Workflow)** is being built in a sibling worktree at the same time as
  this one. The provider side of `EventCleaningCapacity` is real and tested in this build; whether
  anything actually calls `reserve`/`find`/`release` in production depends on S173's own build landing
  with a matching adapter. Until then, `cleaning_capacity_reservations` will be empty in any environment
  that has S169 but not S173 - that is expected, not a defect to chase.
- **No drainer publishes this service's outbox yet** (platform-wide, not S169-specific - see
  `docs/facilities/S152_Event_Contracts.md` §Status). The seven events in
  `docs/facilities/S169_Event_Contracts.md` are written, correctly shaped, and inert until one exists.

## 7. What was NOT modified, and why

No file outside `cleaning/**`, this module's own `V19` migration, the ends of the `AuditAction` and
`FacilitiesErrorCode` S169 blocks, `S169MandatoryScenariosTest`, `support/InMemoryCleaningRepository`
and `support/InMemoryCleaningVendorMasterPort`, and this module's own docs/runbook was touched. In
particular:

- `UnbuiltEventCleaningCapacity` was deleted, as instructed for the contract's provider.
- `BookingLifecycleObserver` and `booking/**` were read, never edited; `CleaningArchitectureTest` proves
  `booking..` does not depend on `cleaning..` and that only the two named integration adapters
  (`S159BookingDirectoryAdapter`, `BookingCleaningObserver`) import `booking` at all.
- No new `SflRole`, `SflPermission` or `FacilitiesPermissionMatrix` grant was needed - every permission
  S169 uses (`FACILITIES_CLEANING_READ/SCHEDULE_MANAGE/REQUEST/TASK_EXECUTE/TASK_SUPERVISE/
  FEEDBACK_SUBMIT/VENDOR_MANAGE`) was already seeded by the foundation commit and already granted to
  the roles this module's tests exercise.

## 8. New `AuditAction` and `FacilitiesErrorCode` entries

Five `AuditAction` constants were appended to the end of the pre-seeded S169 block (none pre-existed for
these): `CLEANING_TASK_RESCHEDULED`, `CLEANING_COMPLETION_DISCREPANCY_RECORDED`,
`CLEANING_LOW_RATING_REVIEWED`, `CLEANING_VENDOR_MASTER_REFERENCE_RECORDED`,
`CLEANING_VENDOR_STATUS_CHANGED`. No new `FacilitiesErrorCode` was needed - the four pre-seeded codes
(`CLEANING_BOOKING_UNLINKED`, `CLEANING_CHECKLIST_INCOMPLETE`, `CLEANING_RESOURCING_CONFLICT`,
`CLEANING_VENDOR_NOT_FOUND`) covered every error state this build raises.
