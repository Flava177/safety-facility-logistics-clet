# S166 Fleet and Vehicle Management — Requirement-to-Test Plan

Written before implementation. Every SRS acceptance criterion gets, at minimum: happy path, validation failure,
unauthorised role, unauthorised site, duplicate/idempotent request, concurrency or repeated-delivery case where
relevant, an audit assertion and an outbox/event assertion.

Module: `services/sfl-fleet-logistics-service`, test root
`src/test/java/gh/edu/clet/sfl/fleetlogistics/fleet`.

---

## 1. Test layers

| Layer | Location | Runs without Docker | Purpose |
|---|---|---|---|
| Domain unit | `domain/` | yes | invariants, every allowed **and prohibited** state transition, readiness/eligibility/SLA policies, hash chain |
| Application | `application/` | yes | each command/query: authorisation, site scope, audit write, outbox write, error mapping |
| API contract (WebMvc slice) | `api/` | yes | status codes, envelope shape, exact SRS error wording, validation, 403 paths, pagination |
| Architecture (ArchUnit) | `architecture/` | yes | dependency direction, domain purity, no vendor names in domain, adapters use ports |
| Persistence / migration (Testcontainers) | `infrastructure/persistence/` | **Docker required** | Flyway from empty DB, constraints, uniqueness, exclusion constraints, append-only guards, optimistic locking |
| Integration reliability (Testcontainers) | `infrastructure/integration/`, `infrastructure/messaging/` | **Docker required** | webhook security, inbox idempotency, outbox retry/DLQ |
| Scheduler | `infrastructure/scheduling/` | yes | SLA evaluation, compliance expiry sweep, service-due sweep, dashboard refresh |
| End-to-end scenario | `e2e/` | **Docker required** | the 16 critical scenarios below |

Docker/PostgreSQL-dependent coverage has two paths:

1. The critical E2E suite uses `SFL_TEST_DB_URL`, `SFL_TEST_DB_USERNAME` and `SFL_TEST_DB_PASSWORD` so it can run
   against the local Docker Compose E2E database or a CI service container.
2. The older Testcontainers probe remains annotated `@Testcontainers(disabledWithoutDocker = true)` and may skip
   on Docker Desktop environments where the Java Docker client cannot auto-detect a valid Docker API endpoint.

The verification guide records the exact local commands and the expected skipped set.

---

## 2. Requirement → test matrix

### `SRS-SFL-S166-01` — Maintain Fleet Operational Records

| Case | Test |
|---|---|
| Happy path — register vehicle | `VehicleApplicationServiceTest.registers_vehicle_with_metadata` |
| Happy path — register driver | `DriverApplicationServiceTest.registers_driver_reference` |
| Validation — missing site scope (SRS wording) | `VehicleControllerTest.missing_site_returns_422_with_srs_wording` |
| Validation — blank registration, bad year, negative capacity | `VehicleControllerTest.validation_errors_use_error_envelope` |
| Duplicate — active registration within site | `VehicleApplicationServiceTest.duplicate_active_registration_is_blocked` |
| Duplicate — archived registration may be reused | `VehicleApplicationServiceTest.archived_registration_can_be_reused` |
| Duplicate — same registration in a different site is allowed | `VehicleApplicationServiceTest.same_registration_other_site_allowed` |
| Idempotency — replayed `Idempotency-Key` returns the original vehicle | `IdempotentCommandIT.replayed_registration_returns_original` |
| Unauthorised role | `VehicleApplicationServiceTest.reporting_viewer_cannot_register` |
| Unauthorised site | `VehicleApplicationServiceTest.cross_site_registration_denied` |
| Sensitive-field masking | `FleetFieldMaskingPolicyTest`, `DriverControllerTest.licence_masked_without_permission` |
| Lifecycle transitions (allowed + prohibited) | `VehicleLifecyclePolicyTest` |
| Archived record is not editable | `VehicleTest.archived_vehicle_rejects_updates` |
| Concurrency — optimistic lock conflict | `VehicleConcurrencyIT.concurrent_update_raises_version_conflict` |
| Audit assertion | `VehicleApplicationServiceTest.registration_writes_audit_with_before_after` |
| Outbox assertion | `VehicleApplicationServiceTest.registration_writes_outbox_event` |

### `SRS-SFL-S166-02` — Execute Fleet Workflow

| Case | Test |
|---|---|
| Create / assign / reassign / hold / resume / cancel / close / reopen | `FleetWorkflowItemTest`, `FleetWorkflowApplicationServiceTest` |
| Prohibited transitions | `FleetWorkflowTransitionPolicyTest.prohibited_transitions_are_rejected` |
| SLA computed from priority/severity/site/mode/type | `SlaPolicyTest` |
| Runtime configuration active at evaluation time | `SlaEvaluationSchedulerTest.uses_configuration_active_at_evaluation_time` |
| Escalation notifies the configured role | `SlaEvaluationSchedulerTest.escalation_notifies_configured_role` |
| Closure without evidence blocked (SRS wording) | `FleetWorkflowControllerTest.closure_without_evidence_returns_422` |
| Unauthorised approval (SRS wording) | `FleetWorkflowControllerTest.unauthorized_approval_returns_403` |
| Transitions and comments are immutable | `FleetWorkflowTransitionAppendOnlyIT` |
| Trip: assignment conflict (vehicle) | `AssignmentConflictPolicyTest.overlapping_vehicle_assignment_rejected` |
| Trip: assignment conflict (driver) | `AssignmentConflictPolicyTest.overlapping_driver_assignment_rejected` |
| Trip: concurrent double booking | `AssignmentConcurrencyIT.concurrent_assignment_only_one_succeeds` |
| Trip: readiness blocks assignment | `TripApplicationServiceTest.assignment_blocked_by_readiness` |
| Trip: closure requires evidence + reason + non-regressing odometer | `TripApplicationServiceTest.closure_requires_evidence_and_odometer` |
| Inspection failure blocks readiness and opens a defect workflow | `VehicleInspectionTest`, `TripApplicationServiceTest.critical_defect_takes_vehicle_out_of_service` |
| Audit + outbox on every transition | `FleetWorkflowApplicationServiceTest.transition_writes_audit_and_outbox` |

### `SRS-SFL-S166-03` — Capture Evidence and Audit Trail

| Case | Test |
|---|---|
| Hash chain links records in order | `AuditHashChainTest.chain_links_records` |
| Chain replay passes on an untouched chain | `AuditIntegrityServiceTest.intact_chain_verifies` |
| Chain replay detects a mutated record | `AuditIntegrityServiceTest.mutated_record_is_detected` |
| Chain replay detects a deleted/reordered record | `AuditIntegrityServiceTest.missing_sequence_is_detected` |
| Integrity failure raises a critical compliance alert | `AuditIntegrityServiceTest.failure_raises_critical_alert` |
| Database rejects UPDATE/DELETE on audit rows | `AuditAppendOnlyIT` |
| Evidence requires retention class (SRS wording) | `EvidenceControllerTest.missing_retention_class_returns_422` |
| Evidence stores reference + hash, never binary | `EvidenceReferenceTest.rejects_inline_content` |
| Access is logged | `EvidenceApplicationServiceTest.view_records_access_entry` |
| Export requires approval (SRS wording) | `EvidenceControllerTest.export_without_approval_returns_403` |
| Approver ≠ requester | `EvidenceExportRequestTest.self_approval_rejected` |
| Legal hold blocks export without override | `EvidenceExportRequestTest.legal_hold_blocks_export` |
| Audit search restricted to authorised roles | `AuditControllerTest.non_auditor_forbidden` |

### `SRS-SFL-S166-04` — Integrate Fleet with Related Systems

| Case | Test |
|---|---|
| Valid signed message accepted and processed once | `TelematicsIngestionIT.signed_message_processed_exactly_once` |
| Invalid signature rejected (SRS wording), no domain effect | `TelematicsWebhookControllerTest.invalid_signature_returns_401` |
| Unsigned message rejected | `TelematicsWebhookControllerTest.missing_signature_returns_401` |
| Replayed signature outside the timestamp window rejected | `HmacSignatureVerifierTest.stale_timestamp_rejected` |
| Source not on allowlist rejected | `SourceAllowlistGuardTest` |
| Schema-invalid payload rejected (SRS wording) | `TelematicsWebhookControllerTest.schema_invalid_returns_422` |
| Duplicate message safely ignored (SRS wording) | `TelematicsWebhookControllerTest.duplicate_returns_200_ignored` |
| Inbox envelope persisted before domain processing | `TelematicsIngestionIT.inbox_written_before_domain_command` |
| Outbox retry with backoff, then dead letter | `OutboxRetryAndDeadLetterIT` |
| Dead-letter replay is idempotent | `OutboxRetryAndDeadLetterIT.replay_is_idempotent` |
| Missing adapter configuration fails loudly (no silent fallback) | `IntegrationConfigurationRegistryTest.missing_configuration_fails_loud` |
| Adapters never touch operational repositories | `FleetArchitectureTest.vendor_adapters_do_not_touch_operational_repositories` |
| No provider names in the domain layer | `FleetArchitectureTest.domain_has_no_vendor_names` |
| Integration health reflects failures | `IntegrationHealthQueryTest` |

### `SRS-SFL-S166-05` — Expose Fleet Dashboards and Reports

| Case | Test |
|---|---|
| Indicators computed for every required metric | `FleetDashboardQueryTest.all_required_indicators_present` |
| Filters: site, date range, status, priority, owner, operating mode | `FleetDashboardQueryTest.filters_*` |
| Site/role scoping — user with no scope (SRS wording) | `FleetDashboardControllerTest.no_scope_returns_403` |
| Restricted drilldown (SRS wording) | `FleetDashboardControllerTest.restricted_drilldown_returns_403` |
| Drilldown returns authorised source records | `FleetDashboardControllerTest.drilldown_returns_source_records` |
| Counts reconcile with underlying records | `FleetDashboardReconciliationIT.counts_reconcile` |
| Snapshot timestamp and source references present | `FleetDashboardQueryTest.snapshot_metadata_present` |
| Stale data flagged with a warning | `FleetDashboardStaleDataIT.stale_snapshot_is_flagged` |
| Go-live readiness report | `FleetDashboardControllerTest.readiness_report_returns_summary` |

---

## 3. Critical end-to-end scenarios

| # | Scenario | Test |
|---|---|---|
| 1 | Register a vehicle → audit + outbox created | `FleetEndToEndIT.scenario_01_register_vehicle` |
| 2 | Duplicate active registration in the same site rejected | `FleetEndToEndIT.scenario_02_duplicate_registration_rejected` |
| 3 | Cross-site access denied | `FleetEndToEndIT.scenario_03_cross_site_access_denied` |
| 4 | Compliance documents added → readiness recalculated | `FleetEndToEndIT.scenario_04_compliance_drives_readiness` |
| 5 | Expired compliance blocks assignment | `FleetEndToEndIT.scenario_05_expired_compliance_blocks_assignment` |
| 6 | Ineligible driver blocks assignment | `FleetEndToEndIT.scenario_06_ineligible_driver_blocks_assignment` |
| 7 | Overlapping vehicle and driver assignments prevented | `FleetEndToEndIT.scenario_07_overlapping_assignments_prevented` |
| 8 | Pre-trip inspection with a critical failure blocks readiness | `FleetEndToEndIT.scenario_08_critical_inspection_blocks_readiness` |
| 9 | Valid assignment completed with required closure evidence | `FleetEndToEndIT.scenario_09_valid_trip_closes_with_evidence` |
| 10 | Overdue workflow escalated using runtime SLA configuration | `FleetEndToEndIT.scenario_10_overdue_workflow_escalates` |
| 11 | Signed telematics message processed exactly once | `FleetEndToEndIT.scenario_11_telematics_processed_once` |
| 12 | Unsigned / schema-invalid message rejected with no domain side effect | `FleetEndToEndIT.scenario_12_invalid_message_rejected` |
| 13 | Failed outbound integration retried and surfaced | `FleetEndToEndIT.scenario_13_failed_delivery_retries_and_surfaces` |
| 14 | Audit-chain tampering detected | `FleetEndToEndIT.scenario_14_audit_tampering_detected` |
| 15 | Dashboard counts reconcile with underlying records | `FleetEndToEndIT.scenario_15_dashboard_reconciles` |
| 16 | Stale dashboard data displayed explicitly | `FleetEndToEndIT.scenario_16_stale_data_visible` |

---

## 4. Architecture rules asserted

1. `domain..` must not depend on `org.springframework..`, `jakarta.persistence..`, `tools.jackson..`,
   `com.fasterxml.jackson..`, `org.hibernate..`, `javax..servlet`, `org.postgresql..`,
   `com.rabbitmq..`, `io.lettuce..`, `redis..`.
2. `domain..` must not depend on `api..`, `application..` or `infrastructure..`.
3. `application..` must not depend on `api..` or `infrastructure..`.
4. `api..` must not depend on `infrastructure..`.
5. No class in `domain..` may carry a vendor/provider name (allowlist-checked substrings).
6. Controllers must not be annotated `@Transactional` and must not reference repositories directly.
7. JPA `@Entity` classes exist only under `infrastructure.persistence..`.
8. Every `application.port` type is an interface.
9. Integration adapters must not depend on `infrastructure.persistence` repositories other than the inbox,
   outbox, dead-letter and health projections.
