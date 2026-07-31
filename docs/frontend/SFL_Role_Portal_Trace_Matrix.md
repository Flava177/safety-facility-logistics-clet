# SFL role → portal trace matrix

**All 26 `SflRole` values.** One row each, no omissions — a role missing from this table is a role
somebody signs in as and nobody has thought about.

**Authority, in precedence order.** `CLET_Comprehensive_Digital_System_Mapping_v2.docx` §30A.6.9 for
system identity, cluster ownership and phase (the SRS defers to it at §1.5); `SFL_SRS.docx` §2.3 for
the eleven user classes Phase 1 is contracted to serve; code as evidence of what is implemented, never
as a specification.

**Permission counts are read from the matrices, not transcribed.** Each was produced by asking
`FacilitiesPermissionMatrix`, `FleetPermissionMatrix`, `FuelPermissionMatrix` and
`DispatchPermissionMatrix` directly for every one of the 145 `SflPermission` values. Counting by hand
is how a matrix and a portal drift apart.

## Basis, and what each obliges

| Basis | Meaning | Obligation |
|---|---|---|
| **SRS** | Maps cleanly onto a §2.3 user class | Build the portal |
| **Derived** | A narrower specialisation of a §2.3 class within one system, where the mapping puts that system under a unit that would staff the specialisation | Build it, and state the derivation in one sentence |
| **Deviation** | No §2.3 class and no honest derivation | **Do not invent a portal.** Record the row, name the owner who must decide, leave the role on the narrowest existing view |

---

## The matrix

Permission counts are per programme: **I** = IFIMP (facilities), **F** = FTLMP (fleet + fuel +
dispatch), **S** = SSEMP (emergency).

| # | Role | SRS §2.3 user class | Mapping unit | Systems entitled | Permissions | Portal | Basis |
|---|---|---|---|---|---|---|---|
| 1 | `FACILITIES_DIRECTOR` | Facilities Director / Manager | Building & Infrastructure | S152, S153 | I 43 | Facilities operator (existing) | **SRS** |
| 2 | `FACILITIES_MANAGER` | Facilities Director / Manager | Building & Infrastructure | S152, S153 | I 38 | Facilities operator (existing) | **SRS** |
| 3 | `IFIMP_MAINTENANCE_SUPERVISOR` | Facilities Officer | Building & Infrastructure | S152, S153 | I 34 | Maintenance operator (existing) | **SRS** |
| 4 | `IFIMP_TECHNICIAN` | Maintenance Technician / Vendor | Building & Infrastructure | S152, S153 | I 19 | **My work queue** (new landing) | **SRS** |
| 5 | `VENDOR_TECHNICIAN` | Maintenance Technician / Vendor | Building & Infrastructure | S152, S153 | I 7 | **My work queue** (new landing) | **SRS** |
| 6 | `IFIMP_REQUESTER` | Room Requester / Host | Building & Infrastructure | S152, S153 | I 7 | **My requests** (new) | **SRS** |
| 7 | `FLEET_MANAGER` | Fleet / Logistics Officer | Transportation & Logistics | S166, S168, S171 | F 71 | Fleet operator (existing) | **SRS** |
| 8 | `FLEET_LOGISTICS_OFFICER` | Fleet / Logistics Officer | Transportation & Logistics | S166, S168, S171 | F 41 | Fleet operator (existing) | **SRS** |
| 9 | `FLEET_DRIVER` | **none** | Transportation & Logistics | S166, S168 | F 8 | **My driving day** (new, narrow) | **Deviation** |
| 10 | `DISPATCH_CONTROLLER` | Fleet / Logistics Officer | Transportation & Logistics | S171 | F 11 | Dispatch operator (existing) | **Derived** |
| 11 | `LOGISTICS_COORDINATOR` | Fleet / Logistics Officer | Transportation & Logistics | S171 | F 11 | Dispatch operator (existing) | **Derived** |
| 12 | `MAILROOM_OFFICER` | Fleet / Logistics Officer | Transportation & Logistics | S171 | F 7 | **Mailroom** (new) | **Derived** |
| 13 | `CENTRE_MANAGER` | **none** | Transportation & Logistics | S152, S153, S171 | F 6 · I 19 | **Centre receipts** (new, unnarrowed) | **Deviation** |
| 14 | `SECURITY_OFFICER` | Security Director / SOC Operator | Health, Safety & Security | S171, S174 | F 5 · S | Dispatch exceptions + emergency (existing) | **Derived** |
| 15 | `EMERGENCY_COORDINATOR` | Emergency Coordinator | Health, Safety & Security | S174 | S | Emergency operator (existing) | **SRS** |
| 16 | `SECURITY_DIRECTOR` | Security Director / SOC Operator | Health, Safety & Security | S174 | S | Emergency operator (existing) | **SRS** |
| 17 | `SOC_OPERATOR` | Security Director / SOC Operator | Health, Safety & Security | S174 | S | Emergency operator (existing) | **SRS** |
| 18 | `HSE_MANAGER` | HSE Officer | Health, Safety & Security | S152, S153, S174 | I 12 · S | **Read-only posture** (new) | **SRS** *(partially served — see below)* |
| 19 | `AUDITOR` | Auditor / Compliance / DPO | cross-programme | all | I 19 · F 22 | **Assurance** (new, cross-system) | **SRS** |
| 20 | `COMPLIANCE_OFFICER` | Auditor / Compliance / DPO | cross-programme | all | I 19 · F 27 | **Assurance** (new, cross-system) | **SRS** |
| 21 | `SFL_ADMIN` | System Administrator | cross-programme | all | I 47 · F 77 | **Administration** (new) | **SRS** |
| 22 | `DTI_ADMIN` | System Administrator | cross-programme | all | I 47 · F 17 | **Administration** (new) | **SRS** |
| 23 | `COMMAND_ROLE` | *(SRS §2.8 command contexts, not §2.3)* | cross-programme | all | I 20 · F 15 | **Command posture** (new, read-only) | **Derived** |
| 24 | `FLEET_REPORTING_VIEWER` | Fleet / Logistics Officer *(read-only)* | Transportation & Logistics | S166, S168, S171 | F 14 | **Read-only posture** (new) | **Derived** |
| 25 | `INTEGRATION_ENGINEER` | System Administrator | cross-programme | all | I 14 · F 8 | Integration health (existing) | **Derived** |
| 26 | `SERVICE_INTEGRATION` | **none — not a person** | — | — | I 13 · F 4 | **No portal.** Machine principal | **Deviation** |

**Unserved user class.** *Visitor Desk Officer* (§2.3) serves **S160 Visitor Management**, which is not
built. The class stays unserved, deliberately and on the record, rather than being quietly dropped
from the eleven or mapped onto a role that does something else.

---

## The Deviations, in full

### `FLEET_DRIVER` — the one that must not be fudged

**There is no §2.3 user class for a driver.** Every `SRS-SFL-S168fuel-*` requirement is written *"As a
Fleet or Logistics Officer"*, and the mapping's S168_fuel entry reads *"Per-vehicle fuel consumption,
fuel-card reconciliation, anti-fraud controls"* — an operational-owner framing, not driver
self-service.

What exists in code is a role with eight real permissions, verified from the matrices:

```
FLEET_VEHICLE_READ  FLEET_TRIP_READ  FLEET_INSPECTION_RECORD  FLEET_EVIDENCE_REGISTER
FUEL_TRANSACTION_READ  FUEL_LOGBOOK_READ  FUEL_LOGBOOK_CREATE  FUEL_LOGBOOK_SUBMIT
```

`FUEL_LOGBOOK_CREATE` and `_SUBMIT` are the load-bearing pair: somebody has to fill in the logbook for
the anti-fraud control to have an input, and the only somebody who can is the driver. So the portal is
built as **the minimum surface those eight permissions imply** and nothing beyond it. A driver holding
`FLEET_VEHICLE_READ` does not get a dashboard of estate-wide vehicle figures; they get the vehicle
they are driving.

**Owner: Transportation & Logistics Unit**, to confirm whether "Driver" is a Phase 1 user class or
whether logbook capture belongs to the officer with the driver as a data subject rather than a user.

Note that **`S168a Trip / Driver-Booking Portal` is a Phase 2 system** in the mapping. The
driver-facing booking product is already scoped and deferred, which is a positive reason to keep this
pass to logbook-and-assignment self-service and not drift toward trip booking.

### `CENTRE_MANAGER` — a portal that cannot yet keep its promise

No §2.3 class. Added additively under S171 decision D-14 as the destination-receipt persona, which is
a real operational role — somebody at an examination centre signs for the consignment.

**The portal is built, and it cannot say "my centre".** `Dispatch.destinationCentre` and
`assignedHandler` are `VARCHAR(200)` free text supplied at creation, with no relationship to a
principal, so there is nothing to narrow on. A rule built on them would hold whenever somebody
happened to type an actor id and fail silently otherwise — worse than no rule, because it looks like
enforcement. Recorded as **C-16** in `docs/fleet/S166_Gap_And_Conflict_Report.md`.

So the screen is labelled for what it actually shows — consignments at this **site**, not this centre
— and says so on the page. **Owner: Transportation & Logistics Unit**, for the schema decision that
puts a principal-bound centre reference on a dispatch.

### `SERVICE_INTEGRATION` — not a person

A machine principal for signed vendor webhooks. It holds ingest permissions and nothing a human would
use. **No portal, deliberately**, and it is listed here so nobody later reads its absence as an
oversight and builds one.

---

## Notes that change what could be built

**`HSE_MANAGER` is only partially served.** The HSE Officer class in §2.3 describes *"incident reports,
near-miss capture, investigation, corrective actions"* — which is **S163**, and S163 is not built. The
twelve IFIMP permissions this role holds are all reads, so the portal it gets is a read-only posture
over facilities and emergency. That is honest but incomplete, and the incompleteness is S163's, not
this pass's.

**Dispatch controller and logistics coordinator get no new portal.** Both hold the full 11-permission
controller set and the existing dispatch module is already their view. Duplicating fifteen screens to
put a different title on them would add a maintenance burden and no capability. **Recorded as a
finding rather than built** — which is what the prompt asked for, and the right answer.

**A driver's fuel transactions are not "mine".** `FUEL_TRANSACTION_READ` is not narrowed per record —
only logbooks are. The driver portal therefore labels that list as transactions **for the vehicles
they drove**, sourced by vehicle, and does not claim it is personal. Recorded in
`docs/fuel/S168_Fuel_Gap_And_Conflict_Report.md`; the policy question of whether a driver should see a
colleague's fill is for the Transportation & Logistics Unit.

**The four roles absent from `roleSystems` are correct.** `SFL_ADMIN`, `DTI_ADMIN`, `AUDITOR` and
`COMPLIANCE_OFFICER` are handled by `crossProgrammeRoles`, which returns every programme. That is by
design and documented at the declaration; it is not an omission and must not be "fixed".
