# PROMPT 2 — Role-based portals for the stakeholders the SRS names

> Run after [`PROMPT_1_Close_Release_1_Gaps.md`](PROMPT_1_Close_Release_1_Gaps.md). It assumes A0
> (per-record narrowing), A1 (authentication on) and the S159 screens have landed.
>
> **A0 landed on 31 July 2026** — the driver is now scoped to their own trips and logbooks, enforced
> on records as well as collections, via `FleetAccessPolicy.requireRecordScope` and
> `FuelAccessPolicy.requireOwnRecord`. So the driver portal below can rely on "my" being enforced by
> the service. **Two caveats that change what you can build:** dispatch is *not* narrowed — the
> centre and handler fields are free text with no principal binding, so a `CENTRE_MANAGER` still sees
> the whole register, and their portal cannot claim otherwise until that schema decision is taken. And
> a driver still reads every fuel *transaction* at their site, so do not present that list as "mine".

You are working in the CLET Cluster 9 SFL repository. Read `CLAUDE.md` and `solution.md` first.

## Authority, in precedence order

1. **`docs/System Mappings and SRS/CLET_Comprehensive_Digital_System_Mapping_v2.docx` §30A.6.9
   (Cluster C9)** — the source of truth for system identity, cluster ownership and phase. The SRS
   says so itself at §1.5.
2. **`docs/System Mappings and SRS/SFL_SRS.docx` §2.3 User Classes and Characteristics** — the eleven
   user classes Phase 1 is contracted to serve.
3. Code — evidence of what is implemented, never a specification.

Both `.docx` files are binary. Extract them to text with:

```
python -c "import zipfile,re,sys;F=sys.argv[1];z=zipfile.ZipFile(F);x=z.read('word/document.xml').decode('utf8','replace');x=re.sub(r'</w:p>','\n',x);open(F+'.txt','w',encoding='utf8').write(re.sub(r'<[^>]+>','',x))" "<file>.docx"
```

### The eleven SRS §2.3 user classes

Facilities Director / Manager · Facilities Officer · Maintenance Technician / Vendor ·
Room Requester / Host · Security Director / SOC Operator · Visitor Desk Officer · HSE Officer ·
Fleet / Logistics Officer · Emergency Coordinator · Auditor / Compliance / Data Protection Officer ·
System Administrator.

Note that **Visitor Desk Officer** serves S160, which is not built — that class stays unserved and
should be recorded as such rather than quietly dropped from the matrix.

### Mapping ownership — who signs off which portal

| Unit | Systems |
|---|---|
| Building & Infrastructure Unit | S152, S153, S159 *(S159 co-owned GSL, CDT)* |
| Health, Safety & Security Unit | S160, S160a, S161, S162, S162a, S163, S174 *(S160a co-owned DTI/P&C; S161 C&A; S163 P&C/C&A; S174 CCP/DTI)* |
| Transportation & Logistics Unit | S166, S168_fuel, S171 *(S171 co-owned CDT)* |

All thirteen sit under F&L — the Safety, Facilities & Logistics Directorate.

## The problem

`frontend/sfl-operations-ui` has five modules — facilities (41 `.tsx`), fuel (24), fleet (22),
dispatch (15), emergency (15) — plus whatever S159 added in Prompt 1. Every one of them was designed
for the **operator** persona: Facilities Manager, Fleet/Logistics Officer, Emergency Coordinator.

There are **26 roles** in `SflRole` and **145 permissions** in `SflPermission` across four service
matrices. Several roles hold real, non-empty permission sets and have no view built for them. They
sign in, land on an operator dashboard built for somebody else, and see mostly-hidden controls.

S153 already solved this for two roles — a vendor technician sees only work assigned to them, a
requester only the faults they reported — and S159 did the same for booking requesters. This pass
extends that idea to the rest of the estate.

---

## Task 1 — The trace matrix, written before any code

Produce `docs/frontend/SFL_Role_Portal_Trace_Matrix.md`. **One row per `SflRole` value — all 26**, with:

| Role | SRS §2.3 user class it serves | Mapping unit | Systems entitled | Permissions actually held | Portal it lands on | Basis |

`Basis` is exactly one of:

- **SRS** — the role maps cleanly onto a §2.3 class. Build it.
- **Derived** — the role is a narrower specialisation of a §2.3 class within one system, and the
  mapping puts that system under a unit that would staff the specialisation. Build it, and state the
  derivation in one sentence. `MAILROOM_OFFICER` ⊂ Fleet/Logistics Officer for S171 is the clean
  example: the mapping's S171 entry names mailroom and courier despatch explicitly.
- **Deviation** — no §2.3 class and no honest derivation. **Do not invent a portal.** Record the row,
  name the owner who must decide, and leave the role on the narrowest existing view.

Get the entitlement facts from the code, not from memory: `roleSystems` in
`shared/layout/programmeModel.ts` maps 22 roles; the remaining four — `SFL_ADMIN`, `DTI_ADMIN`,
`AUDITOR`, `COMPLIANCE_OFFICER` — are deliberately handled by `crossProgrammeRoles`, which returns
every programme. That is by design, not an omission; do not "fix" it.

### `FLEET_DRIVER` is the case that must not be fudged

The SRS gives the driver **no §2.3 user class**, and every `SRS-SFL-S168fuel-*` requirement is written
"As a Fleet or Logistics Officer". The mapping's S168_fuel entry reads "Per-vehicle fuel consumption,
fuel-card reconciliation, anti-fraud controls" — an operational-owner framing, not driver self-service.

What *does* exist is `FLEET_DRIVER` in `SflRole`, holding `FLEET_VEHICLE_READ`, `FLEET_TRIP_READ`,
`FLEET_INSPECTION_RECORD` and `FLEET_EVIDENCE_REGISTER` in the fleet matrix, and `FUEL_TRANSACTION_READ`,
`FUEL_LOGBOOK_READ`, `FUEL_LOGBOOK_CREATE` and `FUEL_LOGBOOK_SUBMIT` in the fuel matrix — plus a
logbook somebody plainly has to fill in for the anti-fraud control to mean anything.

So: build the driver view **narrowly**, as the minimum surface those eight permissions imply, and
record it as a **Deviation** with the Transportation & Logistics Unit named as the owner who must
confirm the user class. Do not hand the driver a dashboard of estate-wide figures on the argument that
they hold `FLEET_VEHICLE_READ`.

Note also that **`S168a Trip / Driver-Booking Portal` is a Phase 2 system** in the mapping. The
driver-facing booking product is already scoped and deferred, which is a positive reason to keep this
pass to logbook-and-assignment self-service and not drift into trip booking.

The same test applies to `CENTRE_MANAGER`, `MAILROOM_OFFICER`, `DISPATCH_CONTROLLER`,
`LOGISTICS_COORDINATOR` and `SECURITY_OFFICER`, all added additively under S171 decision D-14. Some
derive cleanly from Fleet/Logistics Officer; say which, and say it in one sentence each.

---

## Task 2 — Build the portals

For each row whose basis is **SRS** or **Derived**, deliver a landing view that answers *what do I have
to do today*, not *what is the state of the estate*. Reuse the existing shared shell, tables, status
chips, dialogs, workflow timelines and error model — this is new composition within one design system,
not a second front end.

Every item gated on its **real service permission**, read from `/actor/permissions`, so the sidebar
narrows with the actor rather than offering screens the service will refuse.

Minimum set:

- **Driver** (`FLEET_DRIVER`) — my assignments today; my open logbook; start and submit a logbook with
  odometer; record a pre-trip inspection; my fuel transactions. "My" is enforced by the service after
  Prompt 1 A0, not by a client-side filter.
- **Mailroom officer** (`MAILROOM_OFFICER`) — inbound items awaiting registration; items awaiting
  staging; register an item; today's distribution.
- **Centre manager** (`CENTRE_MANAGER`) — consignments inbound to my centre; confirm receipt; record a
  variance or a broken seal; outstanding returns. This is the S171 destination-receipt persona, and
  the seal-variance path is the one that escalates to SSEMP.
- **Dispatch controller / logistics coordinator** — both already hold the full controller permission
  set and the dispatch module is close to their view. Confirm it is the right landing and **record
  that finding** rather than duplicating fifteen screens.
- **Room requester / host** (`IFIMP_REQUESTER`) — my bookings and my reported faults, in one place.
  The backend already narrows both per record: `BookingApplicationService.requesterFilter` and
  `FacilityFaultService.requesterFilter`. Note that `IFIMP_REQUESTER` deliberately does **not** hold
  `FACILITIES_BOOKING_CANCEL` — cancelling one's own booking is allowed by the per-record rule
  instead, so a missing permission here is not a missing capability.
- **Maintenance technician / vendor** (`IFIMP_TECHNICIAN`, `VENDOR_TECHNICIAN`) — S153 already narrows
  these correctly. Confirm the landing is their queue rather than the estate dashboard, and fix the
  landing if it is not.
- **Auditor / compliance** (`AUDITOR`, `COMPLIANCE_OFFICER`) — one cross-system evidence and audit view
  rather than four per-module ones: chain verification, evidence search, export requests with a
  recorded justification and recipient, and the denial records. These two are `crossProgrammeRoles`,
  so they see every system — which is exactly why a single consolidated view is right.
- **Command** (`COMMAND_ROLE`) — read-only cross-programme posture, per the SRS §2.8 command contexts.
- **Reporting viewer** (`FLEET_REPORTING_VIEWER`, `HSE_MANAGER`) — read-only landings that do not offer
  actions the service will refuse.
- **System administrator** (`SFL_ADMIN`, `DTI_ADMIN`) — configuration, runtime thresholds, integration
  health. Check what already exists in the facilities configuration screen before building.

---

## The rules this codebase has already paid for

1. **Adding a module means adding its permissions source.** `shared/layout/actorPermissions.ts` merges
   each service's `/actor/permissions` into one set, and its fail-open is per-***set***, not
   per-service: once **one** source answers, `granted` is non-null and anything absent from it is
   treated as **denied**. A module missing from `SOURCES` does not go "unknown" — it goes denied, with
   every gated control silently absent and no error anywhere. This is exactly what happened when S152
   arrived: fleet answered, facilities was not asked, and every `FACILITIES_*` permission evaluated
   false. It is documented at the call site. If this pass introduces a new deployable, it goes in
   `SOURCES` **in the same commit**.
2. **A permission denial hides the control; a state or data shortfall disables it with the reason.**
   A technician was once shown a Close button disabled with "You do not have permission" —
   permanently, on every job, forever.
3. **Empty states describe what is visible to you, not what exists.** "Every work order at this site is
   closed" is a confident falsehood to a contractor who sees only their own. This matters more in this
   pass than any previous one, because almost every portal here is a narrowed view.
4. **Nothing the service derives is recomputed in the browser.** `overdue`, `minutesOverdue`,
   `assignable`, `dueForGeneration` and their kin come down the wire. A browser working out for itself
   what is late will disagree with the sweep the moment a workstation clock drifts — and the sweep is
   the one that notifies people.

---

## Verification — driving it, not only rendering it

A green build, a green typecheck and green tests have hidden a module-wide defect in this repository
**twice**, and both times it was found by clicking a row.

Against real PostgreSQL, with the services running and authentication **on** after Prompt 1 A1, sign
in as **each** role you built for and confirm:

- the sidebar shows only their sections;
- the landing is useful on arrival, not an empty operator dashboard;
- every control offered succeeds;
- no control that would return 403 is offered at all;
- narrowed views prove the negative — a second driver's logbook and a second centre's manifest are
  refused **by id**, with the service's own error wording, not merely absent from a list.

## Deliverables

- `docs/frontend/SFL_Role_Portal_Trace_Matrix.md` — all 26 roles, basis stated, every Deviation
  owner-named.
- The portals and their tests.
- `docs/frontend/SFL_Operations_UI_Module_Playbook.md` updated with the portal pattern.
- A gap report in the house style: what was built, what was not and why, and what driving it found.
- `solution.md` — one pass entry, in the existing voice.
- **No AI-attribution trailers in any commit, PR body or changelog entry.**
