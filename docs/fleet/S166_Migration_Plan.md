# S166 Fleet and Vehicle Management — Flyway Migration Plan

**Schema:** `fleet_logistics` (owned solely by `sfl-fleet-logistics-service`)
**Location:** `services/sfl-fleet-logistics-service/src/main/resources/db/migration`
**Baseline:** `V1__service_foundation.sql` (existing: `service_metadata`, `outbox_messages`, `inbox_messages`)

Numbering deviates from workplan §4.3 — see conflict **C-05**. `S168_fuel` and `S171` take `V9+`.

## Rules applied to every migration

- No cross-schema foreign keys; other bounded contexts are referenced by ID only.
- All timestamps `TIMESTAMPTZ`, written and compared in UTC.
- Every mutable operational table has `version BIGINT NOT NULL DEFAULT 0` for optimistic locking, plus
  `created_by`, `created_at`, `last_modified_by`, `last_modified_at`, `source_channel`, `audit_correlation_id`.
- Site-scoped uniqueness uses **partial** unique indexes so archived/superseded rows never block a new record.
- No `ON DELETE CASCADE` that could remove operational, audit, evidence or assignment history; child links use
  `ON DELETE RESTRICT`.
- `JSONB` only where the shape is genuinely variable (event payloads, before/after audit images, checklist
  findings, dashboard source references, raw integration payloads).
- Append-only tables are protected by a `BEFORE UPDATE OR DELETE` trigger that raises an exception.

---

## V2 — `V2__fleet_vehicle_register.sql` *(S166-01)*

| Object | Notes |
|---|---|
| `vehicles` | identity, registration, VIN, make/model/year, category, capacity, site, responsible unit, operational owner, acquisition reference, lifecycle/service/availability status, odometer value + unit + source + recorded-at, restricted-use flags (`emergency_only`, `allowed_operating_modes`), metadata + `version` |
| `vehicle_compliance_documents` | vehicle FK (RESTRICT), document type, reference, issued/expires, status, evidence id, retention class, metadata |
| `vehicle_service_records` | vehicle FK, service type, performed on, odometer at service, next due date, next due odometer, provider reference, outcome, evidence id |
| `ux_fleet_vehicles_site_registration_active` | `UNIQUE (site_code, upper(registration_number)) WHERE lifecycle_status <> 'ARCHIVED'` — SRS duplicate-identifier rule |
| `ux_fleet_vehicles_site_vin_active` | same rule for VIN where present |
| `ux_fleet_compliance_active_type` | `UNIQUE (vehicle_id, document_type) WHERE status IN ('ACTIVE','EXPIRING')` |
| Indexes | `(site_code, lifecycle_status, availability_status)`, `(site_code, service_status)`, `(expires_on) WHERE status IN ('ACTIVE','EXPIRING')`, `(vehicle_id, performed_on DESC)`, `(next_due_on)`, trigram-free `upper(registration_number)` search index |
| Checks | odometer ≥ 0; `expires_on >= issued_on`; `next_due_odometer >= odometer_at_service` |

## V3 — `V3__fleet_driver_register.sql` *(S166-01)*

| Object | Notes |
|---|---|
| `driver_profile_references` | identity, HRMS staff reference, display name, licence number, licence class, licence expiry, medical clearance expiry, site, responsible unit, lifecycle status, eligibility status, suspension reason, metadata + `version` |
| `ux_fleet_drivers_site_staff_active` | `UNIQUE (site_code, upper(staff_reference)) WHERE lifecycle_status <> 'ARCHIVED'` |
| `ux_fleet_drivers_site_licence_active` | `UNIQUE (site_code, upper(licence_number)) WHERE lifecycle_status <> 'ARCHIVED'` |
| Indexes | `(site_code, lifecycle_status, eligibility_status)`, `(licence_expires_on)` |

## V4 — `V4__fleet_trips_and_inspections.sql` *(S166-02, supporting S166-01)*

| Object | Notes |
|---|---|
| `trips` | identity, trip number, vehicle id, driver id, site, purpose, origin, destination, operating mode, planned/actual start+end, status, hold reason, cancellation reason, closure reason, closure evidence id, start/end odometer, metadata + `version` |
| `vehicle_inspections` | identity, vehicle id, trip id (nullable), inspection type, status, result, performed by/at, odometer, evidence id, `findings JSONB`, critical defect flag, defect resolution |
| `btree_gist` extension + `ux_trips_vehicle_period` | `EXCLUDE USING gist (vehicle_id WITH =, tstzrange(planned_start, planned_end) WITH &&) WHERE status IN ('PLANNED','ASSIGNED','IN_PROGRESS','ON_HOLD')` |
| `ux_trips_driver_period` | same exclusion on `driver_id` |
| `ux_trips_trip_number` | `UNIQUE (site_code, trip_number)` |
| Indexes | `(site_code, status, planned_start DESC)`, `(vehicle_id, planned_start DESC)`, `(driver_id, planned_start DESC)`, `(vehicle_id, performed_at DESC)` on inspections |
| Checks | `planned_end > planned_start`; `end_odometer >= start_odometer`; terminal statuses require their reason columns |

## V5 — `V5__fleet_workflow_and_sla.sql` *(S166-02)*

| Object | Notes |
|---|---|
| `fleet_workflow_items` | identity, workflow number, workflow type, related record type + id, site, priority, severity, operating mode, status, assignee, sla_due_at, escalation level, first_response_at, closure reason, closure evidence id, closed at/by, metadata + `version` |
| `fleet_workflow_transitions` | **append-only**: item id, sequence, from/to status, action, actor, occurred at, reason, correlation id |
| `fleet_workflow_comments` | **append-only**: item id, author, body, occurred at, correlation id |
| `fleet_sla_rules` | runtime configuration: workflow type, priority, severity, site (nullable = any), operating mode (nullable = any), response minutes, resolution minutes, escalation role, effective from/to, version, updated by — *"evaluated using the runtime configuration active at evaluation time"* |
| `fleet_runtime_configuration` | generic versioned key/value (`JSONB`) for freshness thresholds, compliance warning windows, inspection validity, required document types |
| `fleet_notification_intents` | recorded notifications (recipient role/user, channel, template, payload, status) — the `NotificationPort` record of truth |
| Triggers | `fleet_workflow_transitions` / `fleet_workflow_comments` reject UPDATE and DELETE |
| Indexes | `(site_code, status, sla_due_at)`, `(assignee, status)`, `(status, sla_due_at) WHERE status NOT IN ('CLOSED','CANCELLED')`, `(item_id, sequence)` |

## V6 — `V6__fleet_audit_and_evidence.sql` *(S166-03)*

| Object | Notes |
|---|---|
| `fleet_audit_records` | **append-only, hash-chained**: id, `sequence_no BIGSERIAL UNIQUE`, site scope, actor id/display, action, resource type/id, `before_value JSONB`, `after_value JSONB`, correlation id, source channel, occurred at, `previous_hash`, `record_hash` |
| `fleet_evidence_references` | id, evidence type, file reference (URI — no binary content), `hash_algorithm`, `hash_value`, uploader, related workflow/trip/inspection ids, retention class, legal hold, site, registered at, metadata |
| `fleet_evidence_access_log` | **append-only**: evidence id, actor, action (`VIEW`/`EXPORT`), occurred at, correlation id, justification |
| `fleet_evidence_export_requests` | id, evidence id, requester, justification, recipient, status, approver, approved/rejected at, decision reason, exported at |
| Triggers | `fleet_audit_records`, `fleet_evidence_access_log` reject UPDATE and DELETE (`fleet_append_only_guard()`) |
| Indexes | `(sequence_no)`, `(site_scope, occurred_at DESC)`, `(resource_type, resource_id, occurred_at DESC)`, `(actor_id, occurred_at DESC)`, `(related_workflow_id)`, `(retention_class)`, `(legal_hold) WHERE legal_hold` |
| Checks | `retention_class` `NOT NULL` (conflict **C-08**); `hash_value` `NOT NULL` |

## V7 — `V7__fleet_integration_inbox_outbox.sql` *(S166-04)*

| Object | Notes |
|---|---|
| `outbox_messages` (ALTER) | add `attempt_count INT NOT NULL DEFAULT 0`, `next_attempt_at TIMESTAMPTZ`, `last_attempt_at`, `dead_lettered_at`; index `(status, next_attempt_at)` for `FOR UPDATE SKIP LOCKED` draining |
| `fleet_integration_inbox_messages` | id, source system, event type, `idempotency_key`, correlation id, schema version, `payload JSONB`, signature status, processing status, attempt count, error reason, dead-letter reference, received/processed at |
| `ux_fleet_inbox_source_idempotency` | `UNIQUE (source_system, idempotency_key)` — at-least-once safety |
| `fleet_integration_dead_letters` | inbox/outbox reference, reason, payload snapshot, first/last failure, replayed at/by |
| `fleet_integration_endpoints` | runtime adapter configuration: capability, provider key, base URL, secret reference, allowlist CIDRs, schema id, `active`, effective from/to, version — resolution fails loudly when absent |
| `fleet_integration_health` | capability/source, status, last success/failure, consecutive failures, backlog, dead letters, updated at |
| `vehicle_telematics_positions` | vehicle id, site, latitude, longitude, speed, heading, recorded at (device), received at, provider message reference, inbox message id |
| Indexes | `(vehicle_id, recorded_at DESC)`, `(source_system, processing_status, received_at DESC)`, `(capability) UNIQUE WHERE active` on endpoints |

## V8 — `V8__fleet_dashboard_projection.sql` *(S166-05)*

| Object | Notes |
|---|---|
| `fleet_dashboard_snapshots` | id, site scope, metric code, metric value, period start/end, `filters JSONB`, `source_references JSONB`, generated at, generated by, source freshness at |
| `ux_fleet_dashboard_snapshot` | `UNIQUE (site_scope, metric_code, period_start, period_end)` — one current snapshot per metric/period |
| Indexes | `(site_scope, generated_at DESC)`, `(metric_code, generated_at DESC)` |

---

## Verification

| Check | Test |
|---|---|
| All migrations apply cleanly from an empty database | `FleetMigrationIT.migrations_apply_from_empty_database` (Testcontainers PostgreSQL 16) |
| Hibernate `ddl-auto: validate` agrees with the migrations | `FleetSchemaValidationIT` |
| Site-scoped uniqueness rejects duplicate active registrations | `FleetMigrationIT.duplicate_active_registration_violates_unique_index` |
| Archived rows do not block re-registration | `FleetMigrationIT.archived_registration_does_not_block_reuse` |
| Overlapping vehicle/driver assignment excluded by the database | `AssignmentConcurrencyIT` |
| Append-only guards reject UPDATE/DELETE on audit, transitions, comments and access log | `AuditAppendOnlyIT` |
| No cross-schema foreign keys | `FleetDatabaseLintIT.no_cross_schema_foreign_keys` |
| Optimistic locking raises a conflict under concurrent update | `VehicleConcurrencyIT` |
