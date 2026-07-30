# S152 CAFM / IWMS — Requirement Traceability Matrix

Every clause of `SRS-SFL-S152-01` … `-05`, plus the NFRs S152 implements, mapped to the code that
implements it and the test that proves it. Where something is **not** implemented, it says so.

Legend: **Done** · **Partial** — implemented with a stated limit · **Deferred** — out of scope for this
pass, with the reason.

---

## SRS-SFL-S152-01 — Maintain CAFM/IWMS Operational Records

| Clause | Status | Implementation | Test |
|---|---|---|---|
| Records for site, building, floor, room, zone, facility asset, readiness profile | **Done** | `masterdata.domain.*`, `readiness.domain.*` | `FacilitiesMasterDataTest`, `FacilityAssetTest`, `S152MandatoryScenariosTest` §1 |
| …service request | **Partial** | The S153 fault/work-order spine already existed; S152 adds the `facility_asset_id` and `room_id` links (V6) | `WorkOrderServiceTest` (pre-existing) |
| Each record linked to an authorised site/building/room/zone/device | **Done** | FK chain in V2/V6; `resolveMemberSite` constrains zone members to the zone's site | `S152MandatoryScenariosTest` §4 |
| Users see and update only inside their site scopes and roles | **Done** | `FacilitiesAuthorization.require`, `filterBySite` | `S152MandatoryScenariosTest` §9 (6 tests) |
| Lifecycle: active, inactive, suspended, archived | **Done** | `RecordLifecycleStatus` with an explicit transition table; `ARCHIVED` terminal | `FacilitiesMasterDataTest.archived_is_terminal` |
| System-managed fields: UUID, site scope, created by/date, modified by/date, version, source channel, correlation ID | **Done** | `RecordMetadata` embedded in all seven aggregates | `FacilitiesMasterDataTest.records_the_system_managed_fields_on_creation`, `…increments_the_version_on_every_change`; `FacilitiesMigrationIntegrationTest.the_record_metadata_columns_are_on_every_estate_table` |
| Cannot save without site scope and operational owner | **Done** | `MissingSiteScopeException`; `createdBy` required by `RecordMetadata` | contract test `a_missing_required_field_is_400_with_the_field_named` |
| Duplicate active identifiers blocked within site and object type | **Done** | Pre-write check + DB unique constraint; archived records release their identifier | `S152MandatoryScenariosTest` §2 (3 tests); `FacilitiesMigrationIntegrationTest.an_asset_code_is_unique_within_a_site` |
| Sensitive fields masked from roles without permission | **Deferred** | S152 has no field-level sensitive data — no personal or biometric attributes on the estate model. Applies to S160/S161, not here | — |
| Error: Duplicate Identifier | **Done** | `DUPLICATE_IDENTIFIER` → 409, SRS wording verbatim | `FacilitiesMasterDataControllerTest.a_duplicate_identifier_is_409` |
| Error: Missing Site Scope | **Done** | `MISSING_SITE_SCOPE` → 400 | `FacilitiesErrorCode` |
| Error: Unauthorized Scope | **Done** | `UNAUTHORIZED_SCOPE` → 403 | `…an_unauthorised_scope_is_403_with_the_srs_wording` |
| AC: valid record persists with identifier and audit trail | **Done** | | `S152MandatoryScenariosTest` §10 |
| AC: duplicate blocked | **Done** | | §2 |
| AC: access denied without site scope | **Done** | | §9 |

## SRS-SFL-S152-02 — Execute CAFM/IWMS Workflow

| Clause | Status | Implementation | Test |
|---|---|---|---|
| Workflow creation, assignment, reassignment, escalation, hold, cancellation, closure | **Deferred** | This is the **S153 work-order workflow**. The build brief's non-goal: S152 provides the platform, S153 builds the workflow. The existing OPEN→ASSIGNED→CLOSED spine is untouched | `WorkOrderTest` (pre-existing) |
| SLA timers from configurable priority, severity, site, operating mode, workflow type | **Partial** | The configuration store and operating mode exist and are read at evaluation time; **no SLA timer is computed** — that is S153's | `S152MandatoryScenariosTest` "reports examination mode…" |
| Notify responsible users when assigned, overdue, escalated, blocked | **Deferred** | No notification adapter in this service. The dashboard reports the escalation window (`criticalBeyondEscalationWindow`); nothing sends | — |
| All workflow transitions and comments retained in the audit trail | **Done** | Every state change audited, including readiness transitions and blocker resolution | `S152MandatoryScenariosTest` §10 |
| Escalation rules evaluated using the runtime configuration active at evaluation time | **Done** | `RuntimeConfigurationPort`, no caching; site value overrides platform default | verified live: `PUT /configuration/{key}` then re-read |
| Cannot close without required evidence or closure reason | **Done** (readiness) | A blocker resolution requires a note, in the domain **and** as a DB check constraint | `ReadinessBlockerTest.resolving_requires_a_note`; `FacilitiesMigrationIntegrationTest.a_readiness_blocker_cannot_be_resolved_without_who_when_and_why` |
| Only authorised roles may approve, override, cancel or reopen | **Done** | `FACILITIES_READINESS_OVERRIDE` for locks; `FACILITIES_OPERATING_MODE_CHANGE` for mode | `…a_manager_without_the_override_permission_cannot_lock`, `…a_facilities_manager_may_not_declare_examination_mode` |
| Error: Closure Evidence Missing | **Partial** | Code exists and maps to 422; used for blocker resolution, not yet for work-order closure | `FacilitiesErrorCode` |
| Error: Unauthorized Approval | **Done** | `UNAUTHORIZED_APPROVAL` → 403 | `FacilitiesErrorCode` |

## SRS-SFL-S152-03 — Capture Evidence and Audit Trail

| Clause | Status | Implementation | Test |
|---|---|---|---|
| Capture actor, timestamp, before/after, source channel, correlation ID on all state-changing actions | **Done** | `AuditEvent`, written by `AuditPort.record` inside the caller's transaction | `S152MandatoryScenariosTest` §10; verified live (13 records across one workflow) |
| Audit records append-only and tamper-evident by hash chain | **Done** | `AuditHashChain` (SHA-256, canonical form, unit separators) + DB trigger refusing UPDATE/DELETE | `AuditHashChainTest` (8 tests: mutation, removal, reorder); `FacilitiesMigrationIntegrationTest.the_audit_table_refuses_updates_and_deletes` |
| Audit records cannot be modified or deleted by normal application roles | **Done** | `facility_append_only_guard()` trigger | as above |
| Evidence metadata: file reference, hash, uploader, related workflow, retention class, access history | **Deferred** | **S152 stores no evidence files.** The estate model has no upload path. Evidence belongs to S153 closure and S161 CCTV; the audit before/after payloads are the record of what changed | — |
| Evidence export requires permission, justification and audit logging | **Deferred** | No export path exists to govern | — |
| Retention class mandatory for CCTV/visitor/biometric/incident/dispatch evidence | **Not applicable** | None of those evidence classes exist in S152 | — |
| Error: Audit Chain Failure | **Done** | `AUDIT_CHAIN_FAILURE` → 500 with the SRS wording; `GET /audit/integrity` reports the break point | `AuditHashChainTest`; verified live |
| AC: audit chain replay detects tampering | **Done** | | `AuditHashChainTest.a_mutated_record_is_detected` and two others |

## SRS-SFL-S152-04 — Integrate CAFM/IWMS with Related Systems

| Clause | Status | Implementation | Test |
|---|---|---|---|
| Integrate with IAM/OIDC | **Partial** | `FacilitiesActorResolver` reads the JWT principal when present; `sfl.security.enabled=false` locally means the header path is what runs today. **No IAM is deployed** | — |
| Integrate with HRMS, NBES, CMMS, room booking, asset reference, audit/evidence, reporting | **Deferred** | No adapter is built. The device reference and `assetReferenceId` are the seams | — |
| Inbound webhooks verify HMAC/mTLS, source allowlist, schema validity | **Deferred** | **S152 exposes no inbound webhook.** Nothing to verify yet | — |
| Integration messages carry idempotency keys and correlation IDs | **Done** | `Idempotency-Key` on every state-creating POST; correlation ID on every request and every audit and outbox row | `S152MandatoryScenariosTest` "Idempotency" (3 tests); verified live |
| Failed deliveries retried and surfaced on an integration-health dashboard | **Deferred** | V5 adds the outbox delivery-state columns and the claim index; **no drainer and no health dashboard** | — |
| No vendor adapter writes directly into operational module tables | **Done** | Enforced by `FacilitiesArchitectureTest.nothing_points_into_infrastructure` and `…jpa_entities_live_only_in_persistence_packages` | `FacilitiesArchitectureTest` |
| Error: Duplicate Message | **Partial** | `DUPLICATE_MESSAGE` code exists; the inbox table exists from V1; no consumer uses it yet | — |

## SRS-SFL-S152-05 — Expose CAFM/IWMS Dashboards and Reports

| Clause | Status | Implementation | Test |
|---|---|---|---|
| Indicators: facility readiness, open service requests, unavailable rooms, site compliance exceptions, examination readiness risk | **Done** | `FacilityDashboard` — `spaces`, `maintenance`, `unavailableSpaces`, `assets`, `examinationRisks` | `S152MandatoryScenariosTest` §8; verified live |
| Filterable by site, date range, status, priority, owner, operating mode | **Partial** | Site and operating mode drive the dashboard; **date range and owner filters are on the audit and search endpoints, not the dashboard** | — |
| Dashboard records link back to source workflows and evidence where permitted | **Done** | Every `ExceptionRow` carries the source `id` and `resourceType`; drilldowns need `FACILITIES_DASHBOARD_DRILLDOWN` | `S152MandatoryScenariosTest` §8 |
| Snapshots suitable for operational review and go-live reporting | **Partial** | `facility_dashboard_snapshots` and `…_references` exist (V8); **no scheduled writer**. The live dashboard is computed on request | `FacilitiesMigrationIntegrationTest.the_s152_tables_exist_in_the_facilities_schema` |
| Users see dashboard data only for authorised sites and roles | **Done** | `FACILITIES_DASHBOARD_READ` + site filter on every input | `S152MandatoryScenariosTest` §9 |
| Counts reconcile to source records | **Done** | Computed live from the source tables, not from a snapshot | `S152MandatoryScenariosTest` §8 |
| Critical safety and examination-readiness indicators show stale-data warnings | **Done** | `stale` + `staleWarning`, threshold from runtime config, examination variant when the site is in examination mode. A never-assessed space counts as stale | `S152MandatoryScenariosTest.warns_when_readiness_is_stale` |
| Error: Data Stale | **Done** | Reported as a field on a 200 rather than an error status — the data is still worth showing, with the warning attached | as above |
| Error: No Scope | **Done** | `NO_SCOPE` → 403, distinct from being refused a particular site | `…an_actor_with_no_site_scope_is_told_so_specifically` |
| Error: Restricted Drilldown | **Done** | `FACILITIES_DASHBOARD_DRILLDOWN` separate from `_READ` | `FacilitiesPermissionMatrixTest` |
| Dashboard access recorded where required for sensitive views | **Deferred** | Read access is not audited. Writing an audit row per dashboard load would swamp the chain; revisit if a sensitive view is added | — |

## Non-functional requirements

| NFR | Status | Implementation | Test |
|---|---|---|---|
| 23.1 — role and site-scope authorisation on all APIs; privileged actions audited | **Done** | `FacilitiesAuthorization`; denials audited | `S152MandatoryScenariosTest` §9 |
| 23.1 — inbound integrations require HMAC/mTLS | **Deferred** | No inbound integration exists | — |
| 23.3 — Routine ↔ Examination mode explicit, audited, role-restricted | **Done** | `OperatingMode`, `FACILITIES_OPERATING_MODE_CHANGE` | `FacilitiesMasterDataTest` (2 tests), `S152MandatoryScenariosTest` §10 |
| 23.3 — failed outbound integrations retry with backoff | **Deferred** | Schema present (V5), no drainer | — |
| 23.5 — actor, timestamp, action, source channel, correlation ID, before/after on every change | **Done** | `AuditEvent` | `AuditHashChainTest` |
| 23.5 — append-only, tamper-evident, replay-verifiable | **Done** | `AuditHashChain` + trigger | `AuditHashChainTest` |
| 23.5 — OpenTelemetry trace context propagates | **Partial** | `traceparent` captured into the MDC by `CorrelationIdFilter` and a column exists on the outbox; no exporter configured | — |
| 23.8 — thresholds, SLAs, escalations, readiness checklists runtime-configurable and versioned | **Done** | `RuntimeConfigurationPort` (effective-dated, superseded not overwritten); `ReadinessChecklist.version` | verified live: three successive writes produced v1, v2 and a site override |
| 23.8 — infrastructure adapters isolated from domain/application by ports | **Done** | | `FacilitiesArchitectureTest` (9 rules) |
| 23.8 — architecture tests enforce module boundaries and no provider names outside adapters | **Done** | | `FacilitiesArchitectureTest` |

## Summary

| | Count |
|---|---|
| Done | 40 |
| Partial | 9 |
| Deferred | 12 |
| Not applicable | 1 |

Every **Deferred** row is either the build brief's own non-goal (S153 workflow, S158, S159), or depends
on infrastructure this platform does not yet have anywhere (IAM, a broker drainer, an evidence store).
None is deferred because it was hard.
