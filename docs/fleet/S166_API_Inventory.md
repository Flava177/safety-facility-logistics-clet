# S166 Fleet and Vehicle Management — API Inventory (contract-first)

Base path `/api/v1/fleet` (integration receivers live under `/api/v1/integrations`).
Every response body is the shared envelope `ApiResponse<T> { data, error }`; every failure carries
`ApiError { code, message, correlationId, timestamp }` where `message` reproduces the SRS *Error States* wording
verbatim, plus `fieldErrors[]` for Bean Validation failures.

**Conventions applied to every operation**

| Concern | Rule |
|---|---|
| Actor | `X-SFL-User`, `X-SFL-Display-Name`, `X-SFL-Roles`, `X-SFL-Sites` in dev; OIDC/JWT claims in production (same `ActorContext`) |
| Correlation | `X-Correlation-ID` request header, echoed on the response and propagated into audit + outbox |
| Idempotency | `Idempotency-Key` header **required** on state-creating POSTs; replay returns the original result |
| Timestamps | ISO-8601 with explicit offset; stored and compared in UTC; Ghana operating context is `Africa/Accra` (UTC+0, no DST) for business-day/SLA calendars |
| Collections | `page`, `size` (max 200), `sort` with a stable tiebreak on `createdAt desc, id desc`; response carries `page`, `size`, `totalElements`, `totalPages` |
| Statuses | `200` read/update · `201` synchronous create (+`Location`) · `202` accepted async · `204` no content · `400` malformed · `401` unauthenticated/bad signature · `403` authorisation · `404` not found · `409` conflict/state/version · `422` business validation · `503` integration unavailable |
| Persistence entities | never exposed; `api/mapper` converts domain → response DTO |

---

## 1. Vehicles — `SRS-SFL-S166-01`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| V1 | `POST /api/v1/fleet/vehicles` | Register a vehicle | `201` | 400, 403, 409 `FLEET_DUPLICATE_IDENTIFIER`, 422 `FLEET_MISSING_SITE_SCOPE` |
| V2 | `GET /api/v1/fleet/vehicles` | Search register (filters: `siteCode`, `status`, `serviceStatus`, `availability`, `readiness`, `category`, `responsibleUnit`, `registrationNumber`, `complianceExpiringBefore`) | `200` | 403 |
| V3 | `GET /api/v1/fleet/vehicles/{vehicleId}` | Vehicle detail (sensitive fields masked without permission) | `200` | 403, 404 |
| V4 | `PATCH /api/v1/fleet/vehicles/{vehicleId}` | Update mutable attributes (`If-Match`/`version` required) | `200` | 403, 404, 409 `FLEET_RECORD_VERSION_CONFLICT`, 422 |
| V5 | `PATCH /api/v1/fleet/vehicles/{vehicleId}/lifecycle` | Lifecycle transition (activate/deactivate/suspend/archive/restore) | `200` | 403, 404, 409 `FLEET_INVALID_STATE_TRANSITION` |
| V6 | `GET /api/v1/fleet/vehicles/{vehicleId}/readiness` | Readiness assessment, optionally for a period + driver (`from`, `to`, `driverId`, `operatingMode`) | `200` | 403, 404 |
| V7 | `POST /api/v1/fleet/vehicles/{vehicleId}/compliance-documents` | Register a compliance document | `201` | 403, 404, 409, 422 |
| V8 | `GET /api/v1/fleet/vehicles/{vehicleId}/compliance-documents` | List compliance documents | `200` | 403, 404 |
| V9 | `POST /api/v1/fleet/vehicles/{vehicleId}/service-records` | Record a service event | `201` | 403, 404, 422 `FLEET_ODOMETER_REGRESSION` |
| V10 | `GET /api/v1/fleet/vehicles/{vehicleId}/service-history` | Service history + current service status | `200` | 403, 404 |
| V11 | `POST /api/v1/fleet/vehicles/{vehicleId}/odometer-corrections` | Authorised odometer correction (reason + evidence mandatory) | `200` | 403, 404, 422 |
| V12 | `GET /api/v1/fleet/vehicles/{vehicleId}/movement` | Telematics movement history + freshness age *(C-10)* | `200` | 403, 404 |

## 2. Drivers — `SRS-SFL-S166-01`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| D1 | `POST /api/v1/fleet/drivers` | Register a driver profile reference (HRMS-backed) | `201` | 400, 403, 409 `FLEET_DUPLICATE_IDENTIFIER`, 422 |
| D2 | `GET /api/v1/fleet/drivers` | Search drivers (`siteCode`, `status`, `eligibility`, `licenceExpiringBefore`, `responsibleUnit`) | `200` | 403 |
| D3 | `GET /api/v1/fleet/drivers/{driverId}` | Driver detail (licence number masked without permission) | `200` | 403, 404 |
| D4 | `PATCH /api/v1/fleet/drivers/{driverId}` | Update driver reference / licence / lifecycle | `200` | 403, 404, 409, 422 |
| D5 | `GET /api/v1/fleet/drivers/{driverId}/eligibility` | Eligibility assessment with blocker codes (`vehicleCategory`, `from`, `to` optional) | `200` | 403, 404 |

## 3. Trips and assignments — `SRS-SFL-S166-02`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| T1 | `POST /api/v1/fleet/trips` | Create a trip (optionally with vehicle+driver → assigned) | `201` | 400, 403, 409 `FLEET_ASSIGNMENT_CONFLICT`, 422 `FLEET_READINESS_BLOCKED`/`FLEET_DRIVER_INELIGIBLE` |
| T2 | `GET /api/v1/fleet/trips` | Trip queue (`siteCode`, `status`, `vehicleId`, `driverId`, `from`, `to`, `operatingMode`) | `200` | 403 |
| T3 | `GET /api/v1/fleet/trips/{tripId}` | Trip detail incl. inspections and closure evidence | `200` | 403, 404 |
| T4 | `PATCH /api/v1/fleet/trips/{tripId}/assignment` | Assign or reassign vehicle/driver | `200` | 403, 404, 409, 422 |
| T5 | `POST /api/v1/fleet/trips/{tripId}/inspections` | Record pre-/post-trip inspection *(C-01: traced to S166-01+02)* | `201` | 403, 404, 409, 422 |
| T6 | `PATCH /api/v1/fleet/trips/{tripId}/hold` | Hold or resume (`action: HOLD\|RESUME`, reason) | `200` | 403, 404, 409 |
| T7 | `PATCH /api/v1/fleet/trips/{tripId}/cancel` | Cancel with reason | `200` | 403, 404, 409 |
| T8 | `PATCH /api/v1/fleet/trips/{tripId}/closure` | Close with end odometer, reason and evidence | `200` | 403, 404, 409, 422 `FLEET_CLOSURE_EVIDENCE_MISSING`/`FLEET_ODOMETER_REGRESSION` |
| T9 | `PATCH /api/v1/fleet/trips/{tripId}/start` | Start an assigned trip (pre-trip inspection gate) | `200` | 403, 404, 409, 422 |

## 4. Fleet workflow — `SRS-SFL-S166-02`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| W1 | `POST /api/v1/fleet/workflow-items` | Raise a workflow item | `201` | 400, 403, 422 |
| W2 | `GET /api/v1/fleet/workflow-items` | Queue (`siteCode`, `status`, `priority`, `severity`, `assignee`, `type`, `operatingMode`, `overdueOnly`, `escalatedOnly`) | `200` | 403 |
| W3 | `GET /api/v1/fleet/workflow-items/{itemId}` | Detail incl. SLA target and escalation level | `200` | 403, 404 |
| W4 | `PATCH /api/v1/fleet/workflow-items/{itemId}/assignment` | Assign / reassign | `200` | 403, 404, 409 |
| W5 | `PATCH /api/v1/fleet/workflow-items/{itemId}/escalation` | Manual escalation (privileged) | `200` | 403 `FLEET_UNAUTHORIZED_APPROVAL`, 404, 409 |
| W6 | `PATCH /api/v1/fleet/workflow-items/{itemId}/hold` | Hold / resume | `200` | 403, 404, 409 |
| W7 | `PATCH /api/v1/fleet/workflow-items/{itemId}/cancel` | Cancel (privileged, reason required) | `200` | 403, 404, 409 |
| W8 | `PATCH /api/v1/fleet/workflow-items/{itemId}/closure` | Close with reason + evidence | `200` | 403, 404, 409, 422 `FLEET_CLOSURE_EVIDENCE_MISSING` |
| W9 | `PATCH /api/v1/fleet/workflow-items/{itemId}/reopen` | Reopen (privileged, reason required) | `200` | 403, 404, 409 |
| W10 | `POST /api/v1/fleet/workflow-items/{itemId}/comments` | Add an immutable comment | `201` | 403, 404 |
| W11 | `GET /api/v1/fleet/workflow-items/{itemId}/transitions` | Append-only transition + comment history | `200` | 403, 404 |

## 5. Evidence and audit — `SRS-SFL-S166-03`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| E1 | `POST /api/v1/fleet/evidence` | Register an evidence reference (file reference + hash + retention class) | `201` | 400, 403, 422 `FLEET_RETENTION_CLASS_MISSING` |
| E2 | `GET /api/v1/fleet/evidence` | Search evidence (`siteCode`, `relatedWorkflowId`, `evidenceType`, `retentionClass`, `legalHold`) | `200` | 403 |
| E3 | `GET /api/v1/fleet/evidence/{evidenceId}` | Evidence metadata + access history (records an access entry) | `200` | 403, 404 |
| E4 | `POST /api/v1/fleet/evidence/{evidenceId}/export-requests` | Request an export with justification + recipient | `202` | 400, 403, 404, 422 |
| E5 | `PATCH /api/v1/fleet/evidence/export-requests/{requestId}/approval` | Approve or reject (approver ≠ requester) | `200` | 403 `FLEET_UNAUTHORIZED_APPROVAL`, 404, 409 |
| E6 | `GET /api/v1/fleet/evidence/export-requests/{requestId}/download` | Retrieve the approved export descriptor | `200` | 403 `FLEET_EXPORT_NOT_APPROVED`, 404 |
| A1 | `GET /api/v1/fleet/audit` | Audit search (`siteCode`, `resourceType`, `resourceId`, `actorId`, `action`, `from`, `to`) — auditor roles only | `200` | 403 |
| A2 | `GET /api/v1/fleet/audit/integrity-check` | Replay the hash chain and report the first divergence | `200` | 403, 409 `FLEET_AUDIT_CHAIN_FAILURE` |

## 6. Integrations — `SRS-SFL-S166-04`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| I1 | `POST /api/v1/integrations/webhooks/telematics` | Signed telematics ingestion (HMAC + allowlist + schema + inbox + idempotency) | `202` accepted · `200` duplicate ignored | 401 `FLEET_INTEGRATION_INVALID_SIGNATURE`, 403 `FLEET_INTEGRATION_SOURCE_NOT_ALLOWED`, 422 `FLEET_INTEGRATION_SCHEMA_INVALID` |
| I2 | `GET /api/v1/fleet/integrations/health` | Integration health projection (per source: status, backlog, failures, dead letters, last success) | `200` | 403 |
| I3 | `GET /api/v1/fleet/integrations/messages` | Inbox message search (`source`, `status`, `from`, `to`) | `200` | 403 |
| I4 | `POST /api/v1/fleet/integrations/messages/{messageId}/replay` | Replay a dead-lettered message (privileged, idempotent) | `202` | 403, 404, 409 |

## 7. Dashboards and reports — `SRS-SFL-S166-05`

| # | Method & path | Purpose | Success | Failure codes |
|---|---|---|---|---|
| B1 | `GET /api/v1/fleet/dashboard` | Indicators with `snapshotGeneratedAt`, `stale` flag and warning message. Filters: `siteCode`, `from`, `to`, `status`, `priority`, `responsibleUnit`, `operatingMode`, `reconcile` | `200` (+ stale warning) | 403 `FLEET_DASHBOARD_NO_SCOPE` |
| B2 | `GET /api/v1/fleet/dashboard/drilldown/{metricCode}` | Source records behind a metric | `200` | 403 `FLEET_DASHBOARD_RESTRICTED_DRILLDOWN`, 404 |
| B3 | `GET /api/v1/fleet/dashboard/readiness-report` | Operational / go-live readiness report | `200` | 403 |
| B4 | `GET /api/v1/fleet/dashboard/compliance-report` | Compliance + service exposure report (data-minimised, access audited) | `200` | 403 |

## 8. System

| # | Method & path | Purpose |
|---|---|---|
| S1 | `GET /api/v1/system/info` | Service identity (existing) |
| S2 | `GET /actuator/health`, `/info`, `/metrics`, `/prometheus` | Operations |

---

## 9. Endpoints evaluated and **not** implemented

| Endpoint | Decision |
|---|---|
| `POST /api/v1/fleet/emergency-logistics` | Deferred — not derivable from `SRS-SFL-S166-01…05`; workplan mapping is a category error. Operating mode `EMERGENCY` on trips and workflow items delivers the in-scope behaviour. See conflict **C-12**. |
| `DELETE` on any operational, audit, evidence or assignment resource | Prohibited by the SRS (no hard deletion of history). Lifecycle transitions replace deletion. |
