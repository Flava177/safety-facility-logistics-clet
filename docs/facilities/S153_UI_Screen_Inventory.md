# S153 CMMS — dashboard screen inventory

Nine screens and eight dialogs in `frontend/sfl-operations-ui/src/modules/facilities`, extending the
S152 module rather than starting a new one — same service, same client, same envelope.

- Route base: `/facilities` · System code: **S153** · Programme: SFL.IFIMP
- Service: `sfl-facilities-service` on 8091, `VITE_FACILITIES_API_BASE_URL`
- Companion: [S153_UI_Gap_Report.md](S153_UI_Gap_Report.md)

## Screens

| # | Screen | Route | Reads | Writes | Permission |
| --- | --- | --- | --- | --- | --- |
| 1 | Fault register | `/facilities/faults` | `GET /faults` | `POST /faults` | `FACILITIES_FAULT_READ` |
| 2 | Fault detail | `/facilities/faults/:faultId` | `GET /faults/{id}` | `PATCH …/triage`, `…/dismissal`, `POST /work-orders/from-fault` | `FACILITIES_FAULT_READ` |
| 3 | Work order queue | `/facilities/work-orders` | `GET /work-orders`, `GET /maintenance/vendors` | — | `FACILITIES_WORK_ORDER_READ` |
| 4 | Work order detail | `/facilities/work-orders/:id` | `GET /work-orders/{id}`, `…/parts`, `…/evidence` | 9 transitions, parts, evidence | `FACILITIES_WORK_ORDER_READ` |
| 5 | Preventive schedules | `/facilities/maintenance/schedules` | `GET /maintenance/schedules` | `POST /maintenance/schedules`, `…/runs` | `FACILITIES_PM_SCHEDULE_READ` |
| 6 | Schedule detail | `/facilities/maintenance/schedules/:id` | `GET …/{id}`, `GET /assets/{id}`, `GET /work-orders` | — | `FACILITIES_PM_SCHEDULE_READ` |
| 7 | Vendors | `/facilities/maintenance/vendors` | `GET /maintenance/vendors` | `POST /maintenance/vendors` | `FACILITIES_VENDOR_READ` |
| 8 | Evidence detail | `/facilities/maintenance-evidence/:id` | `GET /maintenance-evidence/{id}` | `POST …/exports`, `PATCH …/legal-hold` | `FACILITIES_EVIDENCE_READ` |
| 9 | Faults on a space | (section of the S152 space detail) | `GET /faults/rooms/{roomId}` | — | `FACILITIES_FAULT_READ` |

## Dialogs

| Dialog | Used by | The rule it states before the user commits |
| --- | --- | --- |
| `ReportFaultDialog` | 1, 9 | A fault needs a room **or** a location code. Shows what the chosen priority will do to the space |
| `TriageFaultDialog` | 2 | Priority may change here and only here; the SLA is computed from it and then fixed |
| `DismissFaultDialog` | 2 | Rejected / duplicate / cancelled are three different judgements, all terminal, all needing a reason |
| `AssignWorkOrderDialog` | 4 | An expired vendor contract is refused with the service's reason; assigning releases a hold |
| `TransitionNoteDialog` | 4, 8 | Hold, reopen, cancel and legal hold — one dialog, because they differ only in wording |
| `CloseWorkOrderDialog` | 4 | The evidence shortfall as a count; complete and close are the same moment from two chairs |
| `RecordPartDialog` | 4 | Cost is optional — a guessed figure is worse than a blank |
| `AttachEvidenceDialog` | 4 | By reference: no upload control, because this service never holds the file |
| `CreateScheduleDialog` | 5 | Lead time must be shorter than the interval, or the queue fills with duplicates |
| `ExportEvidenceDialog` | 8 | Reason and recipient are both required, and both are audited before anything is returned |

## Navigation

One new section, **Maintenance**, between Facility operations and Estate registers, carrying Faults,
Work orders, Preventive schedules and Vendors — each gated on its real service permission. Its own
section rather than items folded into Facility operations because maintenance has a different
audience: a technician and a contractor live there and never open the estate registers.

## The system code

`SystemCode` gained `'S153'`. Entitlement is currently identical to S152 for every role, because
`FacilitiesPermissionMatrix` puts fault and work-order reads inside its shared `READ_ONLY` set. It is
still a separate code for three reasons, recorded at the declaration: the C9 mapping treats S153 as a
Fast-Track system in its own right and the coverage claims count systems; `VITE_SFL_SYSTEMS=S153`
makes it possible to look at maintenance in isolation; and the no-entitlement page names the system.

## What each role sees

Confirmed in a browser against the running service, not inferred:

| Role | Sidebar | Work order controls |
| --- | --- | --- |
| `IFIMP_MAINTENANCE_SUPERVISOR` | All four maintenance items, plus the estate | Start, Hold, Complete, **Close**, Cancel, Reassign |
| `IFIMP_TECHNICIAN` | Faults, Work orders, Schedules — no Vendors | Start, Hold, Complete, Reassign — **no Close** |
| `VENDOR_TECHNICIAN` | Work orders, Sites, Spaces, Assets only | Only on the orders assigned to them |
| `IFIMP_REQUESTER` | Faults, Sites, Spaces | None; sees only the faults they reported |

## Tests

`npm run test` — **73 tests, 5 files** (was 44). `maintenanceWorkflow.test.ts` adds 29, covering the
closure evidence gate, the transition table, the reopen permission, vendor assignability, the fault
workflow and the priority ordering the configurable thresholds depend on.
