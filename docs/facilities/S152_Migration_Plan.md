# S152 CAFM / IWMS — Migration Plan

- Service: `sfl-facilities-service`, schema `facilities`
- Location: `services/sfl-facilities-service/src/main/resources/db/migration`

## Version allocation

| Range | Owner | Status |
|---|---|---|
| V1 | Service foundation — schema, metadata, outbox, inbox | shipped |
| V2–V4 | Pre-S152 estate and the S153 fault/work-order spine | shipped |
| **V5–V8** | **S152 CAFM/IWMS** | **this pass** |
| V9– | S153 CMMS build-out (PPM schedules, SLA timers, vendor SLAs, parts) | reserved |
| V20– | S159 Room & Resource Booking | reserved |

Leaving a gap before S159 keeps its migrations legible as a block, the way S168 and S171 start at V10
in the fleet service.

## What each migration does

### V5 — `facilities_platform_foundation.sql`

The half of S152 that exists so its sub-systems do not rebuild it.

| Object | Purpose |
|---|---|
| `facility_append_only_guard()` | trigger function refusing UPDATE and DELETE |
| `facility_audit_records` | the hash-chained audit trail, with the guard attached |
| `facility_audit_chain_state` | single-row chain head; writers lock it before appending |
| `facility_runtime_configuration` | effective-dated config, seeded with 11 defaults |
| `facility_idempotency_keys` | operation + key + request fingerprint → result id |
| `outbox_messages` (altered) | delivery-state columns for a future drainer |

**Hashes and fingerprints are `VARCHAR(64)`, never `CHAR(64)`.** Hibernate maps a `String` field with
`length = 64` to `VARCHAR(64)` and refuses `CHAR(64)` under `ddl-auto: validate`, so the service will
not start; and `CHAR` blank-pads, which is a latent correctness hazard in the two places we can least
afford it. The fixed length is kept as an explicit `CHECK`. The fleet service needed a corrective
migration (`V9_1`) for exactly this; V5 is written to avoid needing one.

**Audit payloads are `TEXT`, not `JSONB`.** `jsonb` normalises what it stores — it reorders object keys
and strips whitespace — so the value read back is not the value written, and every record replays as
tampered against its hash. Byte fidelity is required; querying inside the payload is not.

### V6 — `facilities_estate_model.sql`

The estate. Extends the six V2 tables rather than replacing them.

1. **Record metadata on all six estate tables** — added via a temporary PL/pgSQL helper with defaults so
   existing rows migrate without a separate backfill, then the defaults are dropped. A record written
   from here on must state its own provenance; a column default would let a caller omit it silently.
2. **`active` → `lifecycle_status`** — existing inactive sites carry forward as `INACTIVE` *before* the
   column is dropped, so no site silently reactivates.
3. **Operating mode on sites** — `ROUTINE` / `EXAMINATION`, with a partial index on the examination case.
4. **Space attributes** — `space_type`, `area_sqm`, `cost_centre`, `bookable`, `examination_capable`,
   and the readiness lock triple with a `CHECK` that a lock records who and when.
5. **Best-effort classification of existing rows** into space types from the free-text `room_type`.
   Anything that does not match a known pattern stays `OTHER`, which is honest: an unclassified space
   should show as unclassified rather than be guessed into a type that changes whether it is bookable.
6. **Device reference integration fields** — `external_reference` and `status_reported_at`.
7. **Zone nesting and membership** — `parent_zone_id`, plus `facility_zone_memberships` keyed by
   (type, id) so "what is in this zone" stays one query for S162a and S174.
8. **`facility_assets`** — the register S153 raises work orders against.
9. **The S153 extension point** — nullable `facility_asset_id` and `room_id` on `facility_faults` and
   `work_orders`.

### V7 — `facilities_readiness.sql`

Checklists, checklist items, assessments (append-only), assessment items, blockers.

Two constraints carry domain rules into the database, because they are rules that must hold even if a
future caller bypasses the application:

- a resolved blocker must record **who, when and why**
- an assessment cannot be updated or deleted

Seeds a routine and an examination checklist **per site that existed when it ran**. On a fresh database
that is none, which is correct — an orphan checklist attached to no site would be worse than none.

### V8 — `facilities_dashboard_snapshots.sql`

`facility_dashboard_snapshots` and `facility_dashboard_snapshot_references`, both append-only.

Snapshots exist for go-live and operational review reporting, **not** to serve the live dashboard,
which is computed from the source records on request so its counts always reconcile. No writer exists
yet; the tables are the schema half of that work.

## Naming

New S152 tables are prefixed `facility_`. The six pre-existing estate tables keep their names
(`sites`, `buildings`, `facility_floors`, `facility_rooms`, `zones`, `device_references`).

Renaming them would break V2–V4, six JPA entities, `WorkOrderService`, the existing tests and the
1 400-line facilities dashboard page, in exchange for a prefix the schema name already supplies —
`facilities.facilities_sites` stutters. Recorded as deviation C-01 in the gap report.

## Rules this schema holds to

- **No cross-schema foreign key.** `asset_reference_id` points at AVAMP-Lite by value only.
- **Append-only where the SRS says immutable** — audit records, assessments, dashboard snapshots.
- **Every operational table carries site scope**, so a row can always be authorised.
- **Partial unique indexes** enforce "one active value per key per scope" on configuration, which is
  what makes supersede-rather-than-overwrite safe.

## Applying and rolling back

Forward:

```powershell
.\use-sfl-env.ps1
docker compose -f compose.facilities-db.yml up -d
cd services
mvn -pl sfl-facilities-service -am spring-boot:run
```

Flyway applies V1–V8 on start; `ddl-auto: validate` then proves the mapped schema matches. A clean
start is the check.

Rollback: there is no down-migration. To reset a **development** database:

```powershell
docker exec sfl-facilities-postgres psql -U sfl -d sfl_facilities_service -c "drop schema if exists facilities cascade;"
```

For production, V6 is the only destructive step (it drops `sites.active` after carrying its meaning
into `lifecycle_status`), and it should be preceded by a backup like any column drop.

## Known risk

V6 rewrites six live tables in one migration. On a large estate the `ALTER TABLE … ADD COLUMN` calls
with defaults are fast on PostgreSQL 11+, but the `UPDATE` that classifies room types is a full table
scan. At Phase 1 volumes (hundreds of rows) that is immaterial; if the estate is loaded before this
migration runs on a real deployment, expect it to take a few seconds and hold a lock on
`facility_rooms` for that time.
