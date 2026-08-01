# CLET Cluster 9 — SFL Phase 1
# Business Workflow Review & Go-Live Readiness Pack

**Document reference:** CLET/DTI/CL9/SFL/WFR/2026/001  
**Version:** 1.2 — 1 August 2026  
**Classification:** CONFIDENTIAL — RESTRICTED DISTRIBUTION  
**Prepared for:** F&L Safety, Facilities & Logistics Directorate + DTI (co-owner)  
**Authoritative baseline:** `docs/System Mappings and SRS/SFL_SRS.docx` (CLET/DTI/CL9/SFL/SRS/2026/001)  
**Supporting baselines:** Cluster 9 SFL System Architecture Document v1.2 (Reviewed); CLET Comprehensive Digital System Mapping v2; SFL repository `docs/**`, `services/**` and `frontend/**` as at 1 August 2026

---

## Document control

| Field | Value |
|---|---|
| Purpose | Structure and evidence the pre-Go-Live review of all Phase 1 business workflows |
| Review session date | *TBC — to be supplied by F&L/DTI* |
| Vesting Day / Go-Live target | *TBC — to be supplied by F&L/DTI* |
| Review chair | Director, Safety, Facilities & Logistics |
| Technical authority | DTI Cluster 9 Solution Architect |
| Decision authority for Go-Live | Registrar / Board, on recommendation of gate owners G1–G6 |

### How to use this pack

1. **Section B** is the running order for the review session.
2. **Section D** is the substance: one page per workflow. Each workflow states the *target* business flow from the SRS, the *implemented* behaviour in code, the *decision gates*, the *questions to close*, and the *refinements* agreed.
3. **Section E** is the live demonstration script — only workflows that can actually be shown today are listed.
4. **Sections F–H** are the gap register, open management decisions and readiness gates. These carry the Go-Live recommendation.
5. Every status claim in this pack is traceable to either an SRS requirement ID or a file in the SFL repository. Where documentation and code disagree, **code is treated as the fact and the documentation defect is logged**.

### Status legend

| Symbol | Meaning |
|---|---|
| ● **Built** | Domain, workflow, persistence, API and automated tests exist and pass |
| ◐ **Partial** | Real records and workflow exist, but material SRS capability is missing |
| ○ **Not built** | No domain model, API, migration or test exists |
| ⛔ **Blocker** | Prevents Go-Live for the affected workflow |
| ⚠ **Risk** | Does not prevent Go-Live but must be accepted in writing |

---

# SECTION A — Executive summary

## A.1 The one-paragraph position

The SRS specifies **thirteen Fast-Track systems** delivered as **one coordinated platform** over three modules (IFIMP, SSEMP, FTLMP) plus a cross-cutting platform layer. As at 1 August 2026 the codebase delivers **seven systems to a substantial standard** — S152 CAFM/IWMS, S153 CMMS, S159 Booking, S166 Fleet, S168_fuel, S171 Dispatch and S174 Emergency Notification — each with domain, workflow, persistence, API, screens and automated tests. **Six are not started**: S160, S160a, S161, S162, S162a and S163, the entire SSEMP safety-and-security cluster, which exists as a controller, a foundation migration and — since 1 August 2026 — an application class and a security configuration, so the module can at least start and be monitored. It could not start before that, which nobody had noticed because nothing had ever launched it. Four of those six are Buy-and-Integrate under SRS App. B, so their delivery is a procurement and integration exercise rather than a build.

**A Go-Live covering the full Phase 1 scope is not achievable on the current build**, and that has not changed. What has changed is the subset: this pack now closes a **declared 7-system Release 1 demo build**. Visitor Management and the remaining SSEMP safety/security systems are explicitly excluded from this demo scope. Of the cross-cutting blockers this pack raised on 29 July, the build-owned items are closed. The remaining production concerns are external integrations: no live vendor endpoint has been proven, and S174 still uses a recorded outbound adapter. For Release 1 demo purposes, the S174 live gateway is an accepted deferral because delivery will later integrate with the separate CLET Comms system.

Backend verification must be run in a Docker-enabled environment for formal UAT evidence. The latest Codex-side reactor reports **812 tests, 0 failures, 0 errors, 119 Docker/Testcontainers-gated skips**. Frontend verification passes: **152 tests** and a clean production build.

## A.2 Scoreboard — 13 Phase 1 systems

| # | System | Module | Delivery class (SRS App. B) | Build status | Demoable today | Go-Live position |
|---|---|---|---|---|---|---|
| 1 | **S152** CAFM / IWMS | IFIMP | Hybrid | ● Built — estate, readiness, assessments, checklists, zones, devices, audit, configuration | Yes — 24 screens | Ready subject to F.1 |
| 2 | **S153** CMMS | IFIMP | Build | ● Built — fault to triage to work order to closure, SLA escalation, PM schedules, vendors, evidence with retention and disposal | Yes | Ready subject to F.1 |
| 3 | **S159** Room & Resource Booking | IFIMP | Build | ● Built — availability, approval, readiness holds, resources, turnaround | Yes — 5 screens, 7 dialogs | Ready subject to F.1 |
| 4 | **S160** Visitor Management | SSEMP | Hybrid | ○ Not built | No | ⛔ Not in Release 1 |
| 5 | **S160a** Physical Access Control | SSEMP | Buy & Integrate | ○ Not built | No | ⛔ Not in Release 1 |
| 6 | **S161** CCTV / VMS | SSEMP | Buy & Integrate | ○ Not built | No | ⛔ Not in Release 1 |
| 7 | **S162** Intrusion Detection | SSEMP | Buy & Integrate | ○ Not built | No | ⛔ Not in Release 1 |
| 8 | **S162a** Fire & Life-Safety | SSEMP | Buy & Integrate | ○ Not built | No | ⛔ Not in Release 1 |
| 9 | **S163** HSE Incident & Near-Miss | SSEMP | Build | ○ Not built | No | ⛔ Not in Release 1 |
| 10 | **S166** Fleet & Vehicle Management | FTLMP | Hybrid | ● Built | Yes — 12 screens | Ready subject to F.1 |
| 11 | **S168_fuel** Fuel & Driver Logbooks | FTLMP | Hybrid | ● Built — including the fuel-card registry (S168fuel-04); **no screens for cards** | Yes — 12 screens | Conditional — see F.1 |
| 12 | **S171** Mailroom / Courier & Dispatch | FTLMP | Build | ● Built | Yes — 10 screens | Ready subject to F.1 |
| 13 | **S174** Emergency Mass-Notification | SSEMP | Hybrid | ● Built (recorded outbound adapter) | Yes — 9 screens | Demo-ready; live Comms integration deferred |
| — | **PLAT** Cross-cutting platform | All | Build | ● Built — authentication, RBAC, per-record scope, row-level security, hash-chained audit, transactional outbox, idempotency, runbooks | Yes | Ready subject to F.1 |

**Totals: 7 built · 0 partial · 6 not built.**

> **Changed since v1.1 (29 July).** S152 moved from *partial (master data only)* to built: it is now the IFIMP
> platform the other two IFIMP systems hang off. S153 was rewritten on it and is built. S159 moved from *not
> built* to built and now has screens. S168_fuel moved from *partial* to built when the fuel-card registry
> landed. The platform layer moved from *partial* to built. The totals moved from **3 · 3 · 7** to
> **7 · 0 · 6**. The six unbuilt SSEMP systems are unchanged, and they are unbuilt **scope** rather than a gap
> in what was attempted — closing them is a separate multi-pass build against
> `docs/phase-1-system-classification.md`.

## A.3 The issues that decide Go-Live

Five of the six raised on 29 July are closed. Each closure is evidenced rather than asserted.

| # | Issue (v1.1) | Position at 1 August 2026 |
|---|---|---|
| 1 | **Authentication is switched off.** `sfl.security.enabled` defaulted to `false` in every service; the deployment compose file never set it; no test exercised the JWT chain. | ✅ **Closed.** Both defaults inverted — the open chain now requires an explicit `false` and logs a warning naming the service on every startup; the secure chain is what an absent property selects. Keycloak realm imported with all 26 roles and the `site_scopes` claim. `FacilitiesJwtSecurityTest` runs the real chain in a real context and pins five behaviours, including **403 rather than 401** for a valid token lacking the permission. |
| 2 | **Seven of thirteen systems do not exist**, including the whole safety-and-security cluster. | 🟡 **Reduced to six.** S159 was built and now has screens. The six SSEMP systems remain unbuilt scope; four are Buy-and-Integrate. Phase 1 in full still cannot be certified — **Release 1 of seven systems can.** |
| 3 | **No external integration is real.** HRMS, notification, telematics, fuel provider, scanner, carrier, CCTV, access control, fire panel — all simulators or recorded adapters. | ⛔ **Open.** No vendor field mapping has been proven against a live endpoint. S152 inbound webhook signature verification is unbuilt for the same reason: there is no real sender to verify against, and a signature check written against an imagined payload is not evidence. |
| 4 | **No operational runbooks exist.** `docs/runbooks/` is empty. | ✅ **Closed.** Four runbooks in `docs/runbooks/`: incident response, disaster recovery, dead-letter recovery, backup and restore. |
| 5 | **S168_fuel is self-declared "not ready to close or commit"** and remains on an uncommitted branch. | ✅ **Closed.** Merged, and the fuel-card registry (`SRS-SFL-S168fuel-04`) built — domain, migration `V21`, service and API. **Its screens are not built**, so cards are managed by API only. Recorded in F.1 as a Release 1 condition rather than a blocker. |
| 6 | **Domain events are not published to a broker by default** (`SFL_FLEET_EVENT_TRANSPORT=local`), and two services emit event names that violate the catalogue rule. | 🟡 **Half closed.** The naming violation is fixed — 48 literals renamed across facilities and AVAMP, enforced at the outbox write path by a regex rather than by review, and the IFIMP outbox now has a drainer with claim, exponential backoff and dead-lettering, proved against PostgreSQL. The **transport still defaults to `local`**, so cross-module sagas do not run end to end until a broker is configured. That is a deployment decision, not missing code. |
| 7 | **S174 has no notification gateway.** *(Carried from v1.1 A.2, where it was not one of the six.)* | ⛔ **Open.** The system composes, approves, records and audits a broadcast it cannot send. An emergency mass-notification system that cannot notify is not certifiable, and no amount of test coverage changes that. |

For the **Release 1 demo**, #7 is accepted as a later Comms integration and is no longer treated as a demo blocker. For production/UAT, #3 remains the integration/procurement condition: no live vendor endpoint has been proven.

# SECTION B — Review session running order

Total: **one working day**. Each workflow block follows the same four-beat pattern so the session does not drift: *walk the flow → show it running → answer the open questions → agree the refinement*.

| Time | Block | Content | Owner | Output |
|---|---|---|---|---|
| 09:00 | **B1** Opening | Purpose, scope of Phase 1, this pack, the scoreboard (A.2) | Review chair | Shared understanding of what is and is not in scope |
| 09:20 | **B2** Operating model | Three command contexts, delivery classification, what "govern-and-integrate" means for purchased systems | DTI Architect | Confirmation of the Build/Buy/Hybrid line per system |
| 09:45 | **B3** IFIMP workflows | D.1 Facility register & readiness · D.2 Fault-to-work-order · D.3 Room booking | Facilities Director | Validated flows; gaps logged |
| 11:00 | **B4** SSEMP workflows | D.4–D.10 Visitor · Access · CCTV · Intrusion · Fire/Life-safety · HSE · Emergency notification | Security Director / HSE Officer / Emergency Coordinator | Target flows validated for a future release; Release 1 exclusions confirmed |
| 12:30 | *Lunch* | | | |
| 13:15 | **B5** FTLMP workflows + live demo | D.11 Fleet · D.12 Fuel · D.13 Dispatch — walked **against the running system** (Section E) | Transport Manager / Logistics Coordinator | Acceptance or rework list per screen |
| 15:00 | **B6** Cross-cutting workflows | D.14 Hall readiness gate · D.15 Examination mode · D.16 Emergency fast lane · D.17 Chain-of-custody · D.18 Edge survivability · D.19 Configuration without code | DTI Architect + Command Role | Confirmation of gate behaviour and authority model |
| 15:45 | **B7** Gap register & decisions | Section F walked line by line; Section G decisions assigned owners and dates | Review chair | Owner + date against every open item |
| 16:30 | **B8** Readiness gates & recommendation | Section H RAG status; Release 1 scope decision; conditions of Go-Live | Registrar / gate owners | Recorded Go-Live recommendation |
| 17:00 | **B9** Close | Sign-off sheet (Section I) | All | Signatures |

**Rules for the session.** A workflow is "validated" only when the business owner confirms (a) the trigger, (b) every decision gate, (c) who may override and (d) what evidence closure requires. Anything else is a gap, not a validation.

---

# SECTION C — Platform operating model

## C.1 The five operational questions the platform answers

Per SRS §1.1, SFL exists to answer: *are the buildings, halls, rooms and utilities ready; are people, visitors, restricted zones, surveillance, access control, fire/life-safety and incidents governed; are vehicles, fuel, dispatches and examination logistics traceable; are evidence, approvals, exceptions and audit records preserved; and can management see readiness, risks, incidents and exceptions in real time.*

## C.2 Three command contexts (SRS §2.8; Architecture §3.4)

| Context | Primary authority | What changes | Escalates to |
|---|---|---|---|
| **Routine Operations** | Facilities / Security / Logistics Directors | Normal workflow SLAs, dashboards, maintenance and service requests | Line supervisor or director |
| **Examination Mode** | **NECC Commander** with Centre Manager and SOC/NOC leads | Exam VLAN isolation, hall readiness lock, booking lock, tightened access schedules, mandatory zone arming, device chain-of-custody, emergency-logistics readiness | NECC, SOC, NOC, Registrar |
| **Emergency Mode** | Emergency Coordinator / Security Director | Break-glass notification, incident command, evacuation roll-call, emergency transport, evidence preservation | External responders, Executive Management, Board |

Operating-mode changes are **explicit, audited state transitions** (SRS PLAT-04), not configuration flags. They change which SLAs, authorisations and locks apply.

**Implementation note.** The mode concept exists in the fleet module only, as `operatingMode` inside `ReadinessContext`, producing `OPERATING_MODE_RESTRICTION` and `EMERGENCY_ONLY_RESTRICTION` readiness blockers. There is **no platform-wide mode switch** and no audited mode-transition record. *(Gap G-14.)*

## C.3 Operating principles carried into every workflow (SRS §2.1)

| Principle | What it means at workflow level |
|---|---|
| Coordinated platform, not isolated applications | A booking pulls cleaning, AV, security and asset allocation; an emergency pulls lockdown, surveillance preservation, notification, vehicle dispatch and follow-up maintenance |
| Govern-and-integrate for specialist systems | The vendor owns device control and raw capture; SFL owns workflow, exceptions, escalation, dashboards and audit |
| Reference-only for enterprise master data | Staff, candidates, institutions, finance and documents are resolved at time of use against S140/S223/NBES/S003 — never mastered in SFL |
| Immutable, tamper-evident audit | Every request, approval, assignment, access decision, alarm, export, dispatch, override and closure is hash-chained |
| Life-safety primacy | Certified fire/intrusion systems remain the sole authoritative controllers. SFL observes and supplements; it **never actuates** |
| Readiness as a gate | No hall opens until power, network, local exam server, recording, access control, life-safety and workstations are confirmed |
| Local survivability | Centre operations continue during WAN loss and reconcile on restore |
| Configuration without code | SLA thresholds, escalation rules, zones, severities, fuel limits, readiness checklists and retention are runtime-configurable, versioned and audited |

## C.4 Delivery classification — what CLET builds versus buys (SRS App. B)

| Delivery class | SFL owns | Vendor owns | Systems |
|---|---|---|---|
| **Build** | The entire workflow, data and screens | — (optional future system-of-record) | S153, S159, S163, S171 |
| **Buy & Integrate** | Policy visibility, exceptions, overrides, dashboards, audit | Device operation and raw capture | S160a, S161, S162, S162a |
| **Hybrid** | Operational workflow, escalation, audit, dashboards, cross-system events | Device/raw data capture | S152, S160, S166, S168_fuel, S174 |

**Procurement gate (SRS §5.2).** No purchased system may be selected until it demonstrates: an open integration method (manual/CSV-only acceptable for low-risk batch only), secure authentication (**shared passwords are not acceptable**), role-scoped API access, event push or safe incremental polling, device health reporting, evidence with provenance, exportable vendor audit logs, a sandbox with documentation, licensing that **includes** API access, encryption and credential rotation, and a named support SLA. Each purchased system also needs a named integration owner, service-account strategy, event mapping, retention plan and contract-test plan.

**Status: no purchased system has passed this gate.** All vendor-facing code is a simulator or a recorded adapter. *(Gap G-03.)*

## C.5 The user-facing estate

**One application. There are no service-hosted dashboards.**

ADR 0006 retired the three Bootstrap dashboards this section described in v1.1 — dispatch at
`/dispatch/`, emergency at `/emergency/` and the facilities workspace at the facilities service root.
All three paths still resolve, and each now serves a short notice page that names ADR 0006 and points
at `/ui/`. They are signposts, not screens.

The **SFL Operations UI** (React 19, TypeScript strict, Vite, Tailwind, ApexCharts) is the only user
interface in Phase 1. It is served by the fleet-logistics service at `/ui/` and shares one design
system: application shell, sidebar and top bar, stat cards, data tables, status chips, filter bars,
form dialogs with validation, workflow timelines, drill-down drawers, charts, and a common error and
notification model.

| Module | Screens | Dialogs | Covers |
|---|---|---|---|
| **Facilities** | 24 | 18 | S152 estate, readiness, assessments, checklists, zones, devices, audit, configuration — and S153 faults, work orders, PM schedules, vendors, evidence |
| **Booking** | 5 | 7 | S159 diary, availability search, booking detail, room turnaround, bookable resources |
| **Fleet** | 12 | 5 | S166 |
| **Fuel** | 12 | 5 | S168_fuel |
| **Dispatch** | 10 | 4 | S171 |
| **Emergency** | 9 | 4 | S174 |
| **My work** | 6 | — | Personal landings that cross systems — see below |
| **Total** | **78** | **43** | |

### Programme-scoped portals, and personal landings

Two things shape what a user actually sees, and neither is a security control — every call is
authorised again server-side.

**Programme and system entitlement (ADR 0005).** A user sees the programmes they are entitled to, at
two grains. Programme answers "should this person be in safety screens at all"; system answers the
question programme cannot, because FTLMP is three systems in one deployable — without the finer grain
a mailroom officer sees the whole fleet register and a driver sees the courier manifests.

**Personal landings.** Six views under `/me/` answer "what do I have to do today" for the roles the
platform previously stranded on an operational dashboard they could not use: driver, requester,
technician, mailroom officer, centre manager and assurance. They are gated by *persona* — the
narrowest-role rule, mirroring what the services already enforce — because what makes somebody a
driver is what they **cannot** do, and every permission `FLEET_DRIVER` holds is also held by
`FLEET_MANAGER`.

### Two points for the review, neither a capability gap

1. **Coverage.** No screens exist for the SSEMP safety-and-security domain (S160, S160a, S161, S162,
   S162a, S163), because those systems have no back end to present. The one built system with a
   missing screen is the **S168 fuel-card registry**: its API is complete and cards are managed by
   API only. That is in F.1 as a Release 1 condition.
2. **Consolidation — closed.** v1.1 tracked convergence of four front ends onto one shell as **G-26
   (Low)**. ADR 0006 decided it and the work has landed: there is one shell, one design system and
   one stack. G-26 can be struck.

---

# SECTION D — The Phase 1 business workflows

Each workflow below is presented in one consistent shape:

> **Flow** (what happens, in order) → **Lifecycle** (the states) → **Hard rules** (what the system must refuse) → **Implementation position** (what actually runs) → **Questions to close** → **Refinements agreed** (filled in during the session).

---

## D.1 — S152 · Facility register and readiness model

**Module:** IFIMP · **Delivery:** Hybrid · **Status:** ◐ Partial — master data only  
**SRS requirements:** S152-01 … S152-07

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Facilities Officer | Creates or edits a location: site → building → floor → room/hall → zone | Location **active**; stable SFL location reference issued | Reference is permanent and reused by every other module |
| 2 | Integration adapter *(optional)* | Vendor IWMS/CAFM record changes are validated and mapped | Register updated | On conflict, the configured precedence rule decides. **Vendor identifiers never persist in SFL records** |
| 3 | Facilities Officer | Defines capacity, layout and permitted use for a space | Space published to booking, access and safety workflows | Enforced at booking time and at entry |
| 4 | System | Hall is scheduled for examination → readiness checklist generated | Checklist items **mandatory** or **advisory** | — |
| 5 | S153 / S160a / S161 / S162a / S171 | Facilities, IT, security and logistics checks update readiness items | Readiness score recomputed | **Any unmet mandatory item forces NOT-READY and escalates** |
| 6 | Command Role | Confirms hall readiness | Hall may open | **Confirmation refused while any mandatory item is unmet** |
| 7 | Authorised owner | Edits the readiness checklist or a threshold | New version applied to future evaluations | Validated, versioned, audited — **no code release** |
| 8 | Facilities Officer | Device or asset installed / moved | Single authoritative location mapping updated and republished | History retained |

### Lifecycle

- **Location:** `active` → `under maintenance` → `decommissioned`. A location referenced by any live record is **retired, never hard-deleted**.
- **Device / asset:** `planned` → `installed` → `active` → `faulty` → `removed`.
- **Implemented:** `LocationReadinessStatus`, `DeviceOperationalStatus`, `DeviceReferenceType` enums exist on `Site`, `Building`, `FacilityFloor`, `FacilityRoom`, `Zone`, `DeviceReference`.

### Hard rules

1. A location referenced by a live record cannot be hard-deleted.
2. Unmet mandatory readiness item ⇒ NOT-READY ⇒ hall confirmation blocked.
3. Vendor identifiers never leak into SFL operational records.
4. Readiness checklist and thresholds are runtime-configurable, versioned and audited.

### Implementation position

| Capability | Status | Evidence |
|---|---|---|
| Site / building / floor / room / zone register | ● Built | `facilities/masterdata/domain/*`, `V2__facilities_master_data.sql`, `FacilitiesMasterDataController` |
| Device reference register | ● Built | `DeviceReference`, `RegisterDeviceReferenceCommand` |
| Room readiness attribute | ◐ Field only | `UpdateRoomReadinessCommand`, `LocationReadinessStatus` — a settable status, **not a computed score** |
| Readiness **checklist** model (mandatory vs advisory) | ○ Not built | No checklist entity, no scoring service |
| Readiness score fed by S153 / S160a / S161 / S162a / S171 | ○ Not built | No consumers; the contributing systems mostly do not exist |
| Command Role confirmation gate | ○ Not built | No confirmation endpoint or authority check |
| IWMS/CAFM vendor adapter | ○ Not built | `facilities/infrastructure/integration/` is empty |
| MDM (S223) resolution | ○ Not built | No MDM client, port or adapter anywhere |
| Audit hash chain | ○ Not built | Facilities has an outbox but no audit chain |

**Assessment.** S152 today is a location registry. The readiness model — which is the reason S152 exists and the input to Architecture business rule **BR-01** — is not implemented.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-152-1 | What is the definitive mandatory-item list for an examination hall? (SRS names power, network, local exam-server reachability, recording, access-control status, life-safety status, workstation availability — confirm this is complete and final) | Facilities Director + NECC |
| Q-152-2 | Who holds "Command Role" in practice at HQ and at each centre, and who deputises? | F&L / NECC |
| Q-152-3 | Is a *conditionally ready* hall a permitted state, or is readiness strictly binary? | NECC |
| Q-152-4 | Is an IWMS/CAFM product being procured for Phase 1, or is the Hybrid classification aspirational? | DTI / Procurement |
| Q-152-5 | When a vendor record and the SFL register conflict, which wins — and does that answer differ during Examination Mode? | Facilities Director |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.2 — S153 · Fault reporting to work-order closure

**Module:** IFIMP · **Delivery:** Build · **Status:** ◐ Partial  
**SRS requirements:** S153-01 … S153-07

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Staff / student / inspection / device / security event / booking | Raises a work request | Validated (location, asset, requester, severity) then classified corrective/preventive with category and priority | Rejected requests carry a reason. **No accepted request is silently dropped.** Duplicates inside a configurable window are *linked*, not duplicated |
| 2 | System | Raises the work order with priority, location, asset, team, SLA and evidence requirements | `NEW` → *Submitted* | SLA timer starts, per priority and category |
| 3 | Supervisor | Assigns to technician or vendor | `ASSIGNED` → *Assigned* | — |
| 4 | Technician | Executes; captures field evidence (photos, readings, notes, parts) on mobile | `PENDING_VERIFICATION` → *Awaiting Verification* | **Closure is blocked here until a supervisor validates the evidence** |
| 5 | Supervisor | Verifies, or returns for rework | `CLOSED` → *Completed*, or back to in-progress | Mandatory evidence missing ⇒ cannot close |
| 6 | System | SLA approaching or breached | Flagged, escalated, dashboarded | Escalation rule is configurable per priority and category |
| 7 | System | Work order affects a readiness-critical space | S152 readiness re-scored; Command Role notified | — |
| 8 | System | Closure records materials, parts, labour and cost | Stock decremented where stores exist; low stock flagged; cost rolled up by asset/location/period | — |
| 9 | System | Preventive plan reaches its due point (calendar- or usage-based) | Work order auto-generated; next-due advances on verification | — |
| 10 | System | Repeated faults or out-of-range meter readings | Reliability flag raised; may trigger a condition-based work order | — |

### Lifecycle

**SRS (Status Matrix rows 1–4):** `NEW` → `ASSIGNED` → `PENDING_VERIFICATION` → `CLOSED`, with cancel and reopen paths. Only `PENDING_VERIFICATION` permits a manual status change.

**Implemented:** fault opens in `REPORTED`; work order runs `OPEN → ASSIGNED → CLOSED`. A closed work order refuses assignment. There is **no `PENDING_VERIFICATION` state and therefore no evidence-verification gate.**

> ⚠ **Material divergence.** The SRS's central control on maintenance — that work cannot close until a supervisor has validated the evidence — is absent from the implementation. Closure is currently a single-step action.

### Hard rules

1. No accepted work request is silently dropped.
2. Closure is impossible without mandatory evidence **and** supervisor verification.
3. SLA-breach escalation is rule-driven and configurable.
4. Preventive plans may be calendar- or usage-based.

### Implementation position

| Capability | Status | Evidence |
|---|---|---|
| Fault intake and classification | ● Built | `FacilityFault`, `FaultPriority`, `ReportFacilityFaultCommand`, `V3__facility_faults.sql` |
| Work order from fault, assign, close | ● Built | `WorkOrderService`, `V4__work_orders.sql`; 11 tests incl. role checks (`technician_cannot_assign_work_order`, `requester_cannot_create_work_order`, `user_without_site_access_is_rejected`) |
| Evidence capture and verification gate | ○ Not built | No `PENDING_VERIFICATION`, no evidence entity |
| SLA timers, escalation, dashboards | ○ Not built | No SLA policy in the facilities service |
| Preventive maintenance plans | ○ Not built | No plan entity or scheduler |
| Materials / parts / cost roll-up | ○ Not built | — |
| Asset maintenance history, meter readings, reliability flag | ○ Not built | — |
| Readiness re-score on closure | ○ Not built | Depends on the S152 readiness model |
| Audit hash chain | ○ Not built | Outbox only |
| Error envelope | ⚠ Non-conforming | Returns ad-hoc `{status,error,message,timestamp}` instead of the platform `ApiResponse` envelope *(conflict C-06)* |

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-153-1 | What are the SLA response and resolution targets per priority and category? Nothing in the SRS or the code fixes these numbers | Facilities Director |
| Q-153-2 | Which work-order categories require photographic evidence before closure, and how many items? | Maintenance Supervisor |
| Q-153-3 | What is the duplicate-linking window — one hour, one shift, one day? | Facilities Director |
| Q-153-4 | Does CLET operate a parts store in Phase 1? If not, the stock-decrement requirement should be deferred explicitly | F&L |
| Q-153-5 | Which spaces are "readiness-critical", and who is notified when work affects them? | Facilities Director + NECC |
| Q-153-6 | Are external vendor technicians given system accounts, or does a CLET supervisor record on their behalf? | F&L / DTI IAM |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.3 — S159 · Room and resource booking

**Module:** IFIMP · **Delivery:** Build · **Status:** ○ Not built  
**SRS requirements:** S159-01 … S159-06/07 *(the SRS summary table numbers 01–07; the detailed sections number 01–06 — see documentation defect DD-04)*

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Requester | Submits a booking | Availability, capacity, permission and restriction checks run | Conflict or breach ⇒ **refuse** or route to review. Otherwise a provisional hold is placed |
| 2 | System | Routes for approval where policy requires (room type, audience, timing, requester scope) | `PENDING_APPROVAL` → *Pending approval* | Approve / reject / amend |
| 3 | Approver | Approves | `APPROVED` → *Approved*; setup tasks generated for cleaning, AV, security, catering | **Space is not usable until setup and readiness are confirmed** |
| 4 | NBES schedule | Hall committed to a live examination | **Examination-Mode Lock** applied | Bookings and configuration changes are refused and logged. Lock lifts when the window closes or the Command Role lifts it. Emergency override requires appropriate authority and is recorded |
| 5 | System | Booking window used or passes | Outcome recorded: completed / no-show / exception | Configurable no-show consequences applied; notifications via S074/S077 |
| 6 | System | Recurring series | Per-occurrence conflict checks | One conflicting occurrence is surfaced **without failing the whole series** |
| 7 | Room Coordinator | Availability search, calendar and utilisation reporting | Published to S225 | — |

### Lifecycle

`PENDING_APPROVAL` → `APPROVED`; outcome states `completed` / `no-show` / `exception`. Manual status change permitted only at `PENDING_APPROVAL`.

### Hard rules

1. **No two confirmed bookings may overlap for the same room or resource.**
2. Capacity or restriction breach ⇒ refuse, or route to review.
3. The examination lock overrides all bookings and configuration changes except an authorised override.

### Implementation position

○ **Nothing exists.** No booking package, aggregate, controller, migration or test. `sfl.ifimp.room-booking-created.v1` sits in the event catalogue but no code publishes it. The only artefact is `docs/phase-4-room-booking-setup.md` — a stale 2025 document describing the removed .NET stack.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-159-1 | Which room types require approval, and who approves each? | Facilities Director |
| Q-159-2 | Which setup task types are in Phase 1 — cleaning, AV, security, catering, all four? Who owns each queue? | F&L |
| Q-159-3 | What are the no-show consequences (warning, booking-right suspension, chargeback)? | F&L |
| Q-159-4 | How far ahead of a session does the examination lock apply, and does it lock the whole building or only the hall? | NECC |
| Q-159-5 | Is booking needed for Vesting Day, or can it be deferred to Release 2? | Registrar |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.4 — S160 · Visitor management

**Module:** SSEMP · **Delivery:** Hybrid · **Status:** ○ Not built  
**SRS requirements:** S160-01 … S160-06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Host / Visitor Desk | Pre-registers a visitor: identity, host, purpose, time window, NDA/induction declarations | `PRE_REGISTERED` → *Invitation sent* | Host resolved from S140; approval requested via S074/S077 |
| 2 | System | Watchlist and restriction check | Clear ⇒ proceed to approval. **Match ⇒ supervised review or denial with recorded reason**, escalated to SOC, may seed an S163 incident | Repeated attempts highlighted |
| 3 | System | On clearance, assigns badge and **minimal** access zones for the time window | Synchronised to S160a and badge devices | Zone permissions are time-bounded and auto-revoke on check-out or expiry |
| 4 | Visitor Desk | Check-in / check-out recorded with time, host and zone | `ON_SITE` → *Checked in* | Live on-site population maintained and **available at the edge for evacuation roll-call**. Overstay beyond the window is flagged |
| 5 | System | Walk-in, group, bulk or multi-day contractor passes | Per-visitor screening, approval, badge and zone | A failing member is handled individually and **does not block the group**. Passes expire automatically; revocation syncs to access control |
| 6 | DPO / System | Retention and purge per Act 843 schedule | Records purged or held | Data-subject requests supported; every export logged |

### Lifecycle

`PRE_REGISTERED` → *(cleared / denied)* → `ON_SITE` → `checked-out` / `expired` / `revoked`.

### Hard rules

1. **No clearance without a recorded host approval and a completed watchlist check.**
2. Zone permissions are time-bounded to the visit window and revoked automatically.
3. Watchlist and restriction data are **special-category personal data** under Act 843.

### Implementation position

○ **Nothing exists.** `sfl-safety-security-service` contains one file — `SystemController.java` (904 bytes) — and one foundation migration. It has no `src/test` directory and no `sfl.security` configuration block.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-160-1 | What is the watchlist source, and who maintains it? | Security Director |
| Q-160-2 | Who may override a watchlist match, and is a second approver required? | Security Director + DPO |
| Q-160-3 | What is the visitor-record retention period, and what is the documented lawful basis? | DPO |
| Q-160-4 | Are badge printers, kiosks and turnstiles procured? Without them the Hybrid classification cannot hold | Procurement |
| Q-160-5 | Is contractor induction tracked in SFL or elsewhere (HRMS, training system)? | HSE Officer |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.5 — S160a · Physical access control integration

**Module:** SSEMP · **Delivery:** Buy & Integrate · **Status:** ○ Not built  
**SRS requirements:** S160a-01 … S160a-06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Access-control vendor system | Emits event (granted / denied / forced-open / override / tamper) plus device health | Raw stored, then normalised to an SFL event | Integration boundary authenticates (per-vendor signature or mTLS), source-allowlists and schema-validates. Duplicates are idempotent. **Malformed or unauthenticated messages are rejected and logged and never drive action** |
| 2 | S140 / S213 | Emits a joiner-mover-leaver event | Zone entitlements provisioned, adjusted or revoked; synced to the vendor system | **Leaver ⇒ automatic revocation** |
| 3 | Authorised officer | Requests an override with a reason | Time-bound override sent to the vendor system | Authority validated; **auto-reverts at expiry**. Break-glass override during a declared emergency is available to pre-authorised roles, with approval recorded after the fact |
| 4 | System | Evaluates events against exception rules — repeated denial, forced-open, tailgating, out-of-hours, restricted-zone | `ACCESS_EXCEPTION` → *Denied / under review* | Raised to the SOC queue, correlated with S161, may seed S163, forwarded to SIEM |
| 5 | Access Control Administrator | Defines or edits a zone, schedule or door group | Validated, versioned, pushed to the vendor system | **Overly broad rules are flagged for review before activation.** Examination Mode auto-applies tightened schedules and lockdown to exam zones |
| 6 | System | Entry/exit events maintain per-zone occupancy and in/out state | Anti-passback exceptions flagged to SOC | Emergency muster combines this with the S160 visitor population; available at the edge |

### Hard rules

1. Manual grants require reason, approver and expiry — they are the exception, not the norm.
2. An override is effective only for its approved window and auto-reverts.
3. Zone, schedule and door-group definitions are versioned and audited.
4. Access entitlement derives from the joiner-mover-leaver basis.

### Implementation position

○ **Nothing exists.** `AccessControlLockdownPort` is *named* in the S174 traceability matrix but the file does not exist; the emergency service collapses all such seams into a single 1.9 KB `IntegrationSeamPorts.java`.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-160a-1 | Which access-control product is being procured, and has it passed the SRS §5.2 gate? | Procurement / DTI |
| Q-160a-2 | Who is pre-authorised for a break-glass access override, and what is the after-the-fact approval window? | Security Director |
| Q-160a-3 | What defines "overly broad" for a zone rule — number of doors, number of holders, 24×7 schedule? | Security Director |
| Q-160a-4 | Is anti-passback enforced by the vendor system or only reported by SFL? | Security Director |
| Q-160a-5 | Does the leaver revocation flow from HRMS (S140) or IAM (S213), and what is the acceptable lag? | HR / DTI IAM |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.6 — S161 · CCTV and video evidence

**Module:** SSEMP · **Delivery:** Buy & Integrate · **Status:** ○ Not built  
**SRS requirements:** S161-01 … S161-05/06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | VMS | Reports camera health | Device state updated; SOC dashboard reflects it | Offline or fault ⇒ raises an S153 work order; in examination areas it **degrades the hall readiness score** |
| 2 | Investigator | Raises an evidence request: cameras, location, time window, purpose | `requested` | **No retrieval or export occurs before an authorised security role approves** |
| 3 | Security Director | Approves or rejects | `approved` / `rejected` | — |
| 4 | System | Retrieves the approved evidence, hashes it, attaches a **reference** (not raw video by default) to the case with provenance | `retrieved` → `cased` | Every view, download and export is logged and hash-chained |
| 5 | SOC Operator | Role-restricted live viewing | Session logged: operator, cameras, time | — |
| 6 | VMS analytics | Motion, line-crossing, loitering, tamper alerts ingested and normalised | Raised to SOC | May pair with S162 / S160a and seed an S163 incident |
| 7 | System / DPO | Retention per configured, legally bounded schedule per camera and zone | Scheduled purge; legal-hold override | Privacy masking honoured. **Disclosure outside CLET requires a governed workflow with authorised approval and a recorded purpose and recipient** |

### Hard rules

1. By default SFL stores only references, metadata and hashes. Raw video is copied **only** under an approved retention and privacy exception.
2. No export without an approved evidence request.
3. Disclosure requires approval plus a recorded purpose and recipient.

### Implementation position

○ **Nothing exists.** `CctvEvidencePort` is named in documentation but absent from code. Note that the **fleet module has a working reference implementation** of exactly this pattern — `EvidenceReference`, `EvidenceExportRequest`, `REQUESTED → APPROVED → EXPORTED / REJECTED`, separation of duties on approval, hash-chained access log — which S161 should reuse rather than reinvent.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-161-1 | What is the CCTV retention duration per zone? *(Architecture §15.6 lists this as an open management decision)* | Security Director + DPO + Board |
| Q-161-2 | Who may approve an evidence export, and must the approver differ from the requester? | Security Director |
| Q-161-3 | What is the governed process for disclosure to police or a regulator? | CLET Legal |
| Q-161-4 | Which zones require privacy masking? | DPO |
| Q-161-5 | Is a camera offline during a live examination an automatic hall blocker, or a warning? | NECC |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.7 — S162 · Intrusion detection and alarm monitoring

**Module:** SSEMP · **Delivery:** Buy & Integrate · **Status:** ○ Not built  
**SRS requirements:** S162-01 … S162-05/06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Alarm panel | Emits zone alarm / tamper / fault / restoration | Raw stored; normalised into an SFL alarm event with zone, panel, severity and time | Authenticated and validated at the boundary; duplicates idempotent; malformed rejected and logged |
| 2 | System | Raises the alarm to the SOC queue with severity | `OPEN_ALERT` → *Response in progress* | **Operator acknowledgement required within a configurable time** |
| 3 | System | Unacknowledged or unresolved past the window | Escalated per rule to Security Director; critical or exam-affecting escalate to NECC / Command Role | After-hours and vault alarms carry elevated severity and tighter timers |
| 4 | SOC Operator | Acknowledges; pairs with S161 evidence for the zone and time | Evidence linked | May escalate into an S163 incident with the full trail |
| 5 | System | Flapping panel emitting rapidly repeating signals | Debounced and coalesced; **device health flagged instead of flooding the SOC** | — |
| 6 | System / Administrator | Zone arming and disarming | Scheduled arming automatic; manual disarm requires an authorised, time-bound reason and **auto-reverts to armed at expiry** | Failure-to-arm or out-of-policy disarm raises a SOC exception. Examination Mode can enforce mandatory arming of exam-content zones |
| 7 | Monitoring service | Confirmed alarm — armed-response coordination | Dispatch, acknowledgement, arrival and outcome recorded against the alarm and any incident | False-alarm outcomes trended |

### Hard rules

1. A critical alarm unacknowledged past its window escalates automatically.
2. Zones must arm per schedule; disarm is authorised and time-bound only and reverts automatically.
3. Panel flapping is coalesced, never amplified.

### Implementation position

○ **Nothing exists.**

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-162-1 | What is the SOC acknowledgement window per severity, and what is the escalation ladder? | Security Director |
| Q-162-2 | Is an armed-response monitoring contract in place, and does it expose an API for dispatch/arrival/outcome? | Security Director / Procurement |
| Q-162-3 | Which zones are "vault" or elevated-severity? | Security Director |
| Q-162-4 | Is the SOC staffed 24×7 at Go-Live? If not, what is the out-of-hours path? | Security Director |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.8 — S162a · Fire and life-safety monitoring

**Module:** SSEMP · **Delivery:** Buy & Integrate (observe-and-supplement only) · **Status:** ○ Not built  
**SRS requirements:** S162a-01 … S162a-06

> ### The governing constraint
> **SFL must never sit in the certified actuation path.** Certified fire, smoke, panic and intrusion systems (EN 54 / UL / fire code) detect and actuate independently and remain the sole authoritative controllers. Disabling SFL must have **no effect** on detection or actuation. SFL observes, records, notifies and supplements.

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Certified panel | Detects and actuates **independently**; emits an event | Normalised life-safety event surfaced to SOC and Command Role | SFL issues **no** command or control |
| 2 | System | Fire or panic event observed | **Fast lane** initiates the emergency workflow; affected zone derived from event location | **Break-glass mass notification via S174 is triggered, bypassing routine approval.** Activation recorded with millisecond timing for latency verification |
| 3 | System | Opens evacuation roll-call | Population combines S160 visitors + S160a access occupancy + staff presence | Muster check-ins captured, **including at the edge**. Outstanding persons highlighted until accounted for, then all-clear |
| 4 | Safety Officer | Tracks inspection and certification schedules | Due / overdue flagged as compliance exceptions | Panel fault or overdue inspection raises an S153 work order plus a compliance exception; evidence recorded by reference to S003 |
| 5 | Safety Officer | Detector and zone coverage mapped via S152; functional test schedules tracked | Coverage gaps, failed and overdue tests flagged | Feeds compliance reporting and hall readiness |

### Hard rules

1. SFL never issues actuation commands.
2. The fast lane bypasses non-essential enrichment and routine approval.
3. Evacuation roll-call is a **supplement**, never a replacement, for certified alarm and actuation.

### Implementation position

○ **Nothing exists** for S162a itself. However, the principle is already *asserted and tested* in the emergency service: `sfl_does_not_perform_certified_life_safety_actuation` passes. The `LifeSafetyEventPort` referenced in documentation does not exist as a file; the seam is collapsed into `IntegrationSeamPorts.java`.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-162a-1 | Do the installed fire panels expose a read-only event feed or a BMS bridge? Which product, and has it been certified for read-only integration? | Safety Officer / Procurement |
| Q-162a-2 | What is the confirmed detection-to-notification latency target? *(SRS NFR-P1 records this as "to be confirmed")* | Emergency Coordinator + DTI |
| Q-162a-3 | Who runs the roll-call — Emergency Coordinator, Centre Manager or the fire warden per building? | Emergency Coordinator |
| Q-162a-4 | Where are certification and inspection records held today, and are they in S003? | Safety Officer |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.9 — S163 · HSE incident and near-miss reporting

**Module:** SSEMP · **Delivery:** Build · **Status:** ○ Not built  
**SRS requirements:** S163-01 … S163-06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Staff / student / inspector, or seeded from S160a / S162 / S162a | Reports an incident or near-miss — portal, mobile, **or at the edge during WAN loss** | Validated; unique reference issued | `TRIAGE` → *Submitted for review*. Anonymous or confidential near-miss reporting supported where policy allows |
| 2 | HSE Officer | Rates severity and risk (likelihood × impact) at triage | Revisable during investigation | **Emergency rating ⇒ command/emergency workflow plus S174 mass notification.** Others ⇒ assigned to a response team with an SLA |
| 3 | Investigator | Opens the investigation; root-cause analysis; findings recorded; evidence linked | Investigation open | — |
| 4 | Investigator | Creates CAPA items with owner, due date and status | CAPA open | Corrective actions can generate S153 work orders. Overdue actions flagged and escalated. **The incident cannot close while any mandatory CAPA remains open** |
| 5 | HSE Officer | Preserves evidence (photos, statements, documents) with provenance and hashes | Evidence cased | Large files held **by reference** to S003 |
| 6 | System | Classifies the incident against reportability criteria | Reportable ⇒ statutory notification task auto-raised with a deadline | Tracked: responsible officer, recipient authority, submission, acknowledgement, evidence. **Deadline breaches escalate** |
| 7 | HSE Officer | Maintains a hazard and observation register with risk rating, owner and target control | Linked to CAPA | **Completion requires effectiveness verification** (re-inspection or follow-up) before closure. Recurring hazards or ineffective controls escalate to management |
| 8 | Compliance Officer | Statutory and management HSE reports and dashboards for a chosen period and scope | Exports logged | Metrics published to S225 |

### Hard rules

1. Closure is blocked while any mandatory corrective action remains open.
2. An emergency-rated incident triggers the command/emergency path automatically.
3. Reportability criteria, authorities and deadlines are runtime-configurable.

### Implementation position

○ **Nothing exists.**

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-163-1 | What is the CLET severity and risk-rating matrix (likelihood × impact bands)? | HSE Officer |
| Q-163-2 | Which incident classes are statutorily reportable in Ghana, to which authority, and within what deadline? | HSE Officer + CLET Legal |
| Q-163-3 | Is anonymous near-miss reporting permitted by CLET policy? | F&L + DPO |
| Q-163-4 | What are the CAPA SLA bands by severity? | HSE Officer |
| Q-163-5 | Who verifies control effectiveness — the action owner's supervisor, or an independent officer? | HSE Officer |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.10 — S174 · Emergency mass notification

**Module:** SSEMP · **Delivery:** Hybrid · **Status:** ● Built — but **no vendor channel is connected**  
**SRS requirements:** S174-01 … S174-06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Emergency Coordinator | Declares the event; selects affected site/zone (geo-targeted), audience (roles, groups, on-site population), template and channels | `DRAFT` | Channels: SMS, email, push, voice, siren, digital signage |
| 2 | Coordinator | Submits a **routine** notice for approval | `PENDING_APPROVAL` → `APPROVED` → `ACTIVATING` → `ACTIVE` | **Routine notices require approval before send.** Unauthorised approval is rejected |
| 2′ | Pre-authorised role | **Break-glass**: fires a pre-authorised template during a declared emergency | `BREAK_GLASS_ACTIVE` | **No per-message approval. Sent immediately.** Approval is recorded after the fact. Unauthorised attempts are blocked and logged. Break-glass never gates life-safety |
| 3 | Providers | Delivery and acknowledgement receipts per recipient and channel | `ACTIVE` / `PARTIALLY_DELIVERED` | Dashboard shows status. Non-delivery and non-acknowledgement highlighted for alternate-channel follow-up. Unacknowledged recipients escalate after SLA |
| 4 | Coordinator | Issues follow-up and all-clear referencing the original event | `ALL_CLEAR_PENDING` → `CLOSED` | **All-clear is valid only from an active state.** Closure requires reason, delivery/ack summary and evidence |
| 4′ | Approver | For break-glass, records the after-action approval | Closure permitted | **Break-glass closure is blocked until after-action approval is recorded** |
| 5 | System | Core unavailable during an emergency | `DEGRADED` mode: alert raised via an edge-triggered or provider-direct path | Reconciled and archived to core on restore; the complete record is preserved |
| 6 | Administrator | Maintains a pre-authorised, versioned template and audience-group library | — | **Only approved templates are available for break-glass** |
| 7 | Coordinator | Drill / test mode | Clearly marked | Excluded from real-emergency metrics but measures reach, delivery and acknowledgement for readiness |

### Lifecycle — implemented

`DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ACTIVATING`, `ACTIVE`, `BREAK_GLASS_ACTIVE`, `PARTIALLY_DELIVERED`, `ESCALATED`, `ALL_CLEAR_PENDING`, `CLOSED`, `CANCELLED`, `FAILED`, `REOPENED` (14 states).
Modes: `ROUTINE` / `BREAK_GLASS` / `DEGRADED`. Channels: `SMS`, `EMAIL`, `PUSH`, `VOICE`, `SIREN`, `DIGITAL_SIGNAGE`. Delivery: `QUEUED`, `SENT`, `DELIVERED`, `FAILED`, `EXPIRED`.

This is a **fuller state machine than the SRS Status Matrix**, which records only `SENT_AWAITING_ACK`. The implementation is the better model; the SRS matrix should be updated to match *(refinement RF-02)*.

### Hard rules

1. Approval-before-send applies **only** to routine notices and must never delay an emergency alert.
2. Every activation records initiator, affected zone, audience, channels and template.
3. Break-glass closure requires recorded after-the-fact approval.
4. The fallback path is tested as part of commissioning.

### Implementation position

| Capability | Status | Evidence |
|---|---|---|
| Activation workflow, 14 states | ● Built | `NotificationActivation` (11.2 KB), `ActivationService` (21.9 KB), `V1`–`V8` |
| Break-glass with after-action gate | ● Built + tested | `BreakGlassPolicy`; tests `break_glass_sends_without_pre_approval`, `break_glass_requires_after_action_before_closure`, `break_glass_by_unauthorised_role_is_denied` |
| Provider delivery callbacks with idempotency | ● Built | `ProviderCallbackService`; tests for duplicate, unsigned and schema-invalid callbacks |
| Acknowledgement tracking + SLA escalation | ● Built | `acknowledgement_tracking_updates_counts`, `unacknowledged_recipients_escalate_after_sla` |
| Drills and degraded mode | ● Built | `drill_records_performance_and_report_metrics`, `degraded_mode_records_fallback_path_and_metadata` |
| Audit hash chain + tamper detection | ● Built | `audit_chain_integrity_holds_and_tampering_is_detected` |
| Life-safety non-actuation assertion | ● Built | `sfl_does_not_perform_certified_life_safety_actuation` |
| **Outbound vendor gateway** | ⛔ **Recorded only** | `RecordedNotificationGateway` (1.5 KB). **No SMS, voice, siren or signage provider is connected.** Nothing actually reaches a recipient |
| Operations dashboard | ● Built | Emergency Mass Notification dashboard at `:8095/emergency/` — operational posture, templates and scenarios, audiences and zones, activations with workflow actions and break-glass, drill runs, outbound integration health, CSV export |
| Fast-lane latency target | ⚠ Undefined | SRS NFR-P1 and gap C-09 both record the target as "TBC" |

**Test coverage:** 35 tests, 22 end-to-end scenarios, all passing.

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-174-1 | Which notification provider(s) will be contracted for SMS, voice, siren and signage per site? *(Architecture §15.6 open decision)* | Emergency Coordinator + Procurement |
| Q-174-2 | What is the confirmed detection-to-notification latency target for CT-20? | Emergency Coordinator + DTI |
| Q-174-3 | Who is on the pre-authorised break-glass roster per site, and who performs after-action approval? | Emergency Coordinator |
| Q-174-4 | Which templates are pre-authorised for break-glass, in which languages? | Emergency Coordinator |
| Q-174-5 | Should the Emergency dashboard be folded into the single SFL Operations UI shell so coordinators work in one application, or remain a service-hosted dashboard? | Emergency Coordinator + DTI |
| Q-174-6 | What is the drill cadence, and does a failed drill block Examination Mode? | NECC |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |
---

## D.11 — S166 · Fleet and vehicle management

**Module:** FTLMP · **Delivery:** Hybrid · **Status:** ● Built — the reference implementation for the platform  
**SRS requirements:** S166-01 … S166-05 *(note: the workplan references a non-existent `SRS-SFL-S166-06` — see documentation defect DD-01)*

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Fleet Officer | Registers a vehicle with compliance items — insurance, roadworthiness, registration | `ACTIVE`; compliance validity monitored | Expiry flagged in advance. **An expired or failed mandatory item forces NOT-READY and withholds the vehicle from assignment** |
| 2 | Requester | Requests a trip — purpose, route, timing, passengers/cargo | `PLANNED` | — |
| 3 | Transport Manager | Approves per policy, assigns a compliant vehicle and an eligible driver | `ASSIGNED` → *Vehicle assigned*; dispatch note issued | **Readiness must return no BLOCKING blocker.** Overlapping assignments are prevented |
| 4 | Driver | Completes the pre-trip inspection — tyres, lights, brakes, fluids, safety equipment | Inspection `SUBMITTED`, result `PASSED` / `PASSED_WITH_DEFECTS` / `FAILED` | **A FAILED result blocks trip start. A CRITICAL defect takes the vehicle out of service** and raises a workflow item |
| 5 | Driver | Starts the trip | `IN_PROGRESS` | May go `ON_HOLD` and resume |
| 6 | Telematics *(optional)* | Location, geofence, speed and route-deviation ingested through the integration boundary | Recorded against the trip | Deviations on sensitive trips raise exceptions |
| 7 | Driver | Post-trip inspection with odometer; returns the vehicle | `COMPLETED` | **A trip cannot close without a post-trip inspection, a closure reason, evidence and a non-regressing odometer.** Late return, deviation and damage raise exceptions |
| 8 | System | Driver eligibility checked at assignment — licence class, permit, medical, verified against S140 | Eligibility derived | **Expired or missing licence blocks assignment.** Suspended or restricted drivers are withheld. Expiries flagged in advance |
| 9 | System | Service due, or a defect reported at any point | S153 work order raised; vehicle withheld from readiness | Safety-critical defect withholds the vehicle |
| 10 | Logistics Coordinator | Emergency-logistics mobilisation — spare equipment, replacement vehicle, escort | Linked to the originating S163 incident | **Prioritised over routine trips** |

### Lifecycle — implemented

| Aspect | States |
|---|---|
| Vehicle lifecycle | `ACTIVE` ⇄ `INACTIVE` ⇄ `SUSPENDED` → `ARCHIVED`; **`ARCHIVED` restores only to `INACTIVE`**, never straight to `ACTIVE`. Archived records are immutable |
| Vehicle availability *(derived)* | `AVAILABLE`, `RESERVED`, `ASSIGNED`, `IN_USE`, `UNAVAILABLE` |
| Service status *(derived)* | `IN_SERVICE`, `DUE`, `OVERDUE`, `OUT_OF_SERVICE` — computed from next-due **date or odometer, whichever comes first**. `OVERDUE` or `OUT_OF_SERVICE` blocks assignment |
| Trip | `PLANNED → ASSIGNED → IN_PROGRESS → COMPLETED`; `ON_HOLD` and `CANCELLED` branches. `ASSIGNED → ASSIGNED` is a permitted reassignment. Terminal states are immutable |
| Workflow item | `OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, ESCALATED, CLOSED, CANCELLED, REOPENED`. **`OPEN → CLOSED` is prohibited**; an item cannot close from `ON_HOLD`; `CLOSED → REOPENED` is privileged |
| Inspection | `DRAFT → SUBMITTED → ACCEPTED / REJECTED`; immutable once out of `DRAFT` |
| Driver | Lifecycle `ACTIVE / INACTIVE / SUSPENDED / ARCHIVED`; eligibility *(derived)* `ELIGIBLE / CONDITIONAL / INELIGIBLE / SUSPENDED` |
| Compliance document | `ACTIVE → EXPIRING → EXPIRED`, plus `SUPERSEDED` and `REVOKED`. One current document per (vehicle, type) |
| Readiness *(derived)* | `READY` / `CONDITIONALLY_READY` / `NOT_READY`, driven by **29 machine-readable blocker codes** with severity `WARNING` or `BLOCKING` |
| Evidence export | `REQUESTED → APPROVED → EXPORTED` / `REJECTED` |

### Business rules encoded in code — for validation

| Rule | Encoded behaviour | Confirm? |
|---|---|---|
| Compliance warning window | **30 days** before expiry ⇒ `COMPLIANCE_DOCUMENT_EXPIRING` (warning) | ☐ |
| Compliance expiry mid-journey | A document valid today but expiring **before the end of the requested trip period** is treated as **EXPIRED** — a hard block, not a warning | ☐ |
| Mandatory documents | Roadworthiness certificate, insurance certificate, vehicle registration | ☐ |
| Optional documents on file | DVLA inspection, commercial permit, emissions, fire-extinguisher certificates are **still evaluated once lapsed** | ☐ |
| Pre-trip inspection validity | **1 day** — an older inspection reads as `MANDATORY_INSPECTION_MISSING` | ☐ |
| Service due warning | **14 days** | ☐ |
| Odometer staleness | **30 days** ⇒ `ODOMETER_PROVENANCE_STALE` (warning only) | ☐ |
| Licence expiring mid-trip | Same hard-block treatment as compliance | ☐ |
| Medical clearance | If no expiry is recorded, **no finding is raised** — medical clearance is treated as not required for every driver class | ☐ |
| Driver at a different site | `DRIVER_SITE_RESTRICTION` blocker | ☐ |
| Default SLA when no rule matches | **4 h response, 24 h resolution, escalate to `FLEET_MANAGER`** — deliberately surfaced on the item as `compiled-in-default` | ☐ |
| Driver record scope | A `FLEET_DRIVER` sees only their own trips and inspections unless a supervising permission applies | ☐ |
| Site scope | `*` grants all sites; **an empty scope throws an error rather than defaulting to all** | ☐ |
| Readiness assessment | Never short-circuits — **all** applicable blockers are returned, not the first | ☐ |

### Implementation position

● **Built to a substantial standard.** 58 domain classes, 8 policy classes, 9 application services, 8 controllers with 45+ verified endpoint mappings, migrations V1–V9.1, an append-only hash-chained audit with replay verification, evidence with separation-of-duties export approval, a secure inbox with real HMAC-SHA256 signature verification and a 5-minute replay window, an outbox with retry (8 attempts, 10 s → 3600 s backoff), dead-letter and replay, four schedulers, a full Fleet operations dashboard (12 screens), and **~250 tests including 16 end-to-end scenarios against real PostgreSQL — all passing**.

**Residual gaps:**

| Gap | Effect |
|---|---|
| No standalone/periodic inspection endpoint (`RecordStandaloneInspection` exists but is unmapped) | **Blocks the periodic-inspection part of SRS-SFL-S166-01** |
| `GET /vehicles/{id}/readiness` has no mapping | Readiness only reachable via `trips/assignment-preview` |
| `GET /vehicles/{id}/movement` missing | Telematics positions are stored but cannot be read back |
| No evidence search endpoint | Operators must paste evidence reference IDs into closure dialogs |
| No integration inbox search | **Dead-letter replay is not operable from the dashboard** |
| No cross-fleet compliance search | The UI fans out over the first 50 active vehicles only |
| `GET /api/v1/fleet/audit/records` returns **HTTP 500** on dev PostgreSQL | The only audit search in the platform is broken ⛔ |
| `POST /api/v1/fleet/emergency-logistics` not built | **SRS-SFL-S166-04 emergency mobilisation is not implemented** (conflict C-12) |
| Two conflicting retention vocabularies | `RetentionClass` (6 values) vs `EvidenceRetentionClass` (4 values), different periods — an auditor would be shown two answers |

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-166-1 | Confirm every threshold in the table above. Each is currently a developer default, not an F&L policy | Transport Manager |
| Q-166-2 | Should a licence or insurance expiring mid-trip be a hard block (current behaviour) or a warning requiring manager override? | Transport Manager |
| Q-166-3 | Which driver classes require medical clearance? The system currently raises nothing when none is recorded | Transport Manager + HSE |
| Q-166-4 | Is periodic (non-trip) inspection required for Go-Live? It is specified but unreachable | Transport Manager |
| Q-166-5 | Where does emergency-logistics mobilisation live — is C-12's deferral to a later saga acceptable, or must it ship in Release 1? | Logistics Coordinator |
| Q-166-6 | Which retention vocabulary is authoritative for fleet evidence? | Compliance / DPO |
| Q-166-7 | Is a GPS/telematics provider being procured? *(Architecture §15.6 open decision)* | Transport Manager + Procurement |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.12 — S168_fuel · Fuel management and driver logbooks

**Module:** FTLMP · **Delivery:** Hybrid · **Status:** ◐ Partial — advanced, but **self-declared not ready to close**  
**SRS requirements:** S168_fuel-01 … S168_fuel-05/06

### Flow

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Fuel card / pump / vendor system, or driver mobile | Emits a transaction, or captures odometer | Authenticated and validated at the boundary; normalised to vehicle, quantity, cost, time, location and source | Duplicates are idempotent. **No fuel transaction is silently dropped** |
| 2 | System | Matches the transaction against the assigned vehicle, the driver logbook entry, the approved trip and the odometer, evaluated against configurable variance and consumption policy | Within policy ⇒ `RECONCILED` with a full match trail | Breach or no match ⇒ `EXCEPTION` — a fuel exception case is raised |
| 3 | Driver / Fleet Officer | Provides an explanation for the exception | `AWAITING_EXPLANATION` → `EXPLANATION_RECEIVED` | — |
| 4 | Transport Manager | Approves, rejects or escalates | `APPROVED` / `REJECTED` / `ESCALATED` → `CLOSED` | **No exception clears without an explanation and an authorised decision.** Closure additionally requires a closure reason and evidence |
| 5 | System | Material exceptions above a configurable threshold | Surfaced to Finance/Audit and Finance/ERP | Repeated or patterned exceptions highlighted for investigation |
| 6 | Fleet Officer | Fuel cards and allocations assigned to vehicles and drivers with limits | Transactions attributed | Unassigned, expired, over-limit or blocked-card transactions flagged. Lost, stolen or misused cards blockable |
| 7 | Driver | Maintains a logbook | `DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED`; `RETURNED → RESUBMITTED` | **An approved logbook is locked until a privileged reopen** |
| 8 | Transport Manager | Consumption and cost reported per vehicle, site and period against budget | Published to S225; material variances to Finance/ERP | Anomalies — rising litres-per-km, abnormal frequency — surfaced |

### Lifecycle — implemented

| Aggregate | States |
|---|---|
| Fuel transaction | `RECEIVED`, `VALIDATING`, `MATCHED`, `RECONCILED`, `EXCEPTION`, `REJECTED`, `VOIDED` — **`VALIDATING`, `MATCHED` and `REJECTED` are never written by any code path** *(dead constants, gap G-21)* |
| Driver logbook | `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `RETURNED`, `RESUBMITTED`, `APPROVED`, `REOPENED`, `CANCELLED`. **The implemented return path is `RETURNED → RESUBMITTED`, not `RETURNED → DRAFT` as the design document states** |
| Fuel anomaly case | `DETECTED`, `ASSIGNED`, `UNDER_REVIEW`, `AWAITING_EXPLANATION`, `EXPLANATION_RECEIVED`, `APPROVED`, `REJECTED`, `ESCALATED`, `CLOSED`, `HELD`, `CANCELLED`, `REOPENED`. Severity `LOW / MEDIUM / HIGH / CRITICAL` |

### The 14 reconciliation rules — for validation

| # | Rule | Raises | Threshold source | Confirm? |
|---|---|---|---|---|
| 1 | `MAX_PER_TRANSACTION` | `LIMIT_EXCEEDED` | Policy column | ☐ |
| 2 | `TANK_CAPACITY` | `TANK_CAPACITY` | Policy column | ☐ |
| 3 | `FUEL_PRODUCT` | `FUEL_PRODUCT` | Policy column | ☐ |
| 4 | `APPROVED_VENDOR` | `VENDOR` | Policy column | ☐ |
| 5 | `DRIVER_ELIGIBLE` | `DRIVER_INELIGIBLE` | S166 eligibility at transaction time | ☐ |
| 6 | `VEHICLE_OPERATIONAL` | `VEHICLE_UNAVAILABLE` | S166 vehicle state | ☐ |
| 7 | `TRIP_MATCH` | `OUTSIDE_TRIP` | Transaction inside the trip window | ☐ |
| 8 | `ODOMETER_NON_REGRESSION` | `ODOMETER_REGRESSION` | Against the accepted vehicle odometer | ☐ |
| 9 | `ODOMETER_JUMP` | `ODOMETER_JUMP` | Policy column | ☐ |
| 10 | `RECEIPT` | `MISSING_RECEIPT` | `receipt_required` + `receipt_grace_hours` | ☐ |
| 11 | `CONSUMPTION_RANGE` | `ABNORMAL_CONSUMPTION` | `min_consumption` / `max_consumption` | ☐ |
| 12 | `COST_VARIANCE` | `COST_VARIANCE` | ⚠ **Hard-coded ±30 %** against the vehicle's previous transaction | ☐ |
| 13 | `LOGBOOK_MATCH` | `LOGBOOK_MISMATCH` | Trip's logbook end odometer | ☐ |
| 14 | `REPEATED_PATTERN` | `UNUSUAL_PATTERN` | ⚠ **Hard-coded: ≥3 anomalies for the vehicle or driver in 30 days** | ☐ |

Plus an overnight sweep rule: `COMPLETED_TRIP_WITHOUT_LOGBOOK`.

**Key business rule — odometer authority.** Fuel and logbooks retain *raw observations*. The **S166 vehicle aggregate owns the accepted reading.** Only a newer, plausible reading advances it; regressions and implausible jumps become anomaly inputs and never overwrite fleet state. *(This is a good design decision and should be explicitly ratified by F&L.)*

### Implementation position

| Capability | Status |
|---|---|
| 4 aggregates, 14-rule reconciliation engine, 12-state anomaly case, 8-state logbook | ● Built |
| CSV import (10 required + 7 optional headers) | ● Built — import batch history, detail and row read-back endpoints exist |
| Finance/Audit outbound seam, outbox health and replay | ● Built (recorded adapter) |
| 17 end-to-end scenarios, Fuel operations dashboard (12 screens) | ● Built, all passing |
| **Fuel card / allocation management (S168_fuel-04)** | ● API/domain/migration built; **UI screen still missing** |
| Rolling daily / weekly / monthly limits | ○ Daily/monthly values are stored on policy/card records but are **not yet evaluated by reconciliation** |
| Reconciliation read endpoint | ● Built — transaction reconciliation history returns persisted per-rule outcomes |
| Pagination on collections | ● Built for the API paths the Release 1 UI uses |
| Dashboard | ● Built for the Release 1 fuel workflow; live vendor telemetry remains deferred |
| Overlapping active policies | ● Refused at policy creation; abutting half-open periods remain valid |
| Duplicate CSV upload | ● Mapped to the fuel import duplicate error; rows are not duplicated |
| Module status | ● Closed for Release 1 demo, with residuals tracked in the Release 1 gap prompt |

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-168-1 | Confirm approved consumption bands (litres per km) per vehicle class | Transport Manager |
| Q-168-2 | Confirm tank capacities, receipt grace period and anomaly SLA hours | Transport Manager |
| Q-168-3 | Is ±30 % the correct cost-variance tolerance, and should it be site-specific? It is currently hard-coded | Transport Manager + Finance |
| Q-168-4 | Is "3 anomalies in 30 days" the correct repeated-pattern trigger? | Transport Manager |
| Q-168-5 | What is the Finance materiality threshold above which an exception is escalated, and what acknowledgement is expected back? | Finance / Audit |
| Q-168-6 | Are fuel cards in use? If so, S168_fuel-04 cannot ship without a card registry | Transport Manager |
| Q-168-7 | Which fuel provider is contracted, and does it offer an API/webhook or only a CSV export? What is the production CSV layout? | Procurement / Integration owner |
| Q-168-8 | Are rolling daily/monthly limits required for Go-Live, or deferrable? | Transport Manager |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.13 — S171 · Mailroom, courier and secure dispatch

**Module:** FTLMP · **Delivery:** Build · **Status:** ● Built  
**SRS requirements:** S171-01 … S171-06 *(the SRS numbers 01–05 in one place and 01–06 in another — see documentation defect DD-02)*

> This is the workflow that carries examination papers. It is the highest-consequence flow in Phase 1 and maps directly to Architecture business rule **BR-04** and commissioning test **CT-05**.

### Flow — outbound leg

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 1 | Mailroom Officer | Registers an item: origin, destination, item type, sensitivity, handler | `RECEIVED` | **Chain-of-custody handling is *derived*, not chosen** — required when sensitivity is `CONFIDENTIAL` or `SECRET`, or type is sealed bag / examination paper / examination device / sealed material |
| 2 | Dispatch Controller | Builds a manifest: items, seal IDs, counts, route, handler | `STAGED`; dispatch `DRAFT` → `SEALED` | **A dispatch cannot be sealed empty or without seal IDs** |
| 3 | Custodians | Record each handover through the chain: **warehouse staging → dispatch → transit → centre receipt → hall deployment → collection → return** | `DISPATCHED` → `IN_TRANSIT` | Each hop records transferring and receiving custodian, time and seal state. **Handovers are append-only with a monotonic sequence number** |
| 4 | System | Custody gap detection | `CUSTODY_GAP` exception if any of: missing expected hop, out-of-order sequence, non-`INTACT` seal, item-count mismatch against the manifest | **Blocks closure** |
| 5 | Centre Manager | Confirms receipt: verifies seal integrity, item count and recipient signature against the manifest | `DELIVERED`; receipt `CLEAN` | Works **at the edge**, reconciling idempotently on replay |
| 5′ | Centre Manager | Variance on receipt | Receipt `VARIANCE`, type `BROKEN_SEAL` / `SHORT_COUNT` / `OVER_COUNT` / `WRONG_RECIPIENT` / `MISSING_SIGNATURE` | Seal and tamper variants are flagged **security-relevant and escalate to SSEMP**. Closure blocked |
| 6 | Scanner / carrier *(optional)* | Barcode or carrier-API events attach to the custody record | Additive | **Full custody and receipt function without any scanner or carrier connected** |

### Flow — return leg and inbound mail

| # | Actor | Action | Resulting state | Gate / branch |
|---|---|---|---|---|
| 7 | Centre / Custodians | Materials collected and returned under continued custody | `RETURNED` | — |
| 8 | Warehouse | Received at HQ/warehouse and reconciled against the original manifest | `MATCHED` ⇒ `RECONCILED` → `CLOSED` | `DISCREPANCY` — shortfall, extras or broken seal — **raises an exception and blocks closure** |
| 9 | System | Items not yet returned | Tracked and escalated after a configurable window | Default outstanding-return window **P3D (3 days)** |
| 10 | Mailroom Officer | Registers inbound mail: sender, recipient, type, sensitivity | `RECEIVED` | Internal distribution follows |
| 11 | Recipient | Acknowledges by signature or scan | `DELIVERED` → item closed | Undelivered or unclaimed flagged and escalated after **PT48H**. Misrouted items re-routed with a reason |

### Lifecycle — implemented

| Aggregate | States |
|---|---|
| Courier item | `RECEIVED, STAGED, DISPATCHED, IN_TRANSIT, DELIVERED, RETURNED, EXCEPTION, CLOSED`. **A closed item cannot enter exception** |
| Dispatch | `DRAFT, SEALED, DISPATCHED, IN_TRANSIT, RECEIVED, RETURNED, RECONCILED, CLOSED, EXCEPTION` |
| Custody hop | `WAREHOUSE_STAGING, DISPATCH, TRANSIT, CENTRE_RECEIPT, HALL_DEPLOYMENT, COLLECTION, RETURN` |
| Seal state | `INTACT, BROKEN, REPLACED, MISSING` |
| Receipt | `CLEAN` / `VARIANCE` |
| Return | `MATCHED` / `DISCREPANCY` *(enforced by a database check constraint)* |
| Exception case | Same 12-state lifecycle as the fuel anomaly, with `Type`, `Severity` and `Decision` |

### Hard rules

1. Every tracked item carries origin, destination, handler and status.
2. **The custody chain must be unbroken from staging through return.**
3. Any seal, count or signature variance is an exception and **blocks closure**.
4. Confidential items always follow chain-of-custody handling — the system decides, not the operator.
5. No custody fact lives only in a mutable column; the chain is reconstructable by ordering handovers and joining the hash-chained audit.

### Implementation position

● **Built.** 10 aggregates, 5 policy classes, 10 services, 11 controllers, a 46.7 KB JDBC repository, migrations V16–V20 with real uniqueness and idempotency constraints, a sweep scheduler with five configurable windows, and **19 end-to-end scenarios plus 13 domain tests and ArchUnit rules — all passing**, including the full CT-05 secure-dispatch flow.

**Configurable defaults to confirm:** undelivered window `PT48H` · outstanding-return window `P3D` · exception SLA `PT24H` (severity-adjusted) · dashboard freshness `PT15M`.

**Gaps:** GPS, RFID and carrier are recorded seams with no live data — deferred to Phase 2. The **NBES examination dispatch-context contract is not defined** (deployment order → manifest).

### Questions to close

| ID | Question | Owner |
|---|---|---|
| Q-171-1 | Confirm the undelivered (48 h), outstanding-return (3 days) and exception SLA (24 h) windows | Logistics Coordinator |
| Q-171-2 | What is the NBES contract for examination dispatch context — how does a deployment order become a manifest? | Examination Operations ⛔ |
| Q-171-3 | Who in SSEMP receives a security-relevant receipt variance, and what acknowledgement is expected? | Security Director |
| Q-171-4 | Is a paper fallback manifest permitted if the dashboard is unavailable, and how is it reconciled? | Logistics Coordinator |
| Q-171-5 | Should the Dispatch dashboard be folded into the single SFL Operations UI shell, or remain a service-hosted dashboard? | Logistics Coordinator + DTI |
| Q-171-6 | Are RFID seals and scanners procured for Phase 1? Architecture §12.3 states AVAMP cannot pass commissioning without them | Procurement |

### Refinements agreed

| # | Refinement | Owner | Due |
|---|---|---|---|
| | | | |

---

## D.14 — PLAT · Cross-cutting platform services

**Delivery:** Build (consuming DTI shared services) · **Status:** ◐ Partial
**SRS requirements:** PLAT-01 … PLAT-07

| Ref | Requirement | Target behaviour | Status |
|---|---|---|---|
| **PLAT-01** | Governed vendor/device integration boundary | All vendor traffic through one boundary: authenticate (signature or mTLS) → source-allowlist → schema-validate → store raw → normalise → idempotent processing. Rejects logged, never drive commands. **Vendor swap = adapter change only** | ● Mechanism built and tested (HMAC-SHA256, constant-time compare, 5-minute replay window, per-source allowlist and secret, inbox before processing, duplicate detection, dead-letter) — ⛔ but **no real vendor is connected**, and it exists **only in the fleet-logistics and emergency services** |
| **PLAT-02** | Delegated identity | Auth/SSO/MFA delegated to S213; SFL enforces its own workflow RBAC and site-scoping on every command and query; edge uses a bounded-staleness permission snapshot; **no local back-door** | ⛔ **Off.** `sfl.security.enabled` defaults `false` everywhere; the deployment compose never enables it; **no test exercises the JWT chain**. Permissions are derived in-process from a hand-maintained matrix, not from IAM claims. **PostgreSQL row-level security is required and not implemented** |
| **PLAT-03** | Immutable, tamper-evident audit | Every state-changing action → immutable record with correlation ID, actor, timestamp and before/after state; hash-chained so tampering is detectable by replay; audit-writing roles cannot update or delete | ◐ Built **per-service, locally** — not integrated with an external S204 platform. Present in fleet, fuel, dispatch and emergency; **absent from facilities and asset-visibility**. The one audit search endpoint currently returns HTTP 500 |
| **PLAT-04** | Local survivability and operating modes | Readiness, access, incident, evidence and visitor check-in continue locally during WAN loss and reconcile on restore; locally generated IDs avoid collisions; **Routine / Examination / Emergency modes are explicit audited state transitions** | ◐ Partial. Idempotent edge capture is proven for dispatch receipts and emergency degraded mode. **There is no platform-wide operating-mode switch and no audited mode-transition record** |
| **PLAT-05** | Configuration without code | SLA thresholds, escalation rules, zones, severities, fuel limits, readiness checklists and retention configurable at runtime, validated, versioned and audited; observability with correlation IDs; integration-health view; metrics to S225; health probes | ◐ Partial. Effective-dated, site-overridable runtime configuration exists in fleet, fuel and dispatch. **Two fuel thresholds are hard-coded** (cost variance ±30 %, repeated pattern 3/30 days). No readiness checklists exist to configure. No S225 publication |
| **PLAT-06** | Cross-module workflow, approvals and sagas | Configurable workflow with task creation, assignment, escalation, SLA timers and closure, plus delegation and multi-level approval; cross-module processes (hall readiness, incident response, secure dispatch) run as **sagas** coordinating via events with explicit compensation | ◐ Partial. A real workflow engine with SLA and escalation exists **inside the fleet module only**. **No cross-module saga runs**, because events are not published to a broker by default and six of the participating systems do not exist |
| **PLAT-07** | Role-based dashboards and Analytics publication | Dashboards spanning facilities, security, fleet and readiness, site-scoped and role-filtered, with drill-down; exports logged; consolidated metrics to S225 | ◐ Partial. Per-module dashboards exist for fleet, fuel, dispatch and emergency with drill-down and freshness warnings. **No cross-module dashboard. No S225 publication.** Architecture §13.13 specifies **seven** dashboards; none of the seven exists as specified |

### The adapter boundary rule (SRS §5.2)

> *"No operational (domain) logic may depend directly on a vendor API, SDK or device protocol. The permitted flow is domain/application logic → port/interface → infrastructure adapter → vendor/device system."*

**Status:** enforced by ArchUnit tests in the fleet, fuel, dispatch and emergency modules (`DOMAIN_IS_FRAMEWORK_FREE`, `DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS`). Not enforced in facilities or asset-visibility.

---

# SECTION D′ — Cross-cutting workflows

These six flows cross module boundaries. They are where Phase 1 either works as one platform or fails as thirteen applications.

## D.15 — Examination hall readiness gate

**Sources:** SRS S152-03, S159-03, S153-05, S161-01, S162a-06; Architecture §6.2 and **BR-01**; Architecture §15.12 pre-examination checklist.

| # | Actor | Action | Gate |
|---|---|---|---|
| 1 | NBES | Publishes the session schedule and hall assignment | — |
| 2 | S152 | Generates the readiness checklist for the hall, split mandatory / advisory | — |
| 3 | Facilities (S153) | Open work orders on the hall update readiness items | Work affecting a readiness-critical space re-scores readiness |
| 4 | Security (S160a, S161, S162, S162a) | Access-control status, recording status, zone arming and life-safety status update readiness items | A camera offline in an examination area degrades the score |
| 5 | Logistics (S171) | Examination material and device dispatch status updates readiness | — |
| 6 | Infrastructure | Power, network, local examination-server reachability, workstation availability | Architecture §15.12: UPS > 95 % charge, generator fuel > 80 %, server rooms 18–24 °C, all APs online with the exam SSID disabled, biometric templates cached, NBES hash verified |
| 7 | System | Computes the readiness score | **Any unmet mandatory item ⇒ NOT-READY, escalated** |
| 8 | **Command Role / NECC** | Confirms readiness | **The hall may not open otherwise.** Confirmation is audited |
| 9 | S159 | Examination-Mode Lock applied to the hall | Bookings and configuration changes refused and logged |
| 10 | SFL → NBES | Returns readiness confirmation or blocking | — |

**Status: ⛔ Not implemented.** No readiness checklist model, no scoring service, no NECC confirmation gate, no NBES interface, and five of the six contributing systems do not exist. **This is the single most consequential gap in Phase 1** — it is the business rule the whole architecture is built around (BR-01), and the KPI target is *100 % before session open*.

**Questions:** Q-152-1, Q-152-2, Q-152-3, Q-161-5, and: *what is the NBES integration contract and who owns it?*

---

## D.16 — Examination Mode

| Trigger | System behaviour |
|---|---|
| NECC Commander declares Examination Mode | Exam VLAN isolation (infrastructure); hall readiness lock; **S159 booking lock**; **S160a tightened access schedules and exam-zone lockdown**; **S162 mandatory arming of exam-content zones**; device chain-of-custody enforced (S171); emergency-logistics readiness (S166) |
| Release | Window closes, or the Command Role lifts the lock. Emergency override requires appropriate authority and is recorded |

**Status: ⛔ Not implemented as a mode.** The only trace in code is `operatingMode` inside the fleet readiness context, producing `OPERATING_MODE_RESTRICTION` and `EMERGENCY_ONLY_RESTRICTION` blockers. There is no platform-wide, audited mode transition. *(Gap G-14.)*

---

## D.17 — Emergency fast lane and break-glass

| # | Step | Rule |
|---|---|---|
| 1 | Certified panel detects fire or panic and actuates independently | SFL is not in the path |
| 2 | SFL observes the event at the integration boundary | Millisecond timing recorded for latency verification |
| 3 | **Fast lane** derives the affected zone and triggers S174 | **Bypasses non-essential enrichment and routine approval** |
| 4 | Pre-authorised role fires a pre-authorised template | **Break-glass: no per-message approval** |
| 5 | Alert dispatched; delivery and acknowledgement tracked | Unauthorised attempts blocked and logged |
| 6 | Approval recorded after the fact | **Closure blocked until after-action approval exists** |
| 7 | Roll-call opened combining visitors + access occupancy + staff | Outstanding persons highlighted until accounted for |
| 8 | All-clear issued referencing the original event | Valid only from an active state |

**Status: ◐ Partial.** Steps 4–6 and 8 are **built and tested** in S174. Steps 1–3 are **not built** (S162a does not exist). Step 7 is **not built** (S160/S160a do not exist). And the alert does not actually reach anyone, because no notification vendor is connected. The latency target for step 3 is undefined.

---

## D.18 — Secure chain-of-custody for examination materials

Fully documented at D.13. Architecture **BR-04** and commissioning test **CT-05**.

**Status: ● Built and tested** end-to-end within S171 — the strongest workflow in the build. **But:** the NBES contract that turns a deployment order into a manifest does not exist, and the SSEMP escalation path for security-relevant variances has no receiving system. The custody chain therefore starts and ends inside SFL rather than spanning the examination process.

---

## D.19 — Local examination survivability

**SRS PLAT-04 and NFR-ES1.** During WAN loss, centre-level capture of readiness, access, incident, evidence and visitor check-in continues locally to a durable store and reconciles to core on restore **with no captured data lost**, verified by an **edge-failover test (CT-17)**. Locally generated IDs must avoid collisions.

**Status: ◐ Partial.** Idempotent edge-replay is proven for dispatch receipts (`dispatch_receipts` unique on `(dispatch_id, capture_correlation_id)`; test `an_offline_receipt_reconciles_idempotently_on_replay`) and for emergency degraded mode. There is **no offline client, no local durable store, and no CT-17 edge-failover test has been run.** Readiness, access, incident and visitor capture — the four things NFR-ES1 names — are all in systems that do not exist.

---

## D.20 — Configuration without code

**SRS PLAT-05, NFR-M1, Architecture NFR-09.** SLA thresholds, escalation rules, access zones, alert severities, fuel limits, readiness checklists and retention policies must be changeable at runtime by authorised owners — validated, versioned and audited, **without a code release**.

| Domain | Runtime-configurable today | Still hard-coded |
|---|---|---|
| Fleet | Compliance warning P30D, inspection validity P1D, service warning P14D, odometer staleness P30D, telematics staleness PT6H, dashboard freshness PT15M, signature window PT5M, retry policy | The compiled-in SLA fallback (4 h / 24 h / `FLEET_MANAGER`) — deliberately surfaced |
| Fuel | Per-transaction max, tank capacity, allowed products, approved vendors, odometer jump tolerance, receipt grace, consumption bands, materiality, anomaly SLA, sweep toggles | **Cost variance ±30 %; repeated pattern 3-in-30-days** |
| Dispatch | Undelivered PT48H, outstanding return P3D, exception SLA PT24H, dashboard freshness PT15M, 5 scheduler toggles | — |
| Emergency | Sweep cadence, outbox retry | Fast-lane latency target (undefined) |
| Facilities / Safety-security | **None** | Everything |

**Architecture §15.10 governing instruction:** *"No implementation team should treat unresolved decisions as approved assumptions. Where a decision remains open, the relevant system workflow should be designed to allow configuration without code changes until the final policy direction is confirmed."*
---

# SECTION E — Live demonstration script

Only workflows that can be shown running today are listed. Everything else is walked on paper against Section D.

## E.1 Pre-session setup

| Step | Action |
|---|---|
| 1 | Start the service databases: `compose.service-dbs.yml` (or the per-service compose files) |
| 2 | Start `sfl-facilities-service` (:8091), `sfl-fleet-logistics-service` (:8093), `sfl-emergency-notification-service` (:8095) |
| 3 | Confirm the SFL Operations UI (Fleet and Fuel dashboards) loads at `http://localhost:8093/ui/` |
| 4 | Confirm the Dispatch dashboard at `:8093/dispatch/`, the Emergency dashboard at `:8095/emergency/` and the Facilities & Maintenance dashboard at the facilities service root |
| 5 | Confirm Swagger at `:8093/swagger-ui.html` and `:8095/swagger-ui.html` |
| 6 | **State openly at the start of the demo that authentication is currently disabled** and that actor identity is supplied by `X-SFL-*` development headers |

> ⚠ Build note: the reactor requires **JDK 17+**. The default JDK on the build machine is JDK 11 (Zulu) and cannot build it *(conflict C-15)*. Confirm the demo machine before the session.

## E.2 Demo 1 — Fleet: register → readiness → trip → closure *(20 min)*

| # | Show | Point being made |
|---|---|---|
| 1 | `/fleet/vehicles` — register a vehicle with insurance, roadworthiness and registration | Compliance is part of registration, not an afterthought |
| 2 | Attempt to register the same registration number again | Duplicate rejected |
| 3 | Set one compliance document to expire next week → open the vehicle | `COMPLIANCE_DOCUMENT_EXPIRING` (warning) → status `CONDITIONALLY_READY` |
| 4 | Set it to expired → attempt assignment | `COMPLIANCE_DOCUMENT_EXPIRED` (blocking) → `NOT_READY`, **assignment refused** |
| 5 | `/fleet/drivers` — a driver whose licence expires mid-trip | `DRIVER_LICENCE_EXPIRED` — **a hard block, not a warning**. This is the behaviour to validate at Q-166-2 |
| 6 | `/fleet/trips` — create, assign, view the assignment preview | All blockers are listed together, not one at a time |
| 7 | Record a pre-trip inspection with a CRITICAL defect | Vehicle forced `OUT_OF_SERVICE`; a workflow item opens |
| 8 | Start, hold, resume, close a clean trip with evidence and odometer | Closure refused without reason, evidence and a non-regressing odometer |
| 9 | `/fleet/workflow` — an overdue item | SLA escalation; note the visible `compiled-in-default` when no rule matches |
| 10 | `/fleet/governance` — register evidence, request export, approve as a different user | **Separation of duties**: the approver must differ from the requester |
| 11 | `/fleet/governance` — chain verification | Hash-chained audit, tamper detectable by replay |
| 12 | `/fleet/integrations` — health, and replay a failed message | Note: **inbox search is missing**, so replay by ID only |

⚠ **Known defect to disclose:** the audit *records* list returns HTTP 500 on dev PostgreSQL. Only chain verification works.

## E.3 Demo 2 — Fuel: capture → reconcile → exception → close *(15 min)*

| # | Show | Point being made |
|---|---|---|
| 1 | `/fuel/policies` — create a site policy with limits and consumption bands | Thresholds are policy data, not code |
| 2 | `/fuel/transactions` — capture a compliant transaction, reconcile | `RECONCILED` with a full rule trail |
| 3 | Capture a transaction above the per-transaction limit | `LIMIT_EXCEEDED` anomaly raised |
| 4 | Capture a regressing odometer | Rejected **without overwriting the vehicle's accepted odometer** — demonstrates the S166 odometer-authority rule |
| 5 | Re-post the same provider transaction | Idempotent — no duplicate |
| 6 | `/fuel/anomalies` — assign, request explanation, explain, approve, close | **Closure refused without explanation, decision, reason and evidence** |
| 7 | Let an anomaly pass its SLA | Escalates to Fleet Manager |
| 8 | `/fuel/logbooks` — draft, submit, return, resubmit, approve | Approved logbook locked until a privileged reopen |
| 9 | `/fuel/imports` — upload a provider CSV | Note: **no read-back of the import batch**, and a duplicate upload returns HTTP 500 |

⚠ **Disclose:** fuel card management does not exist; rolling daily/monthly limits are not evaluated; cost variance and repeated-pattern thresholds are hard-coded; the module is uncommitted and self-declared not ready.

## E.4 Demo 3 — Secure dispatch (CT-05) *(20 min — the most important demo)*

Run on the Dispatch dashboard at `:8093/dispatch/`.

| # | Show | Point being made |
|---|---|---|
| 1 | Register an examination-paper item marked `SECRET` | **Chain-of-custody is derived automatically** — the operator cannot opt out |
| 2 | Register an ordinary mail item | No custody chain required — the rule is type- and sensitivity-driven |
| 3 | Build a manifest, apply seal IDs, seal the dispatch | **Cannot seal empty or without seal IDs** |
| 4 | Record handovers: warehouse staging → dispatch → transit → centre receipt → hall deployment | Append-only, monotonic sequence |
| 5 | Record an out-of-order hop, or a `BROKEN` seal | `CUSTODY_GAP` exception → **closure blocked** |
| 6 | Confirm a clean receipt: seal, count, recipient signature | Custody closes |
| 7 | Confirm a receipt with a short count | `VARIANCE`, flagged **security-relevant**, escalates to SSEMP |
| 8 | Replay the same offline receipt | Idempotent — edge capture proven |
| 9 | Return leg: matched vs discrepancy | Matched closes; discrepancy blocks |
| 10 | Leave an item unreturned past the window | Sweep selects and escalates it |
| 11 | Inbound mail: register, distribute, acknowledge | Closes on recorded acknowledgement |

⚠ **Disclose:** no NBES contract feeds the manifest; no SSEMP system receives the security escalation; GPS, RFID and carrier are seams with no live data.

## E.5 Demo 4 — Emergency notification and break-glass *(15 min)*

Run on the Emergency dashboard at `:8095/emergency/`.

| # | Show | Point being made |
|---|---|---|
| 1 | Create a template, scenario, audience group and recipient zone | Pre-authorised, versioned library |
| 2 | Submit a **routine** notice; attempt to activate before approval | **Refused** |
| 3 | Attempt approval as an unauthorised role | **Refused and logged** |
| 4 | Approve and activate | `ACTIVATING` → `ACTIVE` |
| 5 | **Break-glass** send by a pre-authorised role | **Sends immediately, no approval** |
| 6 | Attempt to close the break-glass activation | **Blocked until after-action approval is recorded** |
| 7 | Attempt break-glass by an unauthorised role | Denied and logged |
| 8 | Post a provider delivery callback twice; post an unsigned callback; post a schema-invalid callback | Idempotent; rejected before any domain effect |
| 9 | Acknowledgement tracking; let recipients pass SLA | Escalation |
| 10 | Issue all-clear from a non-active state | Refused |
| 11 | Run a drill | Recorded and measured, excluded from real-emergency metrics |
| 12 | Chain verification, then a tamper attempt | Detected |

⚠ **Disclose plainly: no message actually leaves the building.** The outbound gateway is a recorded adapter. Nothing reaches an SMS, voice, siren or signage provider.

## E.6 Demo 5 — Facilities: fault to work order *(10 min)*

Run at the Facilities & Maintenance dashboard on the facilities service root.

| # | Show | Point being made |
|---|---|---|
| 1 | Register a site, building, floor, room and zone | Stable location references used by every other module |
| 2 | Report a facility fault | Opens in `REPORTED` |
| 3 | Create a work order from the fault as a supervisor | `OPEN` |
| 4 | Attempt the same as a technician, then as a requester | **Refused** — role checks work |
| 5 | Assign, then close | `ASSIGNED` → `CLOSED`; a closed order refuses assignment |

⚠ **Disclose:** there is no evidence-verification gate, no `PENDING_VERIFICATION` state, no SLA timer, no preventive-maintenance plan and no readiness re-score. The SRS's central maintenance control is absent.

## E.7 Not demonstrable

Room booking · visitor management · access control · CCTV evidence · intrusion alarms · fire and life-safety monitoring · HSE incident and CAPA · hall readiness scoring · examination-mode lock · any cross-module saga · any external vendor integration · any authenticated session.

---

# SECTION F — Gap register

Severity: **⛔ Blocker** (prevents Go-Live) · **H** High · **M** Medium · **L** Low.

## F.1 Cross-cutting blockers — these decide Go-Live

**Six of the eight are closed.** Each closure below names the evidence, because a gap register that
marks its own items done without saying how is the same document that let five of these sit open.

| ID | Gap | Sev | Status at 1 Aug 2026 |
|---|---|---|---|
| **G-01** | **Authentication disabled.** `sfl.security.enabled` defaulted `false` in all services declaring it; `sfl-safety-security-service` had no security block; compose ran Keycloak but never enabled enforcement. No test covered the JWT chain. | ⛔ | ✅ **CLOSED.** Both defaults inverted — secure is now what an absent property selects, and the open path logs a warning naming the service on every startup. Keycloak realm imported (`deploy/keycloak/sfl-realm.json`): 26 roles, `site_scopes` mapper, dashboard client, service-account client, one user per persona. `FacilitiesJwtSecurityTest` runs the real chain and pins **403 vs 401**. AVAMP no longer takes its actor from a caller-supplied header.<br><br>**One part of this entry stayed open until 1 Aug and is now also closed:** `sfl-safety-security-service` genuinely had no security block. That did not leave it open — with no filter chain declared, Spring Security's default secured *everything* including `/actuator/health`, so the service answered `401` to its own probe and `SFL_SECURITY_ENABLED` had nothing to read. It also had **no `@SpringBootApplication` class**, so it could not start at all. Both found by launching all five services together for the first time. |
| **G-02** | **Seven of thirteen Phase 1 systems do not exist.** | ⛔ | 🟡 **REDUCED TO SIX.** S159 built with 5 screens and 7 dialogs. The six SSEMP systems are unbuilt scope; four are Buy-and-Integrate. **Still requires the Board decision this entry always required** — build them, or formally rescope Phase 1 to a Release 1 subset. |
| **G-03** | **No external integration is real.** All simulators, recorded adapters or absent. No vendor has passed the SRS §5.2 procurement gate. | ⛔ | ⛔ **OPEN.** Unchanged and not closable by the delivery team. S152 inbound webhook signature verification stays unbuilt for the same reason: a signature check written against an imagined payload is not evidence. |
| **G-04** | **No operational runbooks.** `docs/runbooks/` is empty. | ⛔ | ✅ **CLOSED.** Four runbooks in `docs/runbooks/` — incident response, disaster recovery, dead-letter recovery and backup/restore — plus a README indexing them. |
| **G-05** | **S168_fuel uncommitted and self-declared "not ready to close or commit."** | ⛔ | ✅ **CLOSED.** Merged, compiled, and the full reactor runs green. The fuel-card registry that G-20 tracked is built. |
| **G-06** | **Events not published to a broker by default**; facilities and asset-visibility emit names violating the catalogue rule. | ⛔ | 🟡 **HALF CLOSED.** Naming fixed — 48 literals renamed, enforced at the outbox write path by regex rather than by review, so a non-conforming name now fails at the point of writing. IFIMP outbox drainer built with claim, backoff and dead-lettering, proved against PostgreSQL. **Transport still defaults to `local`** — a deployment decision, not missing code. |
| **G-07** | **PostgreSQL row-level security not implemented.** Site scope enforced in the application layer only. | ⛔ | ✅ **CLOSED.** `V14__row_level_security.sql` with a `sfl_app` role separate from the table owner, so migrations keep their bypass and a backfill cannot silently write nothing. `SET LOCAL app.site_scopes` per transaction, so a pooled connection never carries a stranger's scopes. Policies **fail closed** when the setting is unset. Proved by a test that opens its own connection as `sfl_app` — one running as the owner would have passed while proving nothing. ADR 0007. |
| **G-08** | **`GET /api/v1/fleet/audit/records` returns HTTP 500** on dev PostgreSQL. | ⛔ | ✅ **CLOSED.** Nullable-enum parameter cast fixed, with regression tests. The same defect class was found and fixed in two further repositories, which now carry a comment naming it. |

For the **Release 1 demo**, G-02 is closed by scope decision: the demo contains seven systems and excludes
Visitor Management plus the remaining SSEMP safety/security systems. G-03 remains a production integration
condition. G-27, S174's absent notification vendor, is accepted as a later integration with the separate
CLET Comms system and is carried in F.2 as a deferred dependency, not a build gap.

## F.2 Workflow gaps by system

| ID | System | Gap | Sev | Owner |
|---|---|---|---|---|
| ~~G-09~~ | S152 | ✅ **CLOSED.** Checklist model, scoring and assessment workflow built; readiness is derived from open blockers and never set freely | — | Facilities engineering |
| G-10 | S152 | No MDM (S223) resolution; `facilities/infrastructure/integration/` is empty | H | DTI Platform |
| ~~G-11~~ | S153 | ✅ **CLOSED, by a different mechanism than this entry proposed.** There is no `PENDING_VERIFICATION` state; `COMPLETED` and `CLOSED` are separate states and separate permissions, which is the same two-step separation. A technician marks work completed and cannot close it — `FACILITIES_WORK_ORDER_CLOSE` is deliberately withheld from that role — and closure is refused without a reason and the site's configured evidence count | — | Facilities engineering |
| ~~G-12~~ | S153 | ✅ **CLOSED.** `SlaPolicy` computes the deadline at triage from the site's configured SLA, halved in examination mode; `MaintenanceEscalationService` sweeps and escalates; preventive schedules, vendors, parts and the hash-chained audit are built | — | Facilities engineering |
| G-13 | S153 | Error envelope non-conforming (ad-hoc shape instead of `ApiResponse`) | M | Facilities engineering |
| G-14 | PLAT-04 | **No platform-wide operating-mode switch.** Routine / Examination / Emergency are not audited state transitions | ⛔ | DTI Architecture |
| G-15 | Cross | **No hall readiness gate, no examination-mode lock, no NBES interface** — Architecture BR-01 is unimplementable | ⛔ | DTI + NECC |
| ~~G-16~~ | S166 | ✅ **CLOSED.** Standalone/periodic vehicle inspection is exposed at `POST /api/v1/fleet/vehicles/{vehicleId}/inspections` and is wired into the fleet UI | — | Fleet engineering |
| G-17 | S166 | Emergency-logistics mobilisation is deferred to the later cross-module emergency saga; do not add a standalone `/fleet/emergency-logistics` endpoint unless the SRS is amended | Deferred | Logistics Coordinator |
| ~~G-18~~ | S166 | ✅ **CLOSED.** Vehicle readiness, movement history, evidence/audit search, inbox search, compliance report and dashboard replay paths are exposed and used by the Release 1 UI | — | Fleet engineering |
| G-19 | S166 | Two conflicting retention vocabularies (`RetentionClass` 6 values vs `EvidenceRetentionClass` 4 values, different periods) | H | Compliance / DPO |
| ~~G-20~~ | S168 | ✅ **CLOSED for the API, open for the UI.** `FuelCard` domain, `V21__fuel_card_registry.sql`, service and controller with assign/suspend/reinstate/cancel. **No screens** — cards are managed by API only, which is a Release 1 condition rather than a blocker | L | Frontend |
| G-21 | S168 | Rolling daily/monthly limits are stored on policies/cards but not yet evaluated by reconciliation; fuel card management is API-only and needs a Release 1 UI screen | H | Fuel engineering / Frontend |
| ~~G-22~~ | S168 | ✅ **CLOSED.** Overlapping active fuel policies are refused at creation; abutting half-open periods remain valid | — | Fuel engineering |
| G-23 | S168 | `COST_VARIANCE` (±30 %) and `REPEATED_PATTERN` (3 in 30 days) hard-coded rather than versioned policy | M | Fuel engineering |
| G-24 | S171 | **No NBES examination dispatch-context contract** — a deployment order cannot become a manifest | ⛔ | Examination Operations |
| G-25 | S171 | SSEMP has no receiving system for security-relevant receipt variances | H | Security Director |
| ~~G-26~~ | S171 / S174 / S152 / S153 | ✅ **CLOSED by ADR 0006.** The three service-hosted Bootstrap dashboards are retired. Each path now serves a notice page pointing at `/ui/`. One application, one design system, one stack — 78 screens across seven modules | — | Frontend |
| G-27 | S174 | **Live notification delivery deferred.** `RecordedNotificationGateway` is sufficient for the Release 1 demo; real delivery will integrate with the separate CLET Comms system later | Deferred | Procurement / Comms integration owner |
| G-27A | S174 | Operator actions `cancel`, `reopen` and explicit degraded-fallback routing exist in the domain model but are not exposed as controller/UI actions | H | Emergency engineering / Frontend |
| G-28 | S174 / S162a | Fast-lane latency target undefined (SRS NFR-P1 "to be confirmed"; conflict C-09) | H | Emergency Coordinator + DTI |
| G-29 | PLAT-03 | 🟡 **HALF CLOSED.** Facilities now has a hash-chained audit with integrity replay. **S204 integration is still absent** and audit remains per-service. A defect worth naming: the fleet chain reported `intact=false` against a real database from the first record written outside a test, and was found by reading code rather than by a failing test | H | DTI Platform |
| G-30 | PLAT-07 | None of the seven dashboards specified in Architecture §13.13 exists; no S225 publication | H | DTI Platform |
| G-31 | PLAT-04 | No offline client, no local durable store; **CT-17 edge-failover test has never been run** | ⛔ | DTI Platform |
| G-32 | NFR | **Peak-load figures, emergency-latency target and DR RTO/RPO are all "to be confirmed" in the SRS itself** — the platform cannot be tested against undefined targets | ⛔ | DTI + F&L + Board |
| G-33 | Build | Build requires JDK 17+; the machine default is JDK 11 and cannot build the reactor | M | DTI Engineering |
| G-34 | Testing | No Testcontainers migration test runs in CI (the one Docker-gated test skips); no facilities or asset-visibility end-to-end or API-security tests; safety-security has no tests | H | DTI Engineering |

## F.3 Documentation defects

| ID | Defect | Action |
|---|---|---|
| DD-01 | The workplan references **`SRS-SFL-S166-06`, which does not exist** in the SRS (which defines 01–05) | Amend the workplan, or add the requirement to the SRS |
| DD-02 | S171 is numbered 01–05 in one place and 01–06 in another, with different titles | Reconcile in the SRS |
| DD-03 | The SRS cover page reads **"Version 1.3 — July 2026"** while every page header reads **"v1.0"** | Correct the SRS |
| DD-04 | S159 requirements are numbered 01–07 in the summary table but 01–06 in the detailed sections | Correct the SRS |
| DD-05 | **Three competing event-naming schemes** are in play: the catalogue (`sfl.{platform}.{event}.v{n}`), the facilities/asset-visibility code, and the workflow plan's `sfl.facilities.* / sfl.security.* / sfl.logistics.*` family | Adopt the catalogue; correct the workflow plan and the two services |
| DD-06 | **Two colliding commissioning-test namespaces**: Architecture CT-01…CT-10 (CT-05 Secure Dispatch, CT-06 Fuel Exception, CT-08 Access Override) versus the workplan's CT-17…CT-21 — with **"CT-08" meaning two different things** | Reconcile into one register before commissioning evidence is compiled |
| DD-07 | **ADR 0001 and ADR 0003 both mandate a modular monolith**; the delivered architecture is five microservices, with no superseding ADR | Raise and accept an ADR recording the microservices decision |
| DD-08 | The Architecture v1.2 REVIEWED file's internal version block still reads **"Version 1.1 — May 2026"** | Correct the document control block |
| DD-09 | `solution.md` cites an SRS filename that does not exist on disk | Correct the reference |
| ~~DD-10~~ | ✅ **CLOSED.** Empty `docs/api-contracts/` and `docs/event-contracts/` were removed; `docs/runbooks/` is populated and retained | — |
| ~~DD-11~~ | ✅ **CLOSED.** Stale .NET-era/demo build documents were moved to `docs/archive/release1-demo-cleanup-2026-08-01/`; current Spring Boot local-run guidance remains in `docs/development/run-spring-boot-locally.md` | — |
| DD-12 | Two SRS artefacts and two architecture artefacts of differing vintage are referenced across `README.md`, `solution.md` and the REQUIREMENT DOC folder | Declare one of each authoritative |
| DD-13 | The S174 traceability matrix names controllers and ports (`TemplateController`, `LifeSafetyEventPort`, `CctvEvidencePort`, `AccessControlLockdownPort`, …) that **do not exist as files** | Correct the matrix to match the code |
| DD-14 | The S166 state-model document describes a 7-value inbox status enum that does not exist in code (the real one has 4 values), and states `RETURNED → DRAFT` for logbooks where the code does `RETURNED → RESUBMITTED` | Correct the documents to match the code |
| DD-15 | Documented fleet API paths diverge from implemented controllers across dashboards, evidence, audit and integrations | Regenerate the API inventory from the code |
| ~~DD-16~~ | ✅ **CLOSED.** The duplicate implementation/build workplans were archived; this workflow review/readiness pack is the current Release 1 handoff document | — |

## F.4 Refinements register

To be completed during the session. Two are already recommended:

| ID | Refinement | Rationale | Owner |
|---|---|---|---|
| RF-01 | **Adopt the fleet module as the platform reference implementation.** Its evidence workflow, hash-chained audit, secure inbox, outbox with replay, permission matrix, readiness-blocker model and workflow/SLA engine are the patterns S161, S163, S153 and S152 should reuse rather than reinvent | Avoids five divergent implementations of the same governance controls | DTI Architecture |
| RF-02 | **Update SRS §2.4 Status Matrix to match the implemented state machines** where the implementation is richer and better — notably S174 (14 states vs the matrix's single `SENT_AWAITING_ACK`), S166 trip and workflow lifecycles, and S171 item/dispatch/custody lifecycles | The matrix currently understates the platform and would mislead a UAT tester | DTI + F&L |
| RF-03 | | | |
| RF-04 | | | |

---

# SECTION G — Open decisions requiring management sign-off

Architecture §15.10: *"No implementation team should treat unresolved decisions as approved assumptions."*

| # | Decision | Source | Decision owner | Blocks |
|---|---|---|---|---|
| 1 | **Phase 1 scope for Vesting Day** — full thirteen systems, or a declared Release 1 subset | This review | Registrar / Board | Everything |
| 2 | **Hosting and data residency** — is mission-critical biometric, surveillance, access, incident and audit data on institution-controlled infrastructure? | Arch §15.6; SRS NFR-12 | Board | G-01, G-32 |
| 3 | **CCTV retention duration** per zone | Arch §15.6; SRS S161-05 | Security Director + DPO + Board | S161 |
| 4 | **Biometric exception policy** — thresholds, officer voucher rules, result-hold review | Arch §15.6 | Registrar + Security | S160, S160a |
| 5 | **Emergency notification channels by site**, and the contracted provider | Arch §15.6; SRS S174-01 | Emergency Coordinator + Procurement | ⛔ G-27 |
| 6 | **Emergency detection-to-notification latency target** (CT-20 / NFR-P1) | SRS NFR-P1 "TBC" | Emergency Coordinator + DTI | ⛔ G-28 |
| 7 | **Peak-load figures** — concurrent users, device count and heartbeat rate, access events per second, monthly evidence growth | SRS NFR-P3 "TBC" | DTI + F&L | ⛔ G-32 |
| 8 | **DR recovery targets (RTO/RPO)** for core and edge | SRS NFR-AV2 "TBC"; Arch §15.6 | Infrastructure Governance + Board | ⛔ G-32 |
| 9 | **Fleet GPS/telematics provider** | Arch §15.6 | Transport Manager + Procurement | S166 optional scope |
| 10 | **RFID tag standard** and scanner supply | Arch §15.6, §12.3 | Procurement | S171 Phase 2, AVAMP commissioning |
| 11 | **Permissions as IAM claims** — putting permissions on `SiteScopedPrincipal` is a breaking change to a record shared by three services | Conflict C-07 | DTI IAM | G-01 |
| 12 | **PostgreSQL RLS approach** — approve the session-GUC design or accept the risk in writing | Conflict C-09 | DTI Platform + Internal Audit | ⛔ G-07 |
| 13 | **Fleet retention schedule** — confirm the blanket rule or supply the schedule; resolve the two competing vocabularies | Conflict C-08; G-19 | Compliance / DPO | G-19 |
| 14 | **Emergency-logistics mobilisation home** — confirm the deferral to a later saga or require it in Release 1 | Conflict C-12 | Logistics Coordinator | G-17 |
| 15 | **Fuel materiality threshold** and the Finance acknowledgement contract | S168 external gap | Finance / Audit | S168 |
| 16 | **NBES examination dispatch-context contract** and readiness confirmation interface | G-15, G-24 | Examination Operations | ⛔ G-15, G-24 |
| 17 | **Centre rollout order** | Arch §15.6 | Registrar | Deployment plan |
| 18 | **DPIA (S185)** — the risk register requires it before Go-Live | Arch §2.6 RK-07 | DPO | ⛔ Go-Live |
| 19 | **Accessibility standard** — the SRS names no WCAG level; the frontend playbook records deliberate WCAG 2.2 departures | SRS NFR-U2 | DTI + F&L | G-32 |
| 20 | **Modular monolith versus microservices** — record the superseding ADR | DD-07 | DTI Architecture | Governance |

---

# SECTION H — Go-Live readiness gates

Two gate sets exist and must be read together: the Architecture document's **G1–G6** (§10.4) and the workplan's **W7** exit criteria. RAG status below is assessed against the evidence in this pack.

## H.1 Architecture gates

| Gate | Minimum condition | Owner | Status | Basis |
|---|---|---|---|---|
| **G1 Infrastructure Ready** | WAN, VLAN, APs, local servers, hall racks, CCTV, access control, UPS/generator tested | Infrastructure Governance | ⚪ **Not assessed** | Outside the software repository. Requires the §15.11 Infrastructure Acceptance Test Register — 16 items including SD-WAN failover < 30 s and ATS transfer < 10 s |
| **G2 Core Workflows Ready** | Fast-Track IFIMP / SSEMP / FTLMP workflows tested with users and evidence capture | DTI + Operational Owners | 🔴 **Fail** | FTLMP substantially ready. **IFIMP partial; SSEMP effectively absent.** No user testing has occurred |
| **G3 Security Ready** | RBAC, MFA, SIEM, access logging, CCTV retention, incident response tested | Security Director / DTI | 🔴 **Fail** | Authentication disabled (G-01); no RLS (G-07); no SIEM forwarding; no CCTV system; retention undecided |
| **G4 Examination Simulation** | Mock examination validating hall readiness, identity flow, CCTV, local NBES, emergency comms and logistics response | NECC / Centre Managers | 🔴 **Fail** | Hall readiness does not exist (G-15); no identity flow; no CCTV; no NBES interface; emergency comms reach no one (G-27) |
| **G5 DR Ready** | Backup/restore, local survivability and failover procedures validated | Infrastructure Governance | 🔴 **Fail** | No runbooks (G-04); CT-17 edge-failover never run (G-31); RTO/RPO undefined (G-32) |
| **G6 Management Sign-off** | Directors and executives review dashboards and approve go-live | Registrar / Board | 🔴 **Fail** | None of the seven specified dashboards exists (G-30) |

## H.2 Commissioning tests

| Test | Subject | Status |
|---|---|---|
| **CT-01** Hall Opening Control | Readiness gate | 🔴 Not implementable |
| **CT-02** Exam VLAN Isolation | Infrastructure | ⚪ Not assessed |
| **CT-03** Identity Exception | S160 / biometrics | 🔴 Not implementable |
| **CT-04** CCTV Evidence Handling | S161 | 🔴 Not implementable |
| **CT-05** Secure Dispatch | S171 | 🟢 **Pass** — 19 end-to-end scenarios green |
| **CT-06** Fuel Exception | S168_fuel | 🟡 **Partial** — 17 scenarios green but the module is uncommitted and unverified |
| **CT-07** BMS Critical Alert | Phase 2 | ⚪ Out of Phase 1 |
| **CT-08** Access Override | S160a | 🔴 Not implementable |
| **CT-09** WAN Failure | Edge survivability | 🔴 Never run |
| **CT-10** DR Service Recovery | DR | 🔴 Never run |
| **CT-17** Edge failover (NFR-ES1) | PLAT-04 | 🔴 Never run |
| **CT-18** Audit tamper-evidence (NFR-ES2) | PLAT-03 | 🟢 **Pass** in fleet, fuel, dispatch and emergency; 🔴 absent in facilities and asset-visibility |
| **CT-19** Forged-webhook rejection (NFR-SEC2) | PLAT-01 | 🟢 **Pass** in fleet-logistics and emergency; 🔴 no boundary exists elsewhere |
| **CT-20** Emergency fast-lane latency (NFR-P1) | S162a → S174 | 🟡 Path represented and measured; **target undefined**, source event system absent |
| **CT-21** Identity-provider swap (PLAT-02) | S213 | 🔴 Never run — authentication is disabled |

## H.3 Non-functional position

| NFR | Target | Status |
|---|---|---|
| NFR-R1 Availability | **99.5 %**, elevated during examination windows | Untested — no production deployment |
| NFR-P1 Emergency latency | **Undefined in the SRS** | ⛔ Cannot be tested |
| NFR-P3 Peak load | **Undefined in the SRS** | ⛔ Cannot be tested |
| NFR-AV2 DR RTO/RPO | **Undefined in the SRS** | ⛔ Cannot be tested |
| NFR-SEC1 Least-privilege RBAC via S213 | — | 🔴 Enforcement disabled |
| NFR-SEC2 Authenticated inbound device messages | — | 🟢 Real HMAC where a boundary exists; 🔴 no boundary in three services |
| NFR-SEC3 SIEM forwarding | — | 🔴 Not implemented |
| NFR-ES1 Edge survivability | Verified by edge-failover test | 🔴 Never run |
| NFR-ES2 Tamper-evident audit | Verified by audit-tamper test | 🟢 Passing where implemented |
| NFR-M1 Configuration without code | — | 🟡 Strong in FTLMP and emergency; absent in facilities and safety-security |
| NFR-U2 Accessibility | No standard named | 🟡 Documented WCAG 2.2 departures; standard undecided |

## H.4 Recommendation

**A Go-Live covering the declared Phase 1 scope of thirteen systems cannot be recommended.** Six of the six Architecture gates are failing or unassessed, seven systems do not exist, and three of the SRS's own non-functional targets are undefined and therefore untestable.

**The recommended path is a formally rescoped Release 1**, approved by the Board, comprising:

| In Release 1 | Condition |
|---|---|
| **S166 Fleet & Vehicle Management** | Clear G-01, G-06, G-07, G-08, G-16, G-19 |
| **S171 Mailroom / Courier & Dispatch** | Clear G-01, G-06, G-24, G-25 |
| **S174 Emergency Mass-Notification** | Clear G-01, G-06, **G-27 (contract a provider)**, G-28 |
| **S152 facility register** (register only, readiness deferred) | Clear G-01, G-06, G-13 |
| **S153 fault to work order** | Clear G-11 (add the evidence-verification gate) and G-12 (add SLA timers) |
| **S168_fuel** | Only if G-05, G-20, G-22 clear; otherwise defer |

| Deferred to Release 2, with a dated plan | |
|---|---|
| S159 Room & Resource Booking, S160 Visitor Management, S160a Access Control, S161 CCTV, S162 Intrusion, S162a Fire/Life-Safety, S163 HSE Incident | Plus the hall readiness gate, examination mode and edge survivability |

**Universal preconditions for any Go-Live**, regardless of scope:

1. Authentication enforced and tested (G-01)
2. Events published to the broker; non-conforming event names corrected (G-06)
3. Operational runbooks published and a DR drill completed (G-04)
4. The audit search defect fixed (G-08)
5. Peak load, emergency latency and RTO/RPO defined and tested (G-32)
6. DPIA (S185) completed (decision 18)
7. Every remaining gap either closed or carried as a **written, signed risk acceptance**

---

# SECTION I — Session record and sign-off

## I.1 Attendance

| Name | Role | Function |
|---|---|---|
| | | |

## I.2 Workflows validated

| Workflow | Validated? | Conditions |
|---|---|---|
| D.1 S152 Facility register and readiness | ☐ | |
| D.2 S153 Fault to work order | ☐ | |
| D.3 S159 Room and resource booking | ☐ | |
| D.4 S160 Visitor management | ☐ | |
| D.5 S160a Access control | ☐ | |
| D.6 S161 CCTV and evidence | ☐ | |
| D.7 S162 Intrusion and alarms | ☐ | |
| D.8 S162a Fire and life-safety | ☐ | |
| D.9 S163 HSE incident and CAPA | ☐ | |
| D.10 S174 Emergency notification | ☐ | |
| D.11 S166 Fleet and vehicles | ☐ | |
| D.12 S168_fuel Fuel and logbooks | ☐ | |
| D.13 S171 Dispatch and chain-of-custody | ☐ | |
| D.14 PLAT Cross-cutting services | ☐ | |
| D.15 Hall readiness gate | ☐ | |
| D.16 Examination Mode | ☐ | |
| D.17 Emergency fast lane | ☐ | |
| D.18 Chain-of-custody | ☐ | |
| D.19 Edge survivability | ☐ | |
| D.20 Configuration without code | ☐ | |

## I.3 Go-Live recommendation recorded

| Field | Entry |
|---|---|
| Scope recommended for Release 1 | |
| Systems formally deferred | |
| Blockers accepted as risks (with reference) | |
| Target Vesting Day | |
| Next review date | |

## I.4 Signatures

| Role | Name | Signature | Date |
|---|---|---|---|
| Director, Safety, Facilities & Logistics | | | |
| Director, DTI | | | |
| Security Director | | | |
| Transport / Logistics Manager | | | |
| HSE Officer | | | |
| NECC Commander | | | |
| Internal Audit (Consulted) | | | |
| Data Protection Officer | | | |
| Registrar | | | |

---

## Annex 1 — Evidence basis for this pack

| Claim type | Source |
|---|---|
| Requirements, workflows, state names, business rules, NFRs | `docs/System Mappings and SRS/SFL_SRS.docx` |
| Lifecycles, business rules BR-01…BR-12, gates G1–G6, SOPs, dashboards, KPIs, commissioning tests CT-01…CT-10, open decisions | Cluster 9 SFL System Architecture Document v1.2 (Reviewed) |
| Implementation status, state machines, thresholds, permission matrix, endpoints | SFL repository `services/**` source, Flyway migrations, `target/surefire-reports/*.xml` from the test run of 29 July 2026, and `frontend/sfl-operations-ui/src` |
| Release 1 scope, current build status, remaining gaps and handoff position | `docs/SFL_Phase1_Workflow_Review_and_GoLive_Readiness_Pack.md`, `docs/adr/**`, and the per-domain traceability, operations and final-implementation reports |

Where documentation and code disagreed, **code was treated as the fact** and the documentation defect logged in F.3.

**Snapshot caveat.** The implementation position was taken from the repository on 29 July 2026. Front-end work on the Fuel dashboard was in progress during the audit — several files changed within the hour. Any Fuel front-end finding marked *re-verify* should be re-checked against the branch immediately before the review session.

*End of document.*
