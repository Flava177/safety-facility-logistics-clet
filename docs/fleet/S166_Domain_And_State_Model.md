# S166 Fleet and Vehicle Management — Domain and State Model

Designed before persistence. The domain layer imports no Spring, JPA, Jackson, HTTP, RabbitMQ, Redis,
PostgreSQL or vendor type — enforced by `FleetArchitectureTest`.

Package: `gh.edu.clet.sfl.fleetlogistics.fleet.domain`

---

## 1. Aggregates and value objects

| Aggregate root | Identity | Owns | Site-scoped |
|---|---|---|---|
| `Vehicle` | `VehicleId` (UUID) | registration, VIN, specification, lifecycle/service/availability status, odometer reading with provenance | yes |
| `DriverProfileReference` | `DriverId` (UUID) | HRMS staff reference, licence, medical clearance, eligibility status, lifecycle | yes |
| `ComplianceDocument` | `ComplianceDocumentId` | vehicle link, document type, validity window, status, evidence reference | yes (via vehicle) |
| `VehicleServiceRecord` | `ServiceRecordId` | service type/date/odometer, next-due date & odometer, outcome, provider reference | yes (via vehicle) |
| `VehicleInspection` | `InspectionId` | inspection type, checklist findings, defect severity, result, odometer, evidence | yes (via vehicle) |
| `Trip` (vehicle/driver assignment) | `TripId` | vehicle + driver + period + purpose + route, status, hold/cancel/closure data | yes |
| `FleetWorkflowItem` | `WorkflowItemId` | queue item: type, priority, severity, operating mode, assignee, SLA, escalation level, closure | yes |
| `WorkflowTransition` | `TransitionId` | append-only transition/comment history (child of workflow item) | yes |
| `EvidenceReference` | `EvidenceId` | file reference + hash + uploader + retention class + legal hold + access history | yes |
| `EvidenceExportRequest` | `ExportRequestId` | justification, recipient, approval decision, export record | yes |
| `AuditEvent` | `AuditId` + monotonic `sequenceNo` | append-only hash-chained audit entry | yes |
| `IntegrationInboxMessage` | `InboxMessageId` | source, idempotency key, raw payload, schema version, processing outcome | yes (payload-derived) |
| `OutboxMessage` | `OutboxId` | event envelope + delivery state, attempts, next retry, dead-letter reason | yes |
| `FleetDashboardSnapshot` | `SnapshotId` | metric code, value, period, filters, generated timestamp, source references | yes |
| `VehicleTelemetryPosition` | `PositionId` | vehicle, position, recorded/received times, provider message reference | yes |

### Value objects

`SiteCode` · `ResponsibleUnit` · `RegistrationNumber` · `VehicleIdentificationNumber` · `VehicleSpecification`
(make/model/year/category/capacity) · `OdometerReading` (`value`, `unit`, `source`, `recordedAt`) ·
`DateTimeRange` (assignment period, half-open `[start, end)`) · `LicenceDetails` · `RecordMetadata`
(`createdBy/At`, `lastModifiedBy/At`, `version`, `sourceChannel`, `auditCorrelationId`) · `EvidenceHash` ·
`RetentionClass` · `ReadinessAssessment` · `ReadinessBlocker` (`code`, `message`, `severity`) ·
`EligibilityAssessment` · `SlaTarget` · `OperatingMode`.

`RecordMetadata` is present on every operational aggregate and supplies the `SRS-SFL-S166-01`
"system-managed fields" set: UUID identity, site scope, created/modified actor + timestamp, optimistic-lock
version, source channel and audit correlation ID.

---

## 2. State machines

Every transition below is encoded in a `domain/policy/*TransitionPolicy` class with an explicit allowed-set, and
every allowed **and prohibited** transition has a unit test. Illegal transitions raise
`InvalidStateTransitionException` (HTTP 409).

### 2.1 Vehicle lifecycle — `VehicleLifecycleStatus`

```
                 ┌──────────── reinstate ────────────┐
                 v                                   │
  (register) → ACTIVE ⇄ INACTIVE                  SUSPENDED
                 │  ^     │                          ^
                 │  └─────┘ activate                 │ suspend (from ACTIVE or INACTIVE)
                 │                                   │
                 └────────────── archive ──────────► ARCHIVED ──(restore, privileged)──► INACTIVE
```

| From | To | Guard |
|---|---|---|
| — | `ACTIVE` | registration succeeds |
| `ACTIVE` | `INACTIVE`, `SUSPENDED`, `ARCHIVED` | no in-progress trip for `ARCHIVED`/`SUSPENDED` |
| `INACTIVE` | `ACTIVE`, `SUSPENDED`, `ARCHIVED` | — |
| `SUSPENDED` | `ACTIVE`, `INACTIVE`, `ARCHIVED` | requires `FLEET_VEHICLE_LIFECYCLE_OVERRIDE` |
| `ARCHIVED` | `INACTIVE` | **only** via authorised restoration workflow (`FLEET_VEHICLE_RESTORE`) |
| `ARCHIVED` | any edit | **prohibited** — archived records are read-only |

### 2.2 Vehicle availability — `VehicleAvailabilityStatus`

`AVAILABLE → ASSIGNED → IN_USE → AVAILABLE` · `AVAILABLE|ASSIGNED → RESERVED` ·
any → `UNAVAILABLE` (lifecycle/service/compliance driven) · `UNAVAILABLE → AVAILABLE` only when no blocker remains.
Availability is **derived** from lifecycle + service status + active trip; it is never set directly by a client.

### 2.3 Vehicle service status — `VehicleServiceStatus`

`IN_SERVICE → DUE → OVERDUE → OUT_OF_SERVICE → IN_SERVICE`.
`DUE`/`OVERDUE` are computed from the latest `VehicleServiceRecord` next-due date **or** next-due odometer,
whichever triggers first. `OUT_OF_SERVICE` is set by an authorised action or automatically by a failed mandatory
inspection with a critical defect.

### 2.4 Readiness — `ReadinessStatus` (derived, never stored as an editable flag)

`READY` · `CONDITIONALLY_READY` · `NOT_READY`.
Computed by `VehicleReadinessPolicy` from explicit blockers. `CONDITIONALLY_READY` means only
`WARNING`-severity blockers are present; any `BLOCKING`-severity blocker forces `NOT_READY`.

**Blocker codes** (`ReadinessBlockerCode`, all machine-readable + human message):

| Code | Severity | Raised when |
|---|---|---|
| `VEHICLE_NOT_ACTIVE` | BLOCKING | lifecycle is `INACTIVE` |
| `VEHICLE_SUSPENDED` | BLOCKING | lifecycle is `SUSPENDED` |
| `VEHICLE_ARCHIVED` | BLOCKING | lifecycle is `ARCHIVED` |
| `VEHICLE_OUT_OF_SERVICE` | BLOCKING | service status is `OUT_OF_SERVICE` |
| `SERVICE_OVERDUE` | BLOCKING | service status is `OVERDUE` |
| `SERVICE_DUE_SOON` | WARNING | service status is `DUE` |
| `COMPLIANCE_DOCUMENT_MISSING` | BLOCKING | a required document type has no active record |
| `COMPLIANCE_DOCUMENT_EXPIRED` | BLOCKING | an active document's `expiresOn` is before the assessment date |
| `COMPLIANCE_DOCUMENT_EXPIRING` | WARNING | expiry falls inside the configured warning window |
| `MANDATORY_INSPECTION_MISSING` | BLOCKING | no passed inspection within the configured validity window |
| `INSPECTION_FAILED` | BLOCKING | the latest inspection result is `FAILED` |
| `OPEN_CRITICAL_DEFECT` | BLOCKING | an inspection recorded an unresolved `CRITICAL` defect |
| `VEHICLE_ASSIGNMENT_CONFLICT` | BLOCKING | an active trip overlaps the requested period |
| `DRIVER_MISSING` | BLOCKING | readiness requested for a period with no driver |
| `DRIVER_INELIGIBLE` | BLOCKING | driver eligibility assessment is not `ELIGIBLE` |
| `DRIVER_LICENCE_EXPIRED` | BLOCKING | driver licence expiry precedes the period |
| `DRIVER_LICENCE_EXPIRING` | WARNING | licence expiry inside the warning window |
| `DRIVER_ASSIGNMENT_CONFLICT` | BLOCKING | driver already assigned in an overlapping period |
| `SITE_RESTRICTION` | BLOCKING | requested site differs from the vehicle's site and no transfer authorisation exists |
| `OPERATING_MODE_RESTRICTION` | BLOCKING | vehicle restricted-use rules exclude the requested operating mode |
| `EMERGENCY_ONLY_RESTRICTION` | BLOCKING | emergency-only vehicle requested for a non-emergency trip |
| `MISSING_REQUIRED_EVIDENCE` | BLOCKING | a required evidence reference is absent |
| `ODOMETER_PROVENANCE_STALE` | WARNING | last odometer reading older than the configured staleness threshold |

### 2.5 Driver eligibility — `DriverEligibilityStatus`

`ELIGIBLE` · `CONDITIONAL` · `INELIGIBLE` · `SUSPENDED`, derived by `DriverEligibilityPolicy` from:
licence validity, licence class vs vehicle category, medical clearance, lifecycle status, open suspensions and
HRMS employment status. Driver lifecycle mirrors the vehicle lifecycle
(`ACTIVE`/`INACTIVE`/`SUSPENDED`/`ARCHIVED`).

### 2.6 Trip / assignment — `TripStatus`

```
PLANNED ──assign──► ASSIGNED ──start──► IN_PROGRESS ──complete──► COMPLETED
   │                   │  ▲                  │  ▲
   │                   │  └── resume ────────┘  │
   │                   ▼                        ▼
   └──cancel──────► CANCELLED ◄──cancel──   ON_HOLD ──cancel──► CANCELLED
```

| From | To | Guard |
|---|---|---|
| `PLANNED` | `ASSIGNED` | vehicle + driver readiness assessment has no BLOCKING blocker |
| `ASSIGNED` | `IN_PROGRESS` | pre-trip inspection recorded and not `FAILED` |
| `ASSIGNED`/`IN_PROGRESS` | `ON_HOLD` | hold reason required |
| `ON_HOLD` | `ASSIGNED`/`IN_PROGRESS` | resume returns to the previous status |
| `IN_PROGRESS` | `COMPLETED` | closure reason **and** closure evidence **and** non-regressing end odometer |
| `PLANNED`/`ASSIGNED`/`IN_PROGRESS`/`ON_HOLD` | `CANCELLED` | cancellation reason + `FLEET_TRIP_CANCEL` permission |
| `COMPLETED`/`CANCELLED` | anything | **prohibited** (terminal) |

### 2.7 Inspection — `InspectionStatus` / `InspectionResult`

Status `DRAFT → SUBMITTED → (ACCEPTED | REJECTED)`; result `PASSED` · `PASSED_WITH_DEFECTS` · `FAILED`.
A `FAILED` result, or any `CRITICAL` defect, raises `VehicleInspectionFailed`, forces the vehicle to
`OUT_OF_SERVICE` and opens a `VEHICLE_DEFECT` workflow item. Submitted inspections are immutable.

### 2.8 Fleet workflow item — `FleetWorkflowStatus`

```
OPEN ─assign─► ASSIGNED ─start─► IN_PROGRESS ─close─► CLOSED ─reopen(privileged)─► REOPENED ─► IN_PROGRESS
  │               │   ▲              │   ▲                ▲
  │               │   └── resume ────┘   │                │
  │               ▼                      ▼                │
  └── cancel ► CANCELLED           ON_HOLD ──────► ESCALATED ──► IN_PROGRESS
```

`ESCALATED` is entered from `OPEN`/`ASSIGNED`/`IN_PROGRESS`/`ON_HOLD` when the SLA evaluation detects a breach;
it raises `escalationLevel` and never loses the previous assignee. `CLOSED` requires closure reason **and**
closure evidence. `CANCELLED` is terminal.

### 2.9 Compliance document — `ComplianceDocumentStatus`

`ACTIVE → EXPIRING → EXPIRED` (date-driven) · `ACTIVE → SUPERSEDED` (a newer document of the same type is
registered) · `ACTIVE|EXPIRING → REVOKED` (authorised action). Only one `ACTIVE` document per
(vehicle, document type).

### 2.10 Evidence export request — `EvidenceExportStatus`

`REQUESTED → APPROVED → EXPORTED` · `REQUESTED → REJECTED`. Export is impossible from any status other than
`APPROVED`; the approver must differ from the requester and must hold `FLEET_EVIDENCE_EXPORT_APPROVE`.
Legal-hold evidence cannot be exported without `FLEET_EVIDENCE_LEGAL_HOLD_OVERRIDE`.

### 2.11 Integration inbox message — `InboxProcessingStatus`

`RECEIVED → VALIDATED → PROCESSED` · `RECEIVED → REJECTED` (signature/allowlist/schema) ·
`VALIDATED → FAILED → RETRYING → PROCESSED | DEAD_LETTERED` · `RECEIVED → DUPLICATE_IGNORED`.

---

## 3. Cross-aggregate invariants

| Invariant | Enforced by | Also enforced in DB |
|---|---|---|
| No two active vehicle assignments overlap in time | `AssignmentConflictPolicy` + `SELECT … FOR UPDATE` on the vehicle row | `EXCLUDE USING gist` on `(vehicle_id, period)` for active trips |
| No two active driver assignments overlap in time | `AssignmentConflictPolicy` | `EXCLUDE USING gist` on `(driver_id, period)` for active trips |
| Assignment requires a ready (or conditionally ready) vehicle | `VehicleReadinessPolicy` in `AssignTripCommandHandler` | — |
| Assignment requires an eligible driver | `DriverEligibilityPolicy` | — |
| Closure requires inspection + evidence + reason | `Trip.close`, `FleetWorkflowItem.close` | `NOT NULL` on closure columns when status is terminal (check constraint) |
| Odometer never regresses | `OdometerReading.advanceTo` | check constraint on service/inspection/trip odometer vs vehicle odometer |
| Odometer correction requires reason + evidence + permission | `CorrectOdometerCommandHandler` | audit row with before/after |
| Archived records are not editable | `VehicleLifecyclePolicy.requireEditable` | — |
| Cross-site access/assignment blocked unless an authorised transfer exists | `FleetAccessPolicy` + `SITE_RESTRICTION` blocker | site column filters |
| Operational, audit, evidence and assignment history is never hard-deleted | no delete methods on repositories | `REVOKE DELETE`-style trigger on `fleet_audit_records` |
| Optimistic locking on every mutable aggregate | `@Version` on the JPA record; `OptimisticLockConflictException` → HTTP 409 `FLEET_RECORD_VERSION_CONFLICT` | `version` column |

---

## 4. Domain events

`domain/event/FleetDomainEvent` is a sealed interface; each event carries aggregate id/type, site scope,
`occurredAt` and a data-minimised payload. Application services translate domain events into the
`sfl.ftlmp.<name>.v1` integration envelope written to the outbox in the same transaction — see
`S166_Event_Contracts.md`.

---

## 5. Domain exceptions → HTTP mapping

| Exception | Code | HTTP |
|---|---|---|
| `DuplicateActiveIdentifierException` | `FLEET_DUPLICATE_IDENTIFIER` | 409 |
| `MissingSiteScopeException` | `FLEET_MISSING_SITE_SCOPE` | 422 |
| `FleetAuthorizationException` | `FLEET_UNAUTHORIZED_SCOPE` | 403 |
| `UnauthorizedApprovalException` | `FLEET_UNAUTHORIZED_APPROVAL` | 403 |
| `ClosureEvidenceMissingException` | `FLEET_CLOSURE_EVIDENCE_MISSING` | 422 |
| `SlaBreachException` | `FLEET_SLA_BREACH` | 409 |
| `InvalidStateTransitionException` | `FLEET_INVALID_STATE_TRANSITION` | 409 |
| `AssignmentConflictException` | `FLEET_ASSIGNMENT_CONFLICT` | 409 |
| `ReadinessBlockedException` | `FLEET_READINESS_BLOCKED` | 422 |
| `DriverIneligibleException` | `FLEET_DRIVER_INELIGIBLE` | 422 |
| `OdometerRegressionException` | `FLEET_ODOMETER_REGRESSION` | 422 |
| `RecordNotFoundException` | `FLEET_RECORD_NOT_FOUND` | 404 |
| `OptimisticLockConflictException` | `FLEET_RECORD_VERSION_CONFLICT` | 409 |
| `RetentionClassMissingException` | `FLEET_RETENTION_CLASS_MISSING` | 422 |
| `ExportNotApprovedException` | `FLEET_EXPORT_NOT_APPROVED` | 403 |
| `AuditChainFailureException` | `FLEET_AUDIT_CHAIN_FAILURE` | 409 |
| `InvalidSignatureException` | `FLEET_INTEGRATION_INVALID_SIGNATURE` | 401 |
| `SchemaValidationFailedException` | `FLEET_INTEGRATION_SCHEMA_INVALID` | 422 |
| `SourceNotAllowedException` | `FLEET_INTEGRATION_SOURCE_NOT_ALLOWED` | 403 |
| `IntegrationConfigurationNotFoundException` | `FLEET_INTEGRATION_NOT_CONFIGURED` | 503 |
| `DashboardScopeMissingException` | `FLEET_DASHBOARD_NO_SCOPE` | 403 |
| `RestrictedDrilldownException` | `FLEET_DASHBOARD_RESTRICTED_DRILLDOWN` | 403 |
