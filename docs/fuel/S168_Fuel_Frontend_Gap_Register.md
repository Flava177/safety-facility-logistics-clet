# S168 fuel — frontend gap register

What the **Fuel & Driver Logbooks** module needed from `gh.edu.clet.sfl.fleetlogistics.fuel` and did not
find. Every entry was confirmed against the running service on port 8093 — the controllers, the
domain records, `/v3/api-docs` and a live probe — not against the API inventory document.

Nothing in this register is mocked. Where an endpoint is missing the screen says so in place, and the
panel that would have used it is either absent or clearly labelled as derived from records the
service really returned.

Companion to `docs/fleet/S166_Frontend_Gap_Register.md`, same conventions.

---

## 1. No reconciliation read endpoint

**Wanted.** The reconciliation screen was specified to show "policy-versioned rule results" and
"matched, exception and rejected outcomes".

**Found.** `FuelApplicationService.reconcile` evaluates twelve named rules and persists them —
`fleet_logistics.fuel_reconciliations(id, transaction_id, policy_id, policy_version, outcome,
calculated_consumption, evaluated_at, evaluated_by, rule_results JSONB, correlation_id)` — through
`FuelRepository.saveReconciliation`. **There is no read path.** `FuelRepository` has no
`findReconciliation`, and no controller exposes one. `S168_Fuel_API_Inventory.md` lists
`POST /reconciliations/run` and `GET /reconciliations/{transactionId}`; neither exists. The only
reconciliation entry point is `POST /api/v1/fuel/transactions/{id}/reconcile`.

**Rules evaluated but not readable:** `MAX_PER_TRANSACTION`, `TANK_CAPACITY`, `FUEL_PRODUCT`,
`APPROVED_VENDOR`, `DRIVER_ELIGIBLE`, `VEHICLE_OPERATIONAL`, `TRIP_MATCH`,
`ODOMETER_NON_REGRESSION`, `ODOMETER_JUMP`, `RECEIPT`, `CONSUMPTION_RANGE`, `COST_VARIANCE`,
`LOGBOOK_MATCH`, `REPEATED_PATTERN`.

**What the UI does instead.** The reconciliation screen runs reconciliation and reports the outcome
the service returns on the transaction (`RECONCILED` / `EXCEPTION`), then reconstructs which rules
failed from the anomaly cases the same run raised — `FuelAnomalyCase.detectedRules` carries the rule
name, and the anomaly is readable. Rules that **passed** cannot be shown, and the page says so.
`calculated_consumption` and `policy_version` are not shown at all, because they are only in the
unreadable row.

**To close.** `GET /api/v1/fuel/reconciliations/{transactionId}` returning the stored rows newest
first, with `ruleResults` deserialised.

---

## 2. No import batch read endpoint

**Wanted.** "Batch detail, accepted/rejected row outcomes, validation errors per row" as a screen an
operator can return to.

**Found.** `POST /api/v1/fuel/imports/csv` returns `ImportResult { batchId, totalRows, acceptedRows,
rejectedRows, rows[] }` and `FuelImportService` writes both `fuel_import_batches` and
`fuel_import_rows` (with `error_code`, `error_message`, `raw_record`). **Neither table is readable.**
There is no `GET /imports/{id}` and no `GET /imports`, despite the inventory document listing the
former.

**What the UI does instead.** The CSV import screen shows the full batch result — headers, totals and
every row outcome with its per-row error — from the upload response, held in page state for the
session. The screen states plainly that the batch cannot be reopened once the page is left, and links
each accepted row to the transaction it created (those *are* readable).

**To close.** `GET /api/v1/fuel/imports?siteCode=` and `GET /api/v1/fuel/imports/{batchId}`.

---

## 3. No policy detail endpoint

**Wanted.** `GET /policies/{id}`, per the inventory document.

**Found.** `FuelPolicyController` exposes `POST /policies` and `GET /policies?siteCode=` only.

**What the UI does instead.** The policy detail screen selects the policy out of the site's policy
list, which returns the complete `FuelPolicy` record — every field the detail screen needs is already
there, so this costs one extra list fetch rather than a missing capability. A deep link to a policy
whose site is not in the actor's scope shows a not-found state rather than a broken screen.

**To close.** `GET /api/v1/fuel/policies/{id}`.

---

## 4. No pagination on any fuel collection

**Wanted.** Server-paginated registers, as the Fleet module has throughout.

**Found.** Every fuel collection returns a bare `List<T>` with a `size` limit only:

| Endpoint | Paging parameters |
| --- | --- |
| `GET /transactions` | `size` (default 100) — no `page`, no `sort` |
| `GET /logbooks` | `size` (default 100) |
| `GET /anomalies` | `size` (default 100) |
| `GET /policies` | none at all |

There is no `PageResponse` envelope, no `totalElements` and no stable sort key on the fuel side —
`JdbcFuelRepository` orders by `occurred_at DESC` / `created_at DESC` and applies `LIMIT`.

**What the UI does instead.** The three registers page **client-side** over the window the service
returned, and the footer says exactly that: "Showing N records returned for this filter (service
limit S)". When the returned count equals the limit the table shows a warning that the window is
truncated and the filters should be narrowed. No screen presents a page count as if it were the whole
register.

**To close.** Adopt the fleet `PageResponse<T>` envelope with `page`/`size`/`sort` on all four.

---

## 5. Anomaly queue cannot be filtered by assignee, type or severity

**Wanted.** A workable anomaly queue — "assign … show SLA status and blockers".

**Found.** `GET /anomalies` accepts `siteCode`, `status` and `size` only.
`FuelRepository.findAnomalies(sites, status, dueBefore, limit)` supports a `dueBefore` cutoff and
`FuelApplicationService.anomalies(...)` hard-codes it to `null`; neither `dueBefore` nor an assignee,
type, severity or materiality filter is reachable over HTTP. The sweep scheduler is the only caller
that uses `dueBefore`.

**What the UI does instead.** Assignee, type, severity, materiality and "SLA breached" are filtered
client-side over the returned window, and each such control is marked as filtering the loaded
records rather than the query. Site and status go to the service.

**To close.** Add `assignee`, `type`, `severity`, `material` and `dueBefore` query parameters.

---

## 6. Dashboard exposes five figures, not the eight the module needs

**Wanted.** Site totals, fuel spend, fuel quantity, transaction freshness, reconciliation status,
open anomaly cases, pending logbook reviews and CSV import status.

**Found.** `GET /dashboard?siteCode=` returns exactly:

```
transactionCount, fuelVolume, fuelSpend, reconciledCount, exceptionCount, sourceUpdatedAt, stale
```

`stale` is computed in the service (`sourceUpdatedAt` older than 15 minutes); the rest come from the
`fuel_dashboard_summary` view over `fuel_transactions` alone. There are **no** anomaly, logbook or
import figures in it, and no per-site breakdown when the actor holds several sites — `siteCode` is
required, so the dashboard is single-site by construction.

**What the UI does instead.** The five service figures plus `stale` are presented as the snapshot,
with the snapshot time in the page header. Open anomaly cases and pending logbook reviews are counted
from the anomaly and logbook list endpoints and are captioned "derived from the records this filter
returned" — they are not presented as service indicators. CSV import status has no source at all
(gap 2) and is therefore absent from the dashboard; the import screen is linked instead.

**To close.** Extend the dashboard payload with `openAnomalies`, `anomaliesBreachingSla`,
`pendingLogbookReviews` and `lastImportAt`, and add a drilldown endpoint in the shape of
`GET /api/v1/fleet/dashboards/operations/drilldowns/{indicator}`.

---

## 7. Logbook `RETURNED` resubmits to `RESUBMITTED`, not `DRAFT`

**Wanted (per S168_Fuel_Domain_And_State_Model.md and the build brief).** "review may move to
`RETURNED`, then back to `DRAFT`".

**Found.** `DriverLogbook.submit` sends a `RETURNED` record to **`RESUBMITTED`**, a ninth status that
the state-model document does not mention:

```java
Status next = status == Status.RETURNED ? Status.RESUBMITTED : Status.SUBMITTED;
```

`startReview` accepts `SUBMITTED` and `RESUBMITTED`, so the loop closes correctly — the documented
`DRAFT` step simply never happens. `FuelMandatoryScenariosEndToEndTest#return_resubmit_and_approve_a_logbook`
asserts the implemented behaviour.

**What the UI does.** Follows the implementation: a returned logbook offers "Resubmit", the timeline
and status chip show `RESUBMITTED`, and the reviewer's queue treats `SUBMITTED` and `RESUBMITTED`
alike. **The document is wrong, not the code.**

**To close.** Correct `S168_Fuel_Domain_And_State_Model.md`.

---

## 8. Transaction statuses `VALIDATING`, `MATCHED` and `REJECTED` are unreachable

**Found.** `FuelTransaction.Status` declares seven values. `capture` writes `RECEIVED`; `reconcile`
writes `RECONCILED` or `EXCEPTION`; `voidTransaction` writes `VOIDED`. Nothing in the service ever
writes `VALIDATING`, `MATCHED` or `REJECTED`.

**What the UI does.** Offers all seven in the status filter, because the enum is the contract and a
record could carry any of them, but the workflow diagram on the transaction detail screen marks the
three as not currently produced rather than implying an operator can reach them.

**To close.** Either implement the intermediate transitions or remove the values from the enum.

---

## 9. No evidence upload from the fuel module

**Found.** Fuel records reference evidence by UUID only — `receiptEvidenceId` on a transaction,
`evidenceId` on a logbook and on an anomaly closure. Registration lives in the fleet module
(`POST /api/v1/fleet/evidence`) and is not re-exposed under `/api/v1/fuel`.

**What the UI does.** Every evidence field is a reference input with a note pointing at Evidence &
audit, exactly as the Fleet workflow closure dialog does. It never fabricates an identifier.

**To close.** Nothing, if evidence stays a fleet-wide concern — this is recorded so the next reader
does not go looking for a fuel-side upload.

---

## 10. No per-record history, and `GET /fleet/audit/records` returns 500

**Wanted.** A transition timeline on the transaction, logbook and anomaly detail screens — the
equivalent of the Fleet module's `GET /workflow-items/{id}/transitions`.

**Found.** No fuel aggregate has a history endpoint. Every fuel state change **is** recorded, through
`AuditPort.record(...)` with a `beforeValue`/`afterValue` pair, and the fleet module already exposes
a search over those records at `GET /api/v1/fleet/audit/records?resourceType=&resourceId=`. The fuel
resource types are `FuelTransaction`, `DriverLogbook`, `FuelAnomalyCase` and `FuelPolicy`.

**That endpoint is currently broken.** Every call returns HTTP 500 against the development Postgres,
with or without parameters:

```
InvalidDataAccessResourceUsageException: JDBC exception executing SQL
  [ERROR: could not determine data type of parameter $11]
  ... where (? is null or are1_0.action=?) ...
```

The generated query compares a nullable enum parameter without a cast, and pgjdbc cannot infer the
type. Probed four ways — no parameters, `resourceType` only, `resourceId` only, and with
`action=CREATE` — all five hundred. **This is pre-existing and not confined to fuel: the Fleet
module's Evidence & audit screen calls the same endpoint.**

**What the UI does instead.** Each detail screen builds its timeline from the record itself, which
carries real provenance and nothing invented: `metadata.createdBy`/`createdAt`,
`metadata.lastModifiedBy`/`lastModifiedAt`/`version`/`sourceChannel`, plus the aggregate's own
milestones — `submittedAt` and `approvedAt` on a logbook, `ingestionTimestamp` on a transaction, and
the current `reviewComment` / `transitionReason` / `closureReason`. Every such panel is captioned
"reconstructed from the record's own provenance — the service exposes no transition history for fuel
records", so nobody mistakes it for the audit trail. Intermediate transitions are not recoverable
this way and the screens do not pretend otherwise.

**To close.** Two things, independently: fix the audit query's enum parameter binding (a cast, or a
pair of derived queries), and add `GET /api/v1/fuel/{aggregate}/{id}/history` so a fuel record's
transitions do not depend on a cross-module audit search.

---

## 11. The "no overlapping active policy" invariant is documented but not enforced

**Documented.** `S168_Fuel_Domain_And_State_Model.md` states the `FuelPolicy` invariant as
"Site/effective period/version; positive limits; **no overlapping active policy for the same
scope**".

**Found.** `FuelPolicy`'s compact constructor checks the limits and that `effectiveTo` follows
`effectiveFrom`. It does not — and cannot — check overlap, because a record knows nothing about its
siblings. `FuelApplicationService.createPolicy` does not check either: it constructs the policy with
`Status.ACTIVE` and saves. There is no unique constraint on the table. **Two ACTIVE policies with
overlapping effective periods can be created for one site, and the second is silently accepted.**

This matters because `findApplicablePolicy` is what reconciliation reads, and with an overlap the
rule set a transaction is judged against depends on which row the query happens to return.

**What the UI does instead.** The create-policy dialog fetches the site's existing policies and warns
— before submission — when the period being created overlaps an ACTIVE one, naming it. The warning
is explicitly labelled a console check, and it does **not** block submission, because the service
will accept the record and the console must not pretend to a veto it does not have. The policy
register also flags every overlapping ACTIVE pair it can see.

**To close.** Enforce it in `createPolicy` (reject with a domain error) or add a database exclusion
constraint on `(site_code, effective period)` where `status = 'ACTIVE'`.

---

## 12. A duplicate CSV upload returns an unmapped 500

**Found.** `fuel_import_batches` carries `CONSTRAINT uq_fuel_import_file UNIQUE (site_code,
source_system, file_hash)`, which is right — the same file should not import twice. But
`FuelImportService.importCsv` inserts the batch with a plain `JdbcTemplate.update` and nothing
catches the violation, so it surfaces as a `DataIntegrityViolationException`.
`FleetApiExceptionHandler` has no handler for it, so the response is Spring's default body rather
than the SFL envelope:

```
POST /api/v1/fuel/imports/csv        (same file, same site, same source system)
{"timestamp":"…","status":500,"error":"Internal Server Error","path":"/api/v1/fuel/imports/csv"}
```

Verified against the running service. **No data is corrupted** — the rows are re-captured first, and
capture is idempotent on `(siteCode, sourceSystem, providerTransactionId)`, so the original
transactions are returned and nothing is duplicated. The batch record is simply not written and the
operator gets a bare 500.

**What the UI does instead.** `FleetApiError.fromUnmappedFailure` already turns a non-envelope
failure into readable wording, so this reaches the operator as a message naming the path rather than
a blank screen. On top of that, the import dialog and the imports screen both warn, before the
upload, that re-uploading a file fails with an error that will not explain itself, and that nothing
is duplicated when it does.

**To close.** Catch `DuplicateKeyException` in `FuelImportService` and raise a domain error —
`FUEL_IMPORT_ALREADY_PROCESSED` or similar — so the envelope carries the reason.

---

## 13. The CSV report refuses `Accept: application/json`

**Found.** `GET /api/v1/fuel/reports/transactions.csv` is declared `produces="text/csv"`. Spring
intersects that with the request's `Accept`, so a client sending the console's standard
`Accept: application/json` gets **406 Not Acceptable** and the report is never generated. This was
found by probing the endpoint with the exact headers the shared API client sends.

**What the UI does.** `downloadFile` in `shared/api/client.ts` sends
`Accept: text/csv, application/json` — the first so the report is produced, the second so an
authorisation refusal, which comes back as a JSON envelope, is not itself rejected for the wrong
content type. Confirmed 200 with `Content-Disposition: attachment; filename=fuel-transactions-<site>.csv`.

**Not a service defect**, strictly — it is correct content negotiation. Recorded because it is a
trap for any other client, and because the report is also capped at 500 rows service-side and takes
no filters beyond the site, which the export button says out loud.

---

## Summary

| Gap | Blocks | Severity |
| --- | --- | --- |
| 1. No reconciliation read | Per-rule reconciliation results | High |
| 2. No import batch read | Returning to a past import | High |
| 3. No `GET /policies/{id}` | Nothing — worked around | Low |
| 4. No pagination | Registers beyond the size window | High |
| 5. No anomaly filters | Server-side queue filtering | Medium |
| 6. Thin dashboard payload | Anomaly/logbook/import indicators | Medium |
| 7. `RESUBMITTED` undocumented | Nothing — document defect | Low |
| 8. Three unreachable statuses | Nothing — noted for clarity | Low |
| 9. No fuel-side evidence upload | Nothing — by design | Low |
| 10. No history; audit search 500s | Real transition timelines | High |
| 11. Policy overlap unenforced | Reproducible rule selection | Medium |
| 12. Duplicate import 500s unmapped | A readable "already imported" | Medium |
| 13. CSV report needs a csv `Accept` | Nothing — worked around | Low |
