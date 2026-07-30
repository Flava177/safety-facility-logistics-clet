# S152 CAFM / IWMS — Domain and State Model

- Service: `sfl-facilities-service`, schema `facilities`
- Requirements: `SRS-SFL-S152-01` … `-05`, NFR 23.3, NFR 23.8

S152 is the **host platform for IFIMP**. S153 maintenance and S159 room booking are sub-systems of it
(C9 mapping: S153 is a "sub-system of CAFM (S152)"), so the model below is designed as much for what
those modules will ask of it as for what CAFM does itself.

---

## 1. The estate

```
Site ──< Building ──< Floor ──< Space
 │                                │
 ├──< Zone ──< ZoneMembership >────┤ (also Building, Floor, Device)
 ├──< DeviceReference ─────────────┤
 └──< FacilityAsset ───────────────┘
```

| Aggregate | Identity | Owns |
|---|---|---|
| `Site` | `siteCode`, unique platform-wide | Operating mode; the unit of authorisation scope |
| `Building` | `buildingCode`, unique per site | — |
| `FacilityFloor` | `floorCode`, unique per building | Signed, nullable `levelNumber` |
| `FacilityRoom` | `roomCode`, unique **per site** | Space attributes, derived readiness, examination lock |
| `Zone` | `zoneCode`, unique per site | Nesting via `parentZoneId`; membership |
| `DeviceReference` | `deviceCode`, unique per site | Vendor identity and last reported status |
| `FacilityAsset` | `assetCode`, unique per site | Condition, service schedule, custody |

**Room code is unique per site, not per building.** A room code is how an operator refers to a space
over a radio; two "HALL-A"s on one site would be ambiguous whichever buildings they sat in.

### Why `FacilityRoom` rather than `Space`

The type keeps its name. Six JPA entities, the maintenance module, the existing tests and a 1 400-line
dashboard page all say "room"; renaming the type without renaming the concept everywhere would buy
nothing and cost a large diff. The API and this document say "space" where the domain means one.

### `FacilityAsset` versus `DeviceReference` versus AVAMP-Lite

Three things that all look like "an asset", and conflating any two of them causes real trouble:

| | Owns | Example |
|---|---|---|
| `DeviceReference` (S152) | **Integration endpoint identity** — something a vendor system reports on | a CCTV camera, a card reader |
| `FacilityAsset` (S152) | **Fixed plant maintenance is raised against** | a chiller, a lift, a generator |
| AVAMP-Lite asset reference (separate service) | **Cross-programme asset identity** | a vehicle, a laptop, a projector |

A fire panel is a device *and* an asset, which is why `FacilityAsset.deviceReferenceId` exists.
`FacilityAsset.assetReferenceId` points at AVAMP by value — no foreign key, no read of another
service's schema.

---

## 2. System-managed fields

`SRS-SFL-S152-01` requires, on every operational record: *"Record UUID/ULID, site scope, created
by/date, last modified by/date, version, source channel and audit correlation ID."*

Carried by the `RecordMetadata` value object, embedded in all seven aggregates:

| Field | Note |
|---|---|
| `createdBy` / `createdAt` | never changes |
| `lastModifiedBy` / `lastModifiedAt` | set on every mutation |
| `version` | **incremented by every mutation** — the optimistic lock |
| `sourceChannel` | `WEB`, `MOBILE`, `INTEGRATION`, `SCHEDULER`, `SYSTEM` |
| `correlationId` | links the record to its audit entry and its outbox event |

Every mutation goes through `RecordMetadata.modifiedBy`, which increments the version — so an
aggregate cannot be changed without its provenance moving with it, and forgetting to bump the version
is not something a developer can do by omission.

---

## 3. Record lifecycle — `SRS-SFL-S152-01`

```
        ┌──────────────────────────┐
        ▼                          │
   ┌─────────┐  ┌──────────┐  ┌───────────┐
   │ ACTIVE  │◄─┤ INACTIVE │◄─┤ SUSPENDED │
   └────┬────┘  └────┬─────┘  └─────┬─────┘
        │            │              │
        └────────────┴──────────────┘
                     ▼
               ┌──────────┐
               │ ARCHIVED │  (terminal)
               └──────────┘
```

- **`ARCHIVED` is terminal.** Archival is the closest thing this platform has to a delete, and §21.2
  requires records used for examination continuity to be protected from deletion. Un-archiving would
  let a record leave and re-enter the estate with its history implying it never left.
- **`SUSPENDED` is reversible** — a flooded hall, a floor under works — and has to come back.
- An archived record **releases its identifier**, which is what lets a demolished building's code be
  given to its replacement.

---

## 4. Operating mode — NFR 23.3

```
   ROUTINE ⇄ EXAMINATION
```

*"Platform mode changes, such as Routine to Examination Mode, must be explicit, audited and reversible
only by authorised roles."*

- Mode lives on the **site**, not the space: an examination is declared over a centre, and every space
  beneath inherits the stricter rules.
- Requires `FACILITIES_OPERATING_MODE_CHANGE` — held by `CENTRE_MANAGER`, `COMMAND_ROLE`,
  `FACILITIES_DIRECTOR` and the administrators. Notably **not** by `FACILITIES_MANAGER`: declaring an
  examination is a centre-level operational decision, not an estate-maintenance one.
- A no-op change is refused, so the audit trail never records a decision nobody made.
- What it changes: which readiness checklist applies, which staleness threshold the dashboard uses, and
  whether examination-readiness risk is reported separately.

---

## 5. Space readiness — the heart of S152

### Status

| Status | Meaning | Bookable? | Examination? |
|---|---|---|---|
| `UNKNOWN` | never assessed | yes, if flagged bookable | no |
| `READY` | assessed, nothing open | yes | yes |
| `DEGRADED` | a major or minor blocker is open | yes | no |
| `BLOCKED` | a **critical** blocker is open | no | no |

`UNKNOWN` is distinct from `READY` and the distinction matters: an unassessed examination hall is not
a passed one. `DEGRADED` still books — a hall with one failed projector is usable, and refusing it
would be worse than warning about it. Examination use requires `READY` outright, because "probably
fine" is not a standard an examination centre can run on.

### The rule

> **A space cannot be marked READY while a critical blocker is open.**

`SRS-SFL-S152-01`. Enforced in exactly two places — the derived path (`submitAssessment`) and the
manual path (`setReadinessDirectly`) — both routing through `ReadinessPolicy`, so there is no third
way around it. The manual override overrides the *process*, never the *invariant*.

### Evaluation

`ReadinessPolicy.evaluate` is a pure function of the open blockers. First match wins:

1. any open `CRITICAL` → **`BLOCKED`**
2. any open `MAJOR` or `MINOR` → **`DEGRADED`**
3. never assessed → **`UNKNOWN`**
4. otherwise → **`READY`** (advisory blockers are reported, not acted on)

The **score** is the weighted percentage of checklist items passed, reported alongside the status
rather than driving it. A room can score 95% and still be `BLOCKED` because the one thing that failed
was the fire door — which is exactly why severity, not score, decides.

### Blockers

| Source | Raised by | Cleared by |
|---|---|---|
| `CHECKLIST_ITEM` | a failed item on an assessment | resolution, or superseded by the next assessment |
| `ASSET` | an asset becoming degraded / out of service | the asset returning to service |
| `WORK_ORDER` | reserved for S153 | — |
| `MANUAL` | an officer, for what no checklist covers | resolution |

Severity from an asset combines criticality and status — criticality sets the ceiling, status sets how
much of it applies:

| Asset criticality | Out of service | Degraded / under maintenance |
|---|---|---|
| `CRITICAL` | `CRITICAL` | `MAJOR` |
| `HIGH` | `MAJOR` | `MINOR` |
| `MEDIUM` | `MINOR` | `MINOR` |
| `LOW` | `ADVISORY` | `ADVISORY` |

A low-criticality asset never rises above advisory however broken it is — a failed noticeboard light
does not stop an examination.

Resolution **requires a note**. A blocker cleared with no explanation leaves a reviewer unable to tell
a fix from a dismissal, and the database constraint enforces it as well as the domain.

### Assessment lifecycle

```
  submit ─► snapshot answers ─► supersede the previous assessment's blockers
         ─► raise a blocker per failed item ─► evaluate ─► write status back to the space
```

All in **one transaction**: a space whose assessment committed and whose blockers did not would report
itself ready on a checklist it failed.

Assessments are **append-only** (database trigger). An assessment is a statement about a space at a
moment, signed by a named assessor; amending one after the fact destroys the only thing it is good for.
A changed space gets a new one.

### Checklists — NFR 23.8

Runtime-configurable and **versioned**. Applicability is by `spaceType` and `operatingMode`, both
nullable meaning "any"; the most specific applicable checklist wins, ties broken deterministically by
code. An assessment records the checklist version it was taken against, so a result from March can
still be read against the questions that were asked in March.

`severityIfFailed` is declared on the **item**, not chosen by the assessor. An assessor records pass or
fail; how much a failure counts was decided when the checklist was approved. That is what keeps two
officers assessing the same hall to the same standard.

### The examination lock — NFR 23.3

```
   unlocked ──lock (FACILITIES_READINESS_OVERRIDE)──► locked
       ▲                                                │
       └──────────── unlock (same permission) ──────────┘
```

While locked, a space refuses **attribute and lifecycle changes**. It still accepts a readiness
outcome: the lock protects the space's definition, not the assessment of it — an examination hall being
reassessed mid-lock is exactly what should happen when something fails.

---

## 6. Facility asset condition

```
   OPERATIONAL ⇄ DEGRADED ⇄ UNDER_MAINTENANCE ⇄ OUT_OF_SERVICE
                              │
                              ▼
                       DECOMMISSIONED
```

Separate from the record lifecycle: an asset can be `ACTIVE` as a record and `OUT_OF_SERVICE` as a
machine. Conflating them would make "we no longer track this chiller" indistinguishable from "this
chiller is broken". `DECOMMISSIONED` raises no blockers — it is retired in place, kept for history.

`serviceDueOn` is derived from `serviceIntervalDays` counted from the last service, or from
installation where there has never been one. An asset installed and never serviced **is** due;
treating "never serviced" as "not due" is how a generator goes three years without a look.

---

## 7. Audit — `SRS-SFL-S152-03`

Append-only, hash-chained: `hash = SHA-256(previousHash ‖ canonical(record))`.

- Canonical form is a fixed field order separated by ASCII unit separator (0x1F), a character that
  cannot appear in any field value. An absent field and an empty one are distinguished by a record
  separator (0x1E), so `(null, "")` and `("", null)` hash differently.
- Writers take a **pessimistic lock on a single chain-head row** before appending. Without it two
  writers both claim sequence *n*: one fails the unique constraint and the other commits a record whose
  predecessor never existed, which replays later as tampering that never happened.
- Replay detects a mutated record (its hash no longer matches), a removed one (the sequence gaps) and a
  reordered one (the previous-hash link breaks).
- **Denials are audited.** `AUTHORIZATION_DENIED` goes onto the chain before the 403 is thrown — a
  refused attempt to read another site's estate is exactly what a compliance review looks for. It is
  written `REQUIRES_NEW` so it survives the rollback of the refusal it documents.

Two storage decisions the chain depends on, both found by replaying against a real PostgreSQL:

1. **Payloads are `TEXT`, not `JSONB`.** jsonb reorders object keys and strips whitespace, so what
   comes back is not what went in — and every record replays as tampered. Byte fidelity beats the
   ability to query inside the payload.
2. **Timestamps are truncated to microseconds before hashing.** PostgreSQL `timestamptz` stores
   microseconds; a Java `Instant` carries nanoseconds. Hashing the untruncated value and reading back
   the truncated one breaks every record.

---

## 8. What S153 and S159 inherit

The point of building S152 first. Neither module rebuilds any of this:

| Platform capability | Where |
|---|---|
| Hash-chained audit | `shared.application.port.AuditPort` |
| Idempotency store | `shared.application.port.IdempotencyPort` |
| Runtime configuration | `shared.application.port.RuntimeConfigurationPort` |
| Permission matrix and site scoping | `shared.application.FacilitiesAuthorization` |
| Actor resolution (JWT or headers) | `shared.api.FacilitiesActorResolver` |
| Correlation ID propagation | `shared.api.CorrelationIdFilter` |
| Uniform error envelope | `shared.api.FacilitiesApiExceptionHandler` |
| Record metadata and lifecycle | `shared.domain.model` |

And the estate itself: **S153 raises work orders against `FacilityAsset` and `FacilityRoom`; S159 books
`FacilityRoom` and reads its readiness.** V6 adds the nullable `facility_asset_id` and `room_id`
columns to `facility_faults` and `work_orders` as the S153 attachment point.
