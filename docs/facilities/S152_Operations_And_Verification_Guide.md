# S152 CAFM / IWMS — Operations and Verification Guide

How to run `sfl-facilities-service`, and the exact walkthrough that proves S152 works end to end.

## 1. Start

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
.\use-sfl-env.ps1                                   # JDK 17 + database URLs
docker compose -f compose.facilities-db.yml up -d   # or compose.service-dbs.yml for all five
cd services
mvn -pl sfl-facilities-service -am test             # 147 tests
mvn -pl sfl-facilities-service -am spring-boot:run
```

| URL | What |
|---|---|
| <http://localhost:8091/actuator/health> | health — expect `{"status":"UP"}` |
| <http://localhost:8091/v3/api-docs> | OpenAPI JSON — 49 paths |
| <http://localhost:8091/swagger-ui.html> | Swagger UI (redirects to `/swagger-ui/index.html`) |
| <http://localhost:8091/> | the pre-S152 facilities dashboard page |

Local runs have **no sign-in** (`sfl.security.enabled=false`); the actor comes from the `X-SFL-*`
headers. Swagger UI declares them globally, so they can be filled in once and reused.

## 2. Databases

| Purpose | URL | Env |
|---|---|---|
| Runtime | `localhost:5441/sfl_facilities_service` | `SFL_FACILITIES_DB_URL` |
| End-to-end | `localhost:55441/sfl_facilities_service_e2e` | `SFL_FACILITIES_TEST_DB_URL` |

To re-run the migrations from clean:

```powershell
docker exec sfl-facilities-postgres psql -U sfl -d sfl_facilities_service -c "drop schema if exists facilities cascade;"
```

Flyway then applies V1–V8 on the next start. Expect:

```
Successfully applied 8 migrations to schema "facilities", now at version v8
Started FacilitiesServiceApplication in ~10 seconds
```

If the service starts, **Hibernate's `ddl-auto: validate` has already passed** against every entity —
that is the cheapest schema check available and it is why the service is configured that way.

## 3. The verification walkthrough

Every step below was run against a live service. `$H` carries an administrator actor scoped to all
sites.

```powershell
$H = @{
  "Content-Type" = "application/json"
  "X-SFL-User"   = "manager"
  "X-SFL-Roles"  = "SFL_ADMIN"
  "X-SFL-Sites"  = "*"
}
$base = "http://localhost:8091/api/v1/facilities"
```

### 3.1 Build the estate — scenarios 1–4

```powershell
$site = irm "$base/sites" -Method Post -Headers $H -Body '{"siteCode":"ACCRA","name":"Accra Centre"}'
$bld  = irm "$base/buildings" -Method Post -Headers $H -Body "{`"siteId`":`"$($site.id)`",`"buildingCode`":`"LAW`",`"name`":`"Law Block`"}"
$flr  = irm "$base/floors" -Method Post -Headers $H -Body "{`"buildingId`":`"$($bld.id)`",`"floorCode`":`"GF`",`"name`":`"Ground Floor`",`"levelNumber`":0}"
$room = irm "$base/rooms" -Method Post -Headers $H -Body "{`"floorId`":`"$($flr.id)`",`"roomCode`":`"HALL-A`",`"name`":`"Moot Courtroom A`",`"spaceType`":`"MOOT_COURTROOM`",`"capacity`":120}"
```

Observed: the site is `ACTIVE` / `ROUTINE` at version 0; the space defaults to `bookable=true`,
`examinationCapable=true` from `MOOT_COURTROOM`, readiness `UNKNOWN`.

Duplicate and validation checks:

| Request | Result |
|---|---|
| `POST /rooms` with `roomCode: "hall-a"` again | `409 DUPLICATE_IDENTIFIER` — *"An active space with identifier 'HALL-A' already exists for site ACCRA."* |
| `POST /rooms` with `capacity: -5` | `400 VALIDATION_FAILED` — `capacity must be greater than or equal to 0` |

### 3.2 Readiness — scenarios 5, 6, 7

```powershell
$cl = irm "$base/readiness/checklists" -Method Post -Headers $H -Body '{"siteCode":"ACCRA","checklistCode":"MOOT-EXAM","name":"Moot readiness","spaceType":"MOOT_COURTROOM","items":[{"itemCode":"FIRE-EGRESS","description":"Fire exits latch","severityIfFailed":"CRITICAL","mandatory":true,"weight":3},{"itemCode":"SEATING","description":"Seating to plan","severityIfFailed":"MAJOR","mandatory":true,"weight":2},{"itemCode":"SIGNAGE","description":"Signage displayed","severityIfFailed":"MINOR","mandatory":false,"weight":1}]}'
```

Submit an assessment where the fire door fails:

```powershell
irm "$base/readiness/assessments" -Method Post -Headers $H -Body "{`"roomId`":`"$($room.id)`",`"checklistId`":`"$($cl.id)`",`"answers`":[{`"itemCode`":`"FIRE-EGRESS`",`"passed`":false},{`"itemCode`":`"SEATING`",`"passed`":true},{`"itemCode`":`"SIGNAGE`",`"passed`":true}]}"
```

Observed: `outcome=BLOCKED  score=50  MOOT-EXAM v2`. One `CRITICAL` blocker open, sourced
`CHECKLIST_ITEM`, and the space's status is now `BLOCKED`. The score is 50 because the weight-3 item
failed out of 6 total weight — the status came from severity, not from the score.

Try to force it ready:

```powershell
irm "$base/rooms/$($room.id)/readiness" -Method Patch -Headers $H -Body '{"status":"READY","notes":"Looks fine"}'
```

Observed: `422 READINESS_BLOCKED` — *"This space cannot be marked ready while 1 critical blocker(s)
remain open."*

Resolve it, then re-read:

```powershell
irm "$base/readiness/blockers/$blockerId/resolution" -Method Patch -Headers $H -Body '{"resolutionNotes":"Latch replaced and retested"}'
irm "$base/readiness/rooms/$($room.id)" -Headers $H
```

Observed: `status=READY  score=50  | All readiness checks passed.`

### 3.3 Asset-driven readiness

```powershell
$asset = irm "$base/assets" -Method Post -Headers $H -Body "{`"siteCode`":`"ACCRA`",`"assetCode`":`"GEN-01`",`"name`":`"Standby generator`",`"category`":`"GENERATOR`",`"criticality`":`"CRITICAL`",`"roomId`":`"$($room.id)`",`"serviceIntervalDays`":90,`"installedOn`":`"2026-04-01`"}"
irm "$base/assets/$($asset.id)/status" -Method Patch -Headers $H -Body '{"operationalStatus":"OUT_OF_SERVICE","notes":"Will not start"}'
```

Observed: the asset reports `impairsReadiness=true` and `serviceDueOn=2026-06-30`; the hall becomes
`BLOCKED` with *"1 critical, 0 major, 0 minor and 0 advisory blocker(s) open."*

Return the asset to service and the hall goes back to `READY` — the blocker the asset raised is the one
it resolves.

### 3.4 Operating mode

```powershell
irm "$base/sites/$($site.id)/operating-mode" -Method Patch -Headers $H -Body '{"operatingMode":"EXAMINATION","reason":"Bar finals"}'
```

Observed: `ACCRA EXAMINATION by=manager`. Repeating the same call returns
`422 OPERATING_MODE_TRANSITION_INVALID`. A `FACILITIES_MANAGER` actor is refused with 403 — the
permission sits with `CENTRE_MANAGER`, `COMMAND_ROLE` and `FACILITIES_DIRECTOR`.

### 3.5 Dashboard — scenario 8

```powershell
irm "$base/dashboard?siteCode=ACCRA" -Headers $H
```

Observed on a one-space site with an open critical blocker and open maintenance:

```
site=ACCRA mode=ROUTINE score=0
spaces: total=1 ready=0 blocked=1 examCapable=1 availForExam=0
blockers: crit=1 major=0 total=1
assets: total=1 impaired=1 overdue=1
stale=False | examRisks=1
```

On a freshly registered space with no assessment, `stale=True` and
`staleWarning="1 space(s) have readiness older than PT24H…"`, with the drilldown reason
`"Never assessed"` — a space that has never been assessed is the most stale thing on the estate.

### 3.6 Site scope — scenario 9

| Actor | Request | Result |
|---|---|---|
| `X-SFL-Roles: FACILITIES_MANAGER`, `X-SFL-Sites: KUMASI` | `GET /sites/{accraId}` | `403 UNAUTHORIZED_SCOPE` |
| `X-SFL-Roles: FACILITIES_MANAGER`, no `X-SFL-Sites` | `GET /rooms` | `403 NO_SCOPE` |
| `X-SFL-Roles: FLEET_DRIVER` | any facilities read | `403 UNAUTHORIZED_SCOPE` |

### 3.7 Audit and integrity — scenario 10

```powershell
irm "$base/audit?limit=200" -Headers $H
irm "$base/audit/integrity" -Headers $H
```

Observed after the walkthrough above — 13 records, then the chain replayed clean:

```
AUTHORIZATION_DENIED x1     BUILDING_CREATED x1        FLOOR_CREATED x1
READINESS_ASSESSMENT_SUBMITTED x1   READINESS_BLOCKER_RAISED x1
READINESS_CHECKLIST_CREATED x1      ROOM_CREATED x1    ROOM_READINESS_CHANGED x1
SITE_CREATED x1             SITE_OPERATING_MODE_CHANGED x1  …

intact=True verified=13
```

The `AUTHORIZATION_DENIED` record is the Kumasi manager's refused read, with `actorId=kumasi.mgr`.
Filtering works: `GET /audit?action=AUTHORIZATION_DENIED` returns just that one.

Running the integrity check is itself audited, so the count rises by one each time it is called.

### 3.8 Idempotency

```powershell
$k = @{ "Idempotency-Key" = "demo-1" } + $H
irm "$base/sites" -Method Post -Headers $k -Body '{"siteCode":"KUMASI","name":"Kumasi Centre"}'   # creates
irm "$base/sites" -Method Post -Headers $k -Body '{"siteCode":"KUMASI","name":"Kumasi Centre"}'   # same id
irm "$base/sites" -Method Post -Headers $k -Body '{"siteCode":"KUMASI","name":"A different name"}' # 409
```

Observed: the replay returns the **same** id; the third returns `409 IDEMPOTENCY_KEY_CONFLICT`.

### 3.9 Runtime configuration

```powershell
irm "$base/configuration/facilities.readiness.staleness-threshold" -Method Put -Headers $H -Body '{"value":"P2D","valueType":"DURATION","description":"Tightened for examinations"}'
irm "$base/configuration/facilities.readiness.staleness-threshold" -Method Put -Headers $H -Body '{"value":"P1D","valueType":"DURATION"}'
irm "$base/configuration/facilities.readiness.staleness-threshold" -Method Put -Headers $H -Body '{"value":"PT12H","valueType":"DURATION","siteCode":"ACCRA"}'
irm "$base/configuration?siteCode=ACCRA" -Headers $H
```

Observed: `v1`, then `v2` — superseded, not overwritten — and the ACCRA override sitting alongside the
platform default at `v0`. The chain stays intact across all three writes.

### 3.10 Actor permissions

```powershell
irm "$base/actor/permissions" -Headers @{ "X-SFL-User"="tech"; "X-SFL-Roles"="IFIMP_TECHNICIAN"; "X-SFL-Sites"="ACCRA" }
```

Observed for a technician: `FACILITIES_ASSET_MANAGE`, `FACILITIES_ASSET_READ`,
`FACILITIES_DASHBOARD_READ`, `FACILITIES_DEVICE_REFERENCE_READ`, `FACILITIES_READINESS_ASSESS`,
`FACILITIES_READINESS_READ`, `FACILITIES_SITE_READ`, `FACILITIES_SPACE_READ`, `FACILITIES_ZONE_READ` —
field work, no estate restructuring, no override.

## 4. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Schema validation: wrong column type … found [bpchar]` | a `CHAR(n)` column mapped as a `String` with `length = n` | use `VARCHAR(n)`; see V5's note |
| `No default constructor found` on a service | two unannotated constructors — Spring considers non-public ones too | `@Autowired` the production constructor |
| `could not determine data type of parameter` | a null bound into a `(:p is null or col = :p)` JPQL clause | build the predicate with a `Specification` instead |
| Audit chain replays as broken with everything untouched | payloads stored as `jsonb` (key reordering) or timestamps hashed at nanosecond precision | `TEXT` payloads; truncate to microseconds before hashing |
| `409` on a configuration write | Hibernate ordered the INSERT before the supersede UPDATE, tripping the partial unique index | flush between them |
| Testcontainers tests skip | the Java Docker client cannot reach the daemon | verify against the compose e2e database instead (§5) |

## 5. Verifying the migrations without Testcontainers

When the Java Docker client cannot reach the daemon, run the service against the compose **e2e**
database with the schema dropped first. This is what was used to verify this build:

```powershell
docker exec sfl-facilities-e2e-postgres psql -U sfl -d sfl_facilities_service_e2e -c "drop schema if exists facilities cascade;"
$env:SFL_FACILITIES_DB_URL = "jdbc:postgresql://localhost:55441/sfl_facilities_service_e2e"
cd services\sfl-facilities-service
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8091"
```

A clean start proves the migrations apply **and** that the mapped schema matches, which together are
most of what the integration test asserts.

## 6. Configuration keys

Seeded by V5 as platform defaults; every one is overridable per site.

| Key | Default | Effect |
|---|---|---|
| `facilities.readiness.staleness-threshold` | `P7D` | when routine readiness is reported stale |
| `facilities.readiness.examination-staleness-threshold` | `PT24H` | the same, for a site in examination mode |
| `facilities.dashboard.freshness-threshold` | `PT15M` | snapshot age before a stale-data warning |
| `facilities.dashboard.default-page-size` | `50` | drilldown page size when unspecified |
| `facilities.blocker.critical-escalation-window` | `PT4H` | how long an open critical blocker may age before it is reported as escalated |
| `facilities.asset.service-due-warning-window` | `P14D` | how far ahead a service is reported as due |
| `facilities.asset.warranty-warning-window` | `P30D` | how far ahead a warranty is reported as expiring |
| `facilities.device.staleness-threshold` | `PT6H` | when a vendor-reported device status is stale |
| `facilities.outbound.max-attempts` | `8` | reserved for the outbox drainer |
| `facilities.outbound.retry-base-seconds` | `10` | reserved for the outbox drainer |
| `facilities.outbound.retry-max-seconds` | `3600` | reserved for the outbox drainer |
