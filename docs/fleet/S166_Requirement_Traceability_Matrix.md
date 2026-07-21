# S166 Fleet and Vehicle Management — Requirement Traceability Matrix

**Service:** `services/sfl-fleet-logistics-service`
**Feature package:** `gh.edu.clet.sfl.fleetlogistics.fleet`
**Schema:** `fleet_logistics`
**Branch:** `fleet`
**Authoritative source:** `docs/System Mappings and SRS/SFL_SRS.docx` § *MODULE: Fleet and Vehicle Management (S166)*

The formal SRS defines exactly five S166 requirements: `SRS-SFL-S166-01` … `SRS-SFL-S166-05`.
**No `SRS-SFL-S166-06` exists.** See `S166_Gap_And_Conflict_Report.md` conflict **C-01** for how vehicle
inspections are traced instead.

Every row below is the binding link between an SRS clause, the code that implements it and the test that
proves it. Anything that cannot be traced to a row here is out of S166 scope.

---

## 1. Requirement summary

| ID | Title | Primary responsibility |
|---|---|---|
| `SRS-SFL-S166-01` | Maintain Fleet Operational Records | Site-scoped authoritative register: vehicles, drivers, assignments, compliance documents, service status, availability |
| `SRS-SFL-S166-02` | Execute Fleet Workflow | Queues, SLA, assignment/escalation/hold/cancel/closure, transitions and comments |
| `SRS-SFL-S166-03` | Capture Evidence and Audit Trail for Fleet | Hash-chained audit, evidence metadata, retention, export approval, integrity replay |
| `SRS-SFL-S166-04` | Integrate Fleet with Related Systems | HMAC/allowlist/schema-validated inbound, inbox, idempotency, retry, dead-letter, integration health |
| `SRS-SFL-S166-05` | Expose Fleet Dashboards and Reports | Indicators, filters, drilldown, reconciliation, stale-data warning |

---

## 2. `SRS-SFL-S166-01` — Maintain Fleet Operational Records

### 2.1 Requirements clauses

| Clause | Implementation | Test |
|---|---|---|
| Maintain records for vehicle, driver, assignment, compliance document, service status, availability | `domain/model/Vehicle`, `DriverProfileReference`, `Trip`, `ComplianceDocument`, `VehicleServiceRecord`, `VehicleInspection` | `VehicleTest`, `DriverProfileReferenceTest`, `ComplianceDocumentTest`, `VehicleServiceRecordTest` |
| Each record linked to an authorised site / vehicle / device reference | `SiteCode` value object on every aggregate; `Vehicle.siteCode`, `Trip.siteCode` | `VehicleTest.site_code_is_required` |
| Users only see/update records inside assigned site scopes and roles | `FleetAccessPolicy` + `AuthorizationPolicy.requireSiteAccess` in every command/query | `VehicleApplicationServiceTest`, `*ControllerTest` 403 cases |
| Active/inactive/suspended/archived lifecycle | `domain/model/VehicleLifecycleStatus` + `VehicleLifecyclePolicy` transition table | `VehicleLifecyclePolicyTest` (allowed **and** prohibited transitions) |
| System-managed: UUID, site scope, created/modified by+date, version, source channel, audit correlation ID | `RecordMetadata` value object on every aggregate | `RecordMetadataTest` |
| Site scope and operational owner required | `Vehicle.register` invariant; `MissingSiteScopeException` | `VehicleTest.registration_requires_site_and_owner` |
| Duplicate active identifiers blocked within site and object type | `VehicleRegistrationUniquenessPolicy` + partial unique index `ux_fleet_vehicles_site_registration_active` | `VehicleApplicationServiceTest.duplicate_active_registration_is_blocked`, `FleetMigrationIT` |
| Sensitive fields masked from roles without permission | `FleetFieldMaskingPolicy` (`VIN`, licence number, driver personal reference) | `FleetFieldMaskingPolicyTest`, `DriverControllerTest.masked_without_permission` |
| Save writes audit evidence and publishes change event | `VehicleApplicationService` single transaction: state + `AuditPort` + `IntegrationEventPublisher` | `VehicleApplicationServiceTest.registration_writes_audit_and_outbox` |

### 2.2 Error states (exact SRS wording preserved)

| Error state | Message | Code | Implementation | Test |
|---|---|---|---|---|
| Duplicate Identifier | `An active record with this identifier already exists for this site.` | `FLEET_DUPLICATE_IDENTIFIER` | `DuplicateActiveIdentifierException` → HTTP 409 | `VehicleControllerTest.duplicate_returns_409_with_srs_wording` |
| Missing Site Scope | `Select a valid CLET site before saving this record.` | `FLEET_MISSING_SITE_SCOPE` | `MissingSiteScopeException` → HTTP 422 | `VehicleControllerTest.missing_site_returns_422_with_srs_wording` |
| Unauthorized Scope | `You are not authorised to access this site or record.` | `FLEET_UNAUTHORIZED_SCOPE` | `FleetAuthorizationException` → HTTP 403 | `VehicleControllerTest.cross_site_returns_403_with_srs_wording` |

### 2.3 Acceptance criteria

| AC | Given / When / Then | Test |
|---|---|---|
| 01-AC1 | Authorised user creates a valid record → persisted with unique identifier and audit trail | `FleetVehicleRegistrationE2E.scenario_01_register_vehicle_writes_audit_and_outbox` |
| 01-AC2 | Duplicate active identifier → blocked with duplicate error | `FleetVehicleRegistrationE2E.scenario_02_duplicate_active_registration_rejected` |
| 01-AC3 | User lacking site scope → access denied | `FleetVehicleRegistrationE2E.scenario_03_cross_site_access_denied` |

---

## 3. `SRS-SFL-S166-02` — Execute Fleet Workflow

### 3.1 Requirements clauses

| Clause | Implementation | Test |
|---|---|---|
| Creation, assignment, reassignment, escalation, hold, cancellation, closure | `domain/model/FleetWorkflowItem` + `FleetWorkflowTransitionPolicy` | `FleetWorkflowItemTest`, `FleetWorkflowTransitionPolicyTest` |
| Permitted reopening | `FleetWorkflowItem.reopen` guarded by `FLEET_WORKFLOW_REOPEN` permission | `FleetWorkflowItemTest.reopen_*` |
| SLA timers from configurable priority, severity, site, operating mode, workflow type | `domain/policy/SlaPolicy` + `application/port/RuntimeConfigurationPort` (`SlaRuleSet`) | `SlaPolicyTest`, `RuntimeSlaConfigurationIT` |
| Notify on assigned / overdue / escalated / blocked | `application/port/NotificationPort` + `RecordedNotificationAdapter` (simulator) | `FleetWorkflowApplicationServiceTest.notifies_*` |
| Retain all transitions and comments in the audit trail | append-only `fleet_workflow_transitions`, `fleet_workflow_comments` + audit record per transition | `FleetWorkflowTransitionAppendOnlyIT` |
| Closure requires evidence or closure reason | `FleetWorkflowItem.close` invariant + `ClosureEvidenceMissingException` | `FleetWorkflowItemTest.close_without_evidence_is_rejected` |
| Only authorised roles approve/override/cancel/reopen | `FleetAccessPolicy.requirePrivilegedTransition` | `FleetWorkflowApplicationServiceTest.unauthorized_approval_is_rejected` |
| Escalation evaluated with runtime configuration active at evaluation time | `SlaEvaluationScheduler` reads `RuntimeConfigurationPort` at each run | `SlaEvaluationSchedulerTest.uses_configuration_active_at_evaluation_time` |
| Assignment/trip workflow (vehicle ↔ driver) | `domain/model/Trip` + `TripTransitionPolicy` + `AssignmentConflictPolicy` | `TripTest`, `AssignmentConflictPolicyTest` |
| Vehicle inspection capture and readiness blocking *(traced here + to 01, see C-01)* | `domain/model/VehicleInspection`, `VehicleReadinessPolicy` | `VehicleInspectionTest`, `VehicleReadinessPolicyTest` |

### 3.2 Error states

| Error state | Message | Code | HTTP | Test |
|---|---|---|---|---|
| Closure Evidence Missing | `Required evidence must be attached before closure.` | `FLEET_CLOSURE_EVIDENCE_MISSING` | 422 | `FleetWorkflowControllerTest.closure_without_evidence_returns_422` |
| SLA Breach | `This item has breached its configured SLA and has been escalated.` | `FLEET_SLA_BREACH` | 409 / event | `SlaEvaluationSchedulerTest.breach_escalates_and_records_message` |
| Unauthorized Approval | `You do not have permission to approve this workflow transition.` | `FLEET_UNAUTHORIZED_APPROVAL` | 403 | `FleetWorkflowControllerTest.unauthorized_approval_returns_403` |

### 3.3 Acceptance criteria

| AC | Given / When / Then | Test |
|---|---|---|
| 02-AC1 | Assignee updates progress → transition recorded with actor and timestamp | `FleetWorkflowE2E.scenario_10_transition_records_actor_and_timestamp` |
| 02-AC2 | Required closure evidence missing → closure blocked | `FleetWorkflowE2E.scenario_09_closure_requires_evidence` |
| 02-AC3 | SLA threshold breached → scheduled evaluation escalates and notifies configured role | `FleetWorkflowE2E.scenario_10_overdue_workflow_escalates_with_runtime_sla` |

---

## 4. `SRS-SFL-S166-03` — Capture Evidence and Audit Trail for Fleet

| Clause | Implementation | Test |
|---|---|---|
| Capture actor, timestamp, before/after, source channel, correlation ID for all state changes | `domain/model/AuditEvent` + `AuditPort` called inside every command transaction | `AuditEventTest`, every `*ApplicationServiceTest` audit assertion |
| Evidence metadata: file reference, hash, uploader, related workflow, retention class, access history | `domain/model/EvidenceReference`, `EvidenceAccessEntry` | `EvidenceReferenceTest` |
| Evidence access/export requires role permission, justification and audit logging | `EvidenceExportRequest` state machine + `FleetAccessPolicy` | `EvidenceExportRequestTest`, `EvidenceControllerTest` |
| Append-only, tamper-evident hash chain | `AuditHashChain` (`hash = SHA-256(prevHash ‖ canonical(record))`) | `AuditHashChainTest` |
| Audit integrity replay / check | `AuditIntegrityService.verifyChain` + `GET /api/v1/fleet/audit/integrity-check` | `AuditIntegrityServiceTest`, `AuditChainTamperingIT` |
| Critical compliance alert on integrity failure | `AuditIntegrityService` publishes `sfl.ftlmp.fleet-audit-integrity-failed.v1` | `AuditIntegrityServiceTest.failure_raises_critical_alert` |
| No normal application role may update or delete audit records | No update/delete methods on `AuditRecordRepository`; DB trigger `fleet_audit_records_no_mutation` | `AuditAppendOnlyIT` |
| Retention class, legal hold, export recipient/approval | `RetentionClass`, `EvidenceReference.legalHold`, `EvidenceExportRequest` | `EvidenceExportRequestTest` |

### 4.1 Error states

| Error state | Message | Code | HTTP |
|---|---|---|---|
| Export Not Approved | `Evidence export requires approval and a recorded reason.` | `FLEET_EXPORT_NOT_APPROVED` | 403 |
| Retention Class Missing | `Select a retention class before saving this evidence.` | `FLEET_RETENTION_CLASS_MISSING` | 422 |
| Audit Chain Failure | `Audit integrity check failed. Escalate to compliance and security.` | `FLEET_AUDIT_CHAIN_FAILURE` | 409 |

### 4.2 Acceptance criteria

| AC | Given / When / Then | Test |
|---|---|---|
| 03-AC1 | Evidence uploaded → metadata, hash and audit reference stored | `EvidenceE2E.evidence_registration_stores_hash_and_audit` |
| 03-AC2 | User without export permission submits export → blocked | `EvidenceE2E.export_without_permission_is_blocked` |
| 03-AC3 | Audit chain replay detects tampering → critical compliance alert | `AuditChainTamperingIT.scenario_14_detect_audit_chain_tampering` |

---

## 5. `SRS-SFL-S166-04` — Integrate Fleet with Related Systems

| Clause | Implementation | Test |
|---|---|---|
| HRMS driver/staff references | `application/port/HrmsDriverDirectoryPort` + `SimulatedHrmsDriverDirectoryAdapter` | `HrmsDriverDirectoryAdapterTest` |
| CMMS / service-maintenance vendors | `ServiceVendorPort` + `SimulatedServiceVendorAdapter` | `ServiceVendorAdapterTest` |
| Fuel/logbook capability *(seam only — S168_fuel is out of scope)* | `FuelCapabilityPort` (interface + event contract only) | `FleetIntegrationSeamArchitectureTest` |
| Dispatch capability *(seam only — S171 is out of scope)* | `DispatchCapabilityPort` (interface + event contract only) | `FleetIntegrationSeamArchitectureTest` |
| GPS/telematics-ready adapter | `TelematicsPort` + `SimulatedTelematicsAdapter` + inbound webhook | `TelematicsWebhookControllerTest` |
| Audit/evidence service | `EvidenceStorePort` + `SimulatedEvidenceStoreAdapter` | `EvidenceStoreAdapterTest` |
| Notification capability | `NotificationPort` + `RecordedNotificationAdapter` | `NotificationAdapterTest` |
| HMAC or mTLS verification | `HmacSignatureVerifier` (constant-time, timestamp window, replay guard) | `HmacSignatureVerifierTest` |
| Source allowlist | `SourceAllowlistGuard` | `SourceAllowlistGuardTest` |
| Payload schema validation | `IntegrationPayloadSchemaValidator` (registered schema + version) | `IntegrationPayloadSchemaValidatorTest` |
| Idempotency key + correlation ID | `IntegrationInboxMessage.idempotencyKey`, unique index | `IntegrationInboxIdempotencyIT` |
| Inbox persistence before domain processing | `IntegrationInboxService.accept` commits envelope first | `TelematicsIngestionIT.scenario_11_signed_message_processed_exactly_once` |
| At-least-once delivery safety / idempotent consumers | dedup on `(source, idempotency_key)` | `IntegrationInboxIdempotencyIT` |
| Retry with configurable backoff + dead letter | `OutboxDrainer` + `IntegrationRetryPolicy` + `integration_dead_letters` | `OutboxDrainerTest`, `OutboxRetryAndDeadLetterIT` |
| Integration health projection | `IntegrationHealthProjection` + `GET /api/v1/fleet/integrations/health` | `IntegrationHealthQueryTest` |
| No vendor adapter writes operational tables | adapters depend only on ports | `FleetArchitectureTest.vendor_adapters_do_not_touch_operational_repositories` |
| No provider-specific model/name in domain | ArchUnit name + import rules | `FleetArchitectureTest.domain_has_no_vendor_names` |
| Runtime adapter resolution fails loudly | `IntegrationConfigurationRegistry.requireActive` → `IntegrationConfigurationNotFoundException` | `IntegrationConfigurationRegistryTest.missing_configuration_fails_loud` |

### 5.1 Error states

| Error state | Message | Code | HTTP |
|---|---|---|---|
| Invalid Signature | `Integration message rejected: signature verification failed.` | `FLEET_INTEGRATION_INVALID_SIGNATURE` | 401 |
| Schema Validation Failed | `Integration message rejected: payload does not match registered schema.` | `FLEET_INTEGRATION_SCHEMA_INVALID` | 422 |
| Duplicate Message | `Duplicate integration message received and safely ignored.` | `FLEET_INTEGRATION_DUPLICATE_MESSAGE` | 200 (accepted, not reprocessed) |

### 5.2 Acceptance criteria

| AC | Given / When / Then | Test |
|---|---|---|
| 04-AC1 | Valid signed webhook, schema passes → stored and processed once | `TelematicsIngestionIT.scenario_11_signed_message_processed_exactly_once` |
| 04-AC2 | Unsigned webhook → rejected and logged without domain side effects | `TelematicsIngestionIT.scenario_12_unsigned_or_invalid_message_has_no_domain_effect` |
| 04-AC3 | Downstream unavailable → retried and surfaced on integration dashboard | `OutboxRetryAndDeadLetterIT.scenario_13_failed_delivery_retries_and_surfaces` |

---

## 6. `SRS-SFL-S166-05` — Expose Fleet Dashboards and Reports

| Indicator (SRS + brief) | Metric code | Implementation |
|---|---|---|
| Vehicle availability | `FLEET_VEHICLES_AVAILABLE` | `FleetDashboardProjection` |
| Ready / conditionally ready / unavailable | `FLEET_VEHICLES_READY`, `FLEET_VEHICLES_CONDITIONALLY_READY`, `FLEET_VEHICLES_UNAVAILABLE` | `VehicleReadinessPolicy` re-evaluated per snapshot |
| Expired or expiring compliance | `FLEET_COMPLIANCE_EXPIRED`, `FLEET_COMPLIANCE_EXPIRING` | `ComplianceDocumentQuery` |
| Service due / overdue | `FLEET_SERVICE_DUE`, `FLEET_SERVICE_OVERDUE` | `VehicleServiceRecordQuery` |
| Assignment conflicts | `FLEET_ASSIGNMENT_CONFLICTS` | `AssignmentConflictQuery` |
| Driver eligibility blockers | `FLEET_DRIVER_ELIGIBILITY_BLOCKERS` | `DriverEligibilityPolicy` |
| Inspection failures | `FLEET_INSPECTION_FAILURES` | `VehicleInspectionQuery` |
| Readiness blockers | `FLEET_READINESS_BLOCKERS` | `VehicleReadinessPolicy` |
| SLA breaches | `FLEET_SLA_BREACHES` | `FleetWorkflowQuery` |
| Integration failures / stale telematics | `FLEET_INTEGRATION_FAILURES`, `FLEET_TELEMATICS_STALE` | `IntegrationHealthProjection`, `VehicleTelemetryQuery` |

| Clause | Implementation | Test |
|---|---|---|
| Filters: site, date range, status, priority, owner/responsible unit, operating mode | `FleetDashboardFilter` | `FleetDashboardQueryTest.filters_*` |
| Site and role scoping | `FleetAccessPolicy.visibleSites` | `FleetDashboardControllerTest.no_scope_returns_403` |
| Drilldown to authorised source records | `GET /api/v1/fleet/dashboard/drilldown/{metricCode}` | `FleetDashboardControllerTest.restricted_drilldown_returns_403` |
| Reconciliation to source records | `FleetDashboardReconciliationService` + `?reconcile=true` | `FleetDashboardReconciliationIT.scenario_15_counts_reconcile` |
| Snapshot timestamp and source references | `FleetDashboardSnapshot.generatedAt`, `sourceReferences` | `FleetDashboardQueryTest` |
| Stale-data warning on freshness breach | `DashboardFreshnessPolicy` (runtime threshold) | `FleetDashboardStaleDataIT.scenario_16_stale_data_is_visible` |
| Operational / go-live readiness reporting | `GET /api/v1/fleet/dashboard/readiness-report` | `FleetDashboardControllerTest.readiness_report_*` |

### 6.1 Error states

| Error state | Message | Code | HTTP |
|---|---|---|---|
| Data Stale | `Dashboard data is older than the configured freshness threshold.` | `FLEET_DASHBOARD_DATA_STALE` | 200 + warning (non-fatal, per SRS "shows a stale-data warning") |
| No Scope | `No site scope is assigned to your user profile.` | `FLEET_DASHBOARD_NO_SCOPE` | 403 |
| Restricted Drilldown | `You do not have permission to view the underlying record.` | `FLEET_DASHBOARD_RESTRICTED_DRILLDOWN` | 403 |

### 6.2 Acceptance criteria

| AC | Given / When / Then | Test |
|---|---|---|
| 05-AC1 | Manager with site scope opens dashboard → only authorised indicators shown | `FleetDashboardControllerTest.site_scoped_indicators_only` |
| 05-AC2 | Stale metric renders → stale-data warning shown | `FleetDashboardStaleDataIT.scenario_16_stale_data_is_visible` |
| 05-AC3 | User with permission clicks exception → source workflow/evidence opens | `FleetDashboardControllerTest.drilldown_returns_source_records` |

---

## 7. Supporting behaviour traceability (no new SRS IDs minted)

| Behaviour | Traced to | Justification |
|---|---|---|
| Vehicle inspection capture and lifecycle | `SRS-SFL-S166-01` | An inspection is a fleet operational record carrying service status and availability evidence; S166-01 requires the register to maintain "service status, availability" |
| Inspection as a blocking workflow step (pre-trip, closure) | `SRS-SFL-S166-02` | S166-02 requires workflow progress, evidence and closure validation; the pre-trip inspection is the evidence-bearing step |
| Inspection failure indicator | `SRS-SFL-S166-05` | S166-05 requires readiness-blocker indicators |
| Readiness computation | `SRS-SFL-S166-01` (record state) + `SRS-SFL-S166-05` ("readiness blockers" indicator) | Readiness is derived state, never free-form input |
| Odometer provenance and correction workflow | `SRS-SFL-S166-01` + `SRS-SFL-S166-03` | Record integrity + before/after audit |
| Operating-mode (`EMERGENCY`) handling on trips and workflow | `SRS-SFL-S166-02` | S166-02 names operating mode as an SLA input |
| `GET /api/v1/fleet/vehicles/{id}/movement` | `SRS-SFL-S166-04` + `SRS-SFL-S166-05` | Telematics is an S166-04 integration; stale telematics is an S166-05 indicator |

---

## 8. Deliberately **not** implemented in this slice

| Item | Reason |
|---|---|
| `SRS-SFL-S166-06` | Does not exist in the formal SRS (conflict **C-01**) |
| `POST /api/v1/fleet/emergency-logistics` | Not derivable from S166-01…05 text; workplan mapping is a category error (conflict **C-12**) |
| `S168_fuel` fuel transactions, cards, reconciliation | Out of task scope; only the `FuelCapabilityPort` seam and event contract exist |
| `S171` courier/dispatch manifests and custody | Out of task scope; only the `DispatchCapabilityPort` seam exists |
| Real vendor connections (HRMS, CMMS, telematics, notification) | No approved contract/configuration; simulator adapters only |
| Postgres Row-Level Security policies | Requires a per-request DB role/session decision (conflict **C-09**) |
