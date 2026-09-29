# S173 Event Logistics & Set-Up Workflow - API reference

Base path `/api/v1/facilities/event-logistics`. Every response is the platform envelope
`{data, error}`. `X-Correlation-ID` is honoured and echoed.

Actor headers in development (`SFL_SECURITY_ENABLED=false`): `X-SFL-User`, `X-SFL-Roles`,
`X-SFL-Sites`, `X-SFL-Source-Channel`.

---

## Hand-off intake - `/handoffs`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/handoffs` | `FACILITIES_EVENT_HANDOFF_INGEST` | Vendor-inbound. Signed by CCP Events (S078) over the raw body. |

Not an ordinary write: it is authenticated the same way as every Phase 2 vendor message
(`VendorMessageVerifier`, `VendorChannel.CCP_EVENTS`, NFR-SEC2). Headers:

| Header | Meaning |
|---|---|
| `X-SFL-Source` | The registered source id. `CCP-EVENTS-SIM` in development. |
| `X-SFL-Signature` | HMAC-SHA256 over `signedAt + "." + rawBody`, hex-encoded. |
| `X-SFL-Signed-At` | ISO instant the sender signed at. Must fall inside the signature window (5 minutes by default). |

Body (the envelope the verifier reads, plus this module's own required fields):

```json
{
  "messageType": "event.handoff",
  "idempotencyKey": "S078-1044-v3",
  "siteCode": "MAIN",
  "s078EventReference": "S078-1044",
  "status": "CONFIRMED",
  "title": "Founders' Day",
  "startsAt": "2026-11-12T08:00:00Z",
  "endsAt": "2026-11-12T18:00:00Z",
  "roomCode": "HALL-A",
  "expectedAttendance": 600,
  "eventCategory": "GRADUATION",
  "statedRequirements": "Stage, PA system, extra security, temporary marquee",
  "externalContractors": true,
  "temporaryStructures": true
}
```

- `s078EventReference` and `status` are required for every message; the confirmation fields
  (`title`, `startsAt`, `endsAt`, `roomCode`, `expectedAttendance`, `eventCategory`) are required only
  when `status` is `CONFIRMED` or another live value - a `CANCELLED` hand-off needs only the reference
  and status.
- `status` is one of `DRAFT`, `TENTATIVE`, `CONFIRMED`, `CANCELLED`. Only `CONFIRMED` creates or
  updates a set-up task; only `CANCELLED` (for a reference S173 already holds) cancels one. Anything
  else is refused - `EVENT_REFERENCE_UNRESOLVABLE` - and nothing is created (SRS-SFL-S173-01).
- A repeat of the same `sourceId` + `idempotencyKey` is a duplicate: answered as the first delivery
  was, nothing re-actioned. `200` for a duplicate, `201` for a new outcome.
- A forged signature, unknown source, wrong channel, stale timestamp or missing required field is
  rejected before this module ever sees it, logged, audited (`VENDOR_MESSAGE_REJECTED`), sent to the
  SIEM and published (`sfl.integration.vendor-message-rejected.v1`) - see `VendorMessageVerifier`.

Response:

```json
{ "data": { "handoffId": "...", "s078EventReference": "S078-1044", "outcome": "ACCEPTED",
            "action": "CREATED", "setupTaskId": "...", "duplicate": false } }
```

---

## Set-up tasks - `/setup-tasks`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/setup-tasks/{id}` | `FACILITIES_EVENT_READ` | |
| `GET` | `/setup-tasks/{id}/readiness` | `FACILITIES_EVENT_READ` | The consolidated view - S173-02. |
| `GET` | `/setup-tasks?siteCode=&from=&to=` | `FACILITIES_EVENT_READ` | Upcoming events by readiness, worst first. Dashboard source. |
| `GET` | `/setup-tasks/{id}/handoffs` | `FACILITIES_EVENT_READ` | The hand-off register for this event, accepted and rejected. |
| `POST` | `/setup-tasks/{id}/resource-requests` | `FACILITIES_EVENT_COORDINATE` | Decompose into typed requests; each is routed immediately. |
| `PATCH` | `/resource-requests/{id}/route` | `FACILITIES_EVENT_COORDINATE` | Send a requested/conflicted/manual line to its owning system again. |
| `PATCH` | `/resource-requests/{id}/manual-coordination/accept` | `FACILITIES_EVENT_COORDINATE` | A named person takes responsibility for a manual item. |
| `PATCH` | `/resource-requests/{id}/cancel` | `FACILITIES_EVENT_COORDINATE` | Releases whatever the request held elsewhere. |
| `PUT` | `/setup-tasks/{id}/risk-assessment` | `FACILITIES_EVENT_COORDINATE` | Link an S165 assessment - S173-03. |
| `PATCH` | `/setup-tasks/{id}/confirm` | `FACILITIES_EVENT_COORDINATE` | Refused for a higher-risk event with no current linked assessment. |
| `PATCH` | `/setup-tasks/{id}/complete` | `FACILITIES_EVENT_COORDINATE` | Refused with an unresolved, unescalated request. |
| `POST` | `/setup-tasks/{id}/reconciliation` | `FACILITIES_EVENT_COORDINATE` | Post-event, per-line delivery outcome. |
| `GET` | `/setup-tasks/{id}/reconciliation` | `FACILITIES_EVENT_READ` | |

### `POST /setup-tasks/{id}/resource-requests`

```json
{
  "requests": [
    { "resourceType": "VENUE", "description": "Main hall", "quantity": 1 },
    { "resourceType": "AV", "description": "Projector and PA", "quantity": 1,
      "bookableResourceId": "98fbf9c5-62d0-45e0-965e-464d380fed0a" },
    { "resourceType": "SECURITY", "description": "Two extra posts", "quantity": 2,
      "bookableResourceId": "..." },
    { "resourceType": "PRE_EVENT_MAINTENANCE", "description": "Fix the stage lighting" },
    { "resourceType": "CLEANING", "description": "Post-event deep clean" },
    { "resourceType": "CATERING", "description": "Reception for 200 guests" }
  ],
  "applyTemplate": true
}
```

`resourceType` is one of `VENUE`, `AV`, `STAGING`, `SECURITY`, `SIGNAGE`, `PRE_EVENT_MAINTENANCE`,
`CLEANING`, `CATERING` - structured, never free text (SRS-SFL-S173-01). `AV`/`STAGING`/`SECURITY`/
`SIGNAGE` must name the S159 `bookableResourceId`; there is exactly one `VENUE` line per task, and it
is what every other S159 line is allocated onto. `applyTemplate: true` also raises this event
category's persistent template lines (S173-04 feedback) for any resource type not already listed.

Response `201` with each request's routed outcome:

```json
{ "data": [
  { "id": "...", "resourceType": "VENUE", "owningSystem": "S159", "status": "REQUESTED",
    "externalReference": "a1b2...", "unresolved": true },
  { "id": "...", "resourceType": "CATERING", "owningSystem": "S172", "status": "MANUAL_COORDINATION",
    "statusDetail": "S172 Catering & Cafeteria Management is Phase 3 and not built...",
    "unresolved": true }
] }
```

### `GET /setup-tasks/{id}/readiness`

The consolidated event-readiness view (S173-02): one `readiness` status
(`UNRESOURCED`/`CONFLICTED`/`AWAITING`/`READY`/`COMPLETED`/`CANCELLED`), a `summary` of counts, every
request, the conflicted subset naming what holds the competing commitment, the manual-coordination
subset, every escalation raised, and - for a higher-risk event - which triggers applied and whether
the linked assessment is current.

### `PUT /setup-tasks/{id}/risk-assessment`

```json
{ "assessmentId": "RA-4821", "version": null }
```

`version: null` links the latest published version S173's projection knows of. Refused
(`EVENT_RISK_ASSESSMENT_NOT_CURRENT`) if S173 holds no such record, or the record is not current -
S165 publishes nothing yet, so this refuses every assessment id until S165 ships (see the gap report).

### `POST /setup-tasks/{id}/reconciliation`

```json
{ "lines": [
  { "resourceRequestId": "...", "outcome": "DELIVERED", "deliveredQuantity": 1, "notes": "On time" },
  { "resourceRequestId": "...", "outcome": "NOT_DELIVERED", "deliveredQuantity": 0,
    "notes": "Projector never arrived; second AV request needed" }
] }
```

`outcome` is `DELIVERED`, `PARTIAL` or `NOT_DELIVERED`. A `PARTIAL`/`NOT_DELIVERED` line feeds the
event category's template (S173-04); once a resource type has gapped at the configured threshold
(`event-logistics.template.gap-threshold`, default 2) that line pre-populates the next decomposition
of the same category when `applyTemplate: true`.

---

## Templates, risk criteria and integration position

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/templates?siteCode=&eventCategory=` | `FACILITIES_EVENT_READ` | Lessons fed back from reconciliation gaps. |
| `GET` | `/risk-criteria?siteCode=` | `FACILITIES_EVENT_READ` | The higher-risk criteria in force. |
| `PUT` | `/risk-criteria?siteCode=` | `FACILITIES_EVENT_RISK_CATEGORY_MANAGE` | Configure attendance threshold, contractor/structure flags, category list. |
| `GET` | `/integration?siteCode=` | `FACILITIES_EVENT_READ` | S078's position, owning-system availability, S165's gap - stated plainly. |

### `PUT /risk-criteria`

```json
{ "attendanceThreshold": 300, "externalContractorsAreHigherRisk": true,
  "temporaryStructuresAreHigherRisk": true, "higherRiskCategories": ["GRADUATION", "CONCERT"] }
```

Every field is optional; an absent one leaves the current value in force. `siteCode` omitted
configures the platform default and requires cross-site (`*`) scope in addition to
`FACILITIES_EVENT_RISK_CATEGORY_MANAGE`; a named site configures that site's override and needs only
that site's scope. SRS-SFL-S173-03's HSE Officer user story: a coordinator does not hold this
permission and cannot exempt their own event.

---

## Error codes this module adds

| Code | HTTP | When |
|---|---|---|
| `EVENT_REFERENCE_UNRESOLVABLE` | 422 | A hand-off for an event S078 does not confirm, or a reference S173 has never seen. |
| `EVENT_RISK_ASSESSMENT_NOT_CURRENT` | 422 | Linking or confirming against an assessment that is not published, current or independently signed off - or none is linked. |
| `EVENT_OWNING_SYSTEM_UNAVAILABLE` | 400 (unmapped; see the gap report) | Reserved for an explicit unavailable-system refusal; in practice unavailability routes to `MANUAL_COORDINATION` rather than refusing, so this code is not currently thrown. |
| `EVENT_UNRESOLVED_RESOURCE_REQUEST` | 422 | Completing a set-up task with a requested-status line that was never escalated. |

`VENDOR_MESSAGE_REJECTED` (401) covers every hand-off authentication failure - see
`docs/facilities/S173_Event_Contracts.md`.
