# S176 Construction Project Management - API reference

Base path `/api/v1/facilities/construction`. Every response is the platform envelope `{data, error}`.
`X-Correlation-ID` is honoured and echoed. `Idempotency-Key` is honoured only on `POST /projects`,
the one state-**creating** operation with no separate identifying key of its own; every other write is
a PATCH/POST guarded by the record's version and its state machine, or is naturally idempotent
(assigning the same contractor twice, linking the same permit twice returns the existing record).

Actor headers in development (`SFL_SECURITY_ENABLED=false`): `X-SFL-User`, `X-SFL-Roles`,
`X-SFL-Sites`, `X-SFL-Source-Channel`.

---

## Projects - `/projects`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/projects` | `FACILITIES_PROJECT_MANAGE` | Registers a project directly. Accepts `Idempotency-Key`. |
| `PATCH` | `/projects/{id}/registration` | `FACILITIES_PROJECT_MANAGE`, project's own manager | Completes registration of a PROPOSED (S158 hand-off) project. |
| `PATCH` | `/projects/{id}/approval` | `FACILITIES_PROJECT_APPROVE` | The accountable sign-off. Refused if the approver is the project's own manager. |
| `PATCH` | `/projects/{id}/start` | `FACILITIES_PROJECT_MANAGE`, project's own manager | The S176-01 gate: sign-off, permits, a responsible contractor. |
| `PATCH` | `/projects/{id}/cancellation` | `FACILITIES_PROJECT_MANAGE`, project's own manager | Reason required. Refused once handed over. |
| `PATCH` | `/projects/{id}/baseline` | `FACILITIES_PROJECT_MANAGE`, project's own manager | Versioned revision, pre-start only. Returns an APPROVED project to REGISTERED. |
| `POST` | `/projects/{id}/milestones` | `FACILITIES_PROJECT_MANAGE`, project's own manager | |
| `PATCH` | `/projects/{id}/milestones/{milestoneId}` | `FACILITIES_PROJECT_MANAGE`, project's own manager | Versioned revision of the target date. |
| `PATCH` | `/projects/{id}/milestones/{milestoneId}/achievement` | `FACILITIES_PROJECT_MANAGE`, project's own manager | |
| `POST` | `/projects/{id}/contractors` | `FACILITIES_PROJECT_MANAGE`, project's own manager | Assigns a responsible contractor; idempotent per contractor. |
| `POST` | `/projects/{id}/permits` | `FACILITIES_PROJECT_MANAGE`, project's own manager | Links an S164 permit by id; recorded whatever the projection currently shows. |
| `GET` | `/projects` | `FACILITIES_PROJECT_READ` | Filter `siteCode`, `status`. Pipeline by stage. |
| `GET` | `/projects/{id}` | `FACILITIES_PROJECT_READ` | |
| `GET` | `/projects/{id}/approvals` | `FACILITIES_PROJECT_READ` | |
| `GET` | `/projects/{id}/milestones` | `FACILITIES_PROJECT_READ` | |
| `GET` | `/projects/{id}/contractors` | `FACILITIES_PROJECT_READ` | |
| `GET` | `/projects/{id}/permits` | `FACILITIES_PROJECT_READ` | Each link with the S164 projection's current view and whether it satisfies the gate. |
| `GET` | `/projects/{id}/permits/missing` | `FACILITIES_PROJECT_READ` | Permit-requiring work types with no current linked permit. |
| `GET` | `/projects/{id}/history` | `FACILITIES_PROJECT_READ` | Every version of the baseline and every milestone target, oldest first (SRS-SFL-S176-01). |
| `GET` | `/projects/{id}/budget` | `FACILITIES_PROJECT_READ` | Baseline + approved variations, broken down by variation record (SRS-SFL-S176-03). |

### `POST /projects`

```json
{
  "siteCode": "MAIN",
  "title": "New library wing",
  "scope": "Extension to the library block",
  "workTypes": ["FIT_OUT"],
  "budgetBaseline": 500000.00,
  "currency": "GHS",
  "fundingSourceReference": "FUND-CAPEX-2026-014",
  "fundingSourceName": "Capital Budget 2026",
  "milestones": [
    {"code": "PRACTICAL_COMPLETION", "name": "Practical completion", "targetDate": "2026-12-01"}
  ],
  "contractors": [{"contractorId": "...", "role": "MAIN_CONTRACTOR"}],
  "projectManagerId": null
}
```

`workTypes` must be drawn from the site's configured catalogue (`construction.work-types`); an unknown
value is refused at registration - see the runbook. `currency` is a three-letter code.

### `PATCH /projects/{id}/start` - the S176-01 gate, error states

| Refused as | Meaning |
|---|---|
| `PROJECT_APPROVAL_MISSING` | No sign-off covers the project's current baseline revision. |
| `PROJECT_PERMIT_MISSING` | A configured permit-requiring work type has no current linked S164 permit. Names the work type(s). |
| `PROJECT_CONTRACTOR_UNASSIGNED` | No contractor is recorded responsible for the project. |

---

## Variations - `/projects/{projectId}/variations`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `` | `FACILITIES_PROJECT_MANAGE`, project's own manager | From APPROVED to PRACTICAL_COMPLETION only. Held for escalation immediately if it crosses the threshold or one is already held. |
| `PATCH` | `/{variationId}/decision` | `FACILITIES_VARIATION_APPROVE` | Approve or reject. Refused `VARIATION_ESCALATION_REQUIRED` if escalation is needed. Submitter cannot decide. |
| `PATCH` | `/{variationId}/escalated-approval` | `FACILITIES_VARIATION_ESCALATED_APPROVE` | Refused if an earlier held variation on the project has not yet been decided. |
| `GET` | `` | `FACILITIES_PROJECT_READ` | |

`GET /projects/{id}/budget` (listed under Projects) is the traceable current-budget view for the
project's variations.

---

## Contractors - `/contractors`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `` | `FACILITIES_CONTRACTOR_MANAGE` | Insurance expiry required. |
| `PATCH` | `/{id}/insurance` | `FACILITIES_CONTRACTOR_MANAGE` | An expiry already past suspends every active site-access grant, in the same request. |
| `POST` | `/{id}/competencies` | `FACILITIES_CONTRACTOR_MANAGE` | Records or renews a certification by code. |
| `POST` | `/{id}/site-access` | `FACILITIES_CONTRACTOR_MANAGE` | Refused `CONTRACTOR_COMPLIANCE_LAPSED` (reason named) if insurance or a competency has lapsed. Recorded and published for S160a - not enforced. |
| `GET` | `` | `FACILITIES_PROJECT_READ` | Filter `siteCode`. Each contractor's compliance today. |
| `GET` | `/{id}` | `FACILITIES_PROJECT_READ` | Competencies, current S164 permits, compliance, access grants. |
| `GET` | `/{id}/site-access` | `FACILITIES_PROJECT_READ` | Every grant, including suspended and refused ones. |

---

## Handover and defects - `/projects/{projectId}`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `PATCH` | `/practical-completion` | `FACILITIES_PROJECT_MANAGE`, project's own manager | |
| `POST` | `/handover` | `FACILITIES_PROJECT_HANDOVER` | Applies the listed S152 register changes in the same transaction. Refused `PROJECT_HANDOVER_INCOMPLETE` (flagged) if no change is listed or a declared space is missed. |
| `PATCH` | `/handover/scenario-confirmation` | `FACILITIES_PROJECT_HANDOVER` | Retries an UNRESOLVED S158 scenario confirmation. |
| `POST` | `/defects` | `FACILITIES_PROJECT_HANDOVER` (or own project manager) | Only during the liability period, only against an assigned contractor. Raises an S153 `CONSTRUCTION_DEFECT` work order. |
| `PATCH` | `/defects/{defectId}/deferral` | `FACILITIES_PROJECT_CLOSE` | Reason required. |
| `PATCH` | `/closure` | `FACILITIES_PROJECT_CLOSE` | Refused `PROJECT_DEFECTS_OPEN` (items named) until every defects-liability item is closed or deferred, and the liability period has ended. |
| `GET` | `/handovers` | `FACILITIES_PROJECT_READ` | Every handover attempt, including flagged-incomplete ones. |
| `GET` | `/defects` | `FACILITIES_PROJECT_READ` | |

### `POST /projects/{id}/handover`

```json
{
  "handoverDate": "2026-12-05",
  "notes": "New wing ready for occupation",
  "roomChanges": [
    {"action": "CREATE", "floorId": "...", "roomCode": "LIB-201", "name": "Reading Room",
     "spaceType": "LIBRARY", "capacity": 80, "bookable": true, "examinationCapable": false}
  ]
}
```

`action` is `CREATE` (needs `floorId`, `roomCode`, `name`, `spaceType`) or `UPDATE` (needs `roomId`;
every other field left out means unchanged). Every listed room must be at the project's own site.

---

## Dashboard - `/dashboard`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `` | `FACILITIES_PROJECT_READ` | Filter `siteCode`. Pipeline by stage, milestone/variation exceptions, contractor compliance, snagging backlog, handover completeness. |
| `GET` | `/integrations` | none (read-only status) | What S176 is and is not connected to - see the gap report. Never reports a dependency as integrated without evidence (SRS CORR-07). |
