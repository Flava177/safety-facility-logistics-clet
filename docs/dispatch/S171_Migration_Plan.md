# S171 Migration Plan

Applied S166 (V1–V9_1) and S168 (V10–V15) migrations are immutable. Dispatch begins at **V16**. Schema is
`fleet_logistics`; no cross-schema foreign keys. Migrations must apply cleanly on a fresh DB and on a DB
already carrying S166 + S168.

| Migration | Tables / content |
|---|---|
| V16 `V16__dispatch_courier_items.sql` | `courier_items` (register: direction, type, sensitivity, origin/destination, sender/recipient, handler, chain-of-custody flag, status, inbound acknowledgement + distribution + misroute fields); unique active identifier per `(site_code, item_number)`; site/date/status/sensitivity/handler indexes |
| V17 `V17__dispatch_manifests.sql` | `dispatches` (manifest number, route, assigned handler, optional trip/vehicle/driver soft refs, item count, seal IDs, status, destination centre, examination context); `dispatch_manifest_items` (dispatch↔item link, expected seal id, expected quantity, per-line return status); unique `(site_code, manifest_number)` |
| V18 `V18__dispatch_custody_and_receipts.sql` | `custody_handovers` (hop, sequence_no, transferring/receiving custodian, occurred_at, seal_state, verified count, evidence ref) with unique `(dispatch_id, sequence_no)`; `dispatch_receipts` (seal state, expected/verified count, recipient, signature evidence, outcome, variance type, edge-capture cols, `capture_correlation_id`) with unique `(dispatch_id, capture_correlation_id)` |
| V19 `V19__dispatch_returns_and_exceptions.sql` | `return_reconciliations` (expected/returned counts, shortfall, extras, broken seals, outcome); `dispatch_exception_cases` (number, type, severity, status, related item/dispatch/handover/trip, assignee, sla_due_at, explanation, evidence, decision, closure reason, escalation level, security_relevant, detected rules); `dispatch_exception_case_history` (immutable transition log); unique idempotent exception key per occurrence |
| V20 `V20__dispatch_scans_dashboard_and_defaults.sql` | `scan_import_batches` + `scan_import_rows` (idempotent row outcomes, validation errors); `dispatch_dashboard_snapshots` (indicators + reconciliation + freshness); seeds `dispatch.*` runtime-config defaults and integration allowlist/secret placeholders into `fleet_runtime_configuration` (`ON CONFLICT DO NOTHING`) |

## Standard column contract (every operational table)

`id UUID PRIMARY KEY`; `site_code VARCHAR(40) NOT NULL`; lifecycle `status VARCHAR(...) NOT NULL`;
`created_by`/`created_at`/`last_modified_by`/`last_modified_at` (TIMESTAMPTZ, UTC); `source_channel
VARCHAR(40) NOT NULL`; `audit_correlation_id VARCHAR(120)`; `version BIGINT NOT NULL DEFAULT 0` on mutable
records. Plus:

- **Unique / check constraints**: active-identifier uniqueness per site+type; positive counts; valid
  status/enum values via CHECK where helpful.
- **Reference + status indexes**: `(site_code, <date> DESC)`, `(site_code, status, <date>)`,
  `(dispatch_id, ...)`, trip/handler/centre lookups.
- **Idempotency constraints**: edge-receipt `(dispatch_id, capture_correlation_id)`; scan row
  `(batch_id, row_reference)`; provider dedup via the shared inbox `(source_system, idempotency_key)`.
- **Queue indexes**: exception SLA queue `(site_code, status, sla_due_at)`; undelivered/outstanding
  sweep indexes on the relevant timestamp + status.

## Runtime-config defaults seeded (V20, platform scope, site-overridable)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `dispatch.undelivered.window` | DURATION | `PT48H` | Inbound item undelivered/unclaimed escalation window |
| `dispatch.outstanding-return.window` | DURATION | `P3D` | Not-yet-returned item escalation window |
| `dispatch.exception.sla.default` | DURATION | `PT24H` | Base exception SLA (severity-adjusted) |
| `dispatch.dashboard.freshness-threshold` | DURATION | `PT15M` | Dashboard stale-data threshold |
| `dispatch.scheduling.undelivered-enabled` | BOOLEAN | `true` | Undelivered-item sweep toggle |
| `dispatch.scheduling.outstanding-return-enabled` | BOOLEAN | `true` | Outstanding-return sweep toggle |
| `dispatch.scheduling.sla-enabled` | BOOLEAN | `true` | Exception SLA-escalation sweep toggle |
| `dispatch.scheduling.dashboard-enabled` | BOOLEAN | `true` | Dashboard snapshot refresh toggle |
| `dispatch.scheduling.stale-integration-enabled` | BOOLEAN | `true` | Stale-integration detection toggle |

Migration tests assert clean application on an empty DB and on a DB pre-loaded with V1–V15, plus
constraint/idempotency behaviour under Testcontainers PostgreSQL.
