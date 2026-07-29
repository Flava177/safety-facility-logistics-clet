# S168 fuel — frontend gap register

What the **Fuel & Driver Logbooks** module needed from `gh.edu.clet.sfl.fleetlogistics.fuel` and did
not find, and what has since been done about it.

Every entry was confirmed against the running service on port 8093 — the controllers, the domain
records, `/v3/api-docs` and a live probe — not against the API inventory document.

**Status.** Eleven of the thirteen gaps are **closed** by the backend work on
`feat/fuel-backend-gaps`; two need nothing. Each entry keeps its original finding so the reasoning
survives, and states what changed.

Companion to `docs/fleet/S166_Frontend_Gap_Register.md`, same conventions.

---

## Summary

| # | Gap | Status |
| --- | --- | --- |
| 1 | No reconciliation read endpoint | **Closed** — `GET /transactions/{id}/reconciliations` |
| 2 | No import batch read endpoint | **Closed** — `GET /imports`, `GET /imports/{id}` |
| 3 | No `GET /policies/{id}` | **Closed** — plus `inForceOnly` on the register |
| 4 | No pagination on any fuel collection | **Closed** — every collection returns `FuelPageResponse<T>` |
| 5 | Anomalies filter on status only | **Closed** — eight more filters, including `dueBefore` |
| 6 | Dashboard published five figures | **Closed** — ten more indicators |
| 7 | `RESUBMITTED` undocumented | Open — document defect, code is correct |
| 8 | Three unreachable transaction statuses | Open — noted for clarity, no change needed |
| 9 | No fuel-side evidence upload | Resolved by design — evidence is fleet-wide |
| 10 | No history; audit search 500s | **Closed** — audit query fixed, four history endpoints |
| 11 | Policy overlap unenforced | **Closed** — refused with `FUEL_POLICY_PERIOD_OVERLAP` |
| 12 | Duplicate CSV upload 500s unmapped | **Closed** — `FUEL_IMPORT_ALREADY_PROCESSED` |
| 13 | CSV report needs a csv `Accept` | Resolved in the client — correct content negotiation |

Backend proof lives in `FuelGapClosureEndToEndTest` — 13 scenarios, one or more per closed gap.

---

## 1. No reconciliation read endpoint — **closed**

**Found.** `FuelApplicationService.reconcile` evaluated up to fourteen named rules and persisted
every outcome to `fleet_logistics.fuel_reconciliations.rule_results`, and **there was no read path**.
`FuelRepository` had no `findReconciliation` and no controller exposed one. A screen could report
that a transaction failed but never which rules it passed.

**Closed by** `GET /api/v1/fuel/transactions/{id}/reconciliations`, returning every run against the
transaction newest first as a `FuelReconciliation` — the policy and policy version it applied, the
outcome, the derived consumption, and the full rule result map. A rerun appends rather than amending,
so the history of decisions survives.

**In the UI.** The transaction detail screen lists every rule the run evaluated, passes and failures
alike, each with what it checks. The reconciliation screen names the failing rules per transaction
and the count that passed, with the policy version. Both used to infer failures from the anomaly
cases raised and say that the passes were unavailable.

---

## 2. No import batch read endpoint — **closed**

**Found.** `FuelImportService` wrote `fuel_import_batches` and `fuel_import_rows` on every upload,
with `error_code`, `error_message` and the raw record retained. **Neither table was readable.** The
upload response was the only view of the batch there would ever be, so an operator who navigated
away lost the record of which rows were rejected and why.

**Closed by** `GET /api/v1/fuel/imports` (paged batch headers) and `GET /api/v1/fuel/imports/{id}`
(the batch with every row). The service now builds a `FuelImportBatch` aggregate and persists it
through the repository rather than writing rows with a loose `JdbcTemplate`.

**In the UI.** The imports screen opens on the site's batch history, most recent first, and selects
the newest batch. Choosing a batch shows every row outcome with its retained error and a link to the
transaction an accepted row created.

---

## 3. No policy detail endpoint — **closed**

**Found.** `FuelPolicyController` exposed `POST /policies` and `GET /policies?siteCode=` only, so the
detail screen had to walk the actor's sites listing policies until it found the one it wanted. A deep
link into a policy at a site the picker had not selected was a dead end.

**Closed by** `GET /api/v1/fuel/policies/{id}`, plus `inForceOnly` on the register — an interval test
rather than a status filter, because an ACTIVE policy whose period has not started is not in force
and one with no end date runs until superseded. That distinction was not expressible before and it is
the whole point of an effective-dated policy.

---

## 4. No pagination on any fuel collection — **closed**

**Found.** Every fuel collection returned a bare `List<T>` with a `size` limit only — no `page`, no
`totalElements`, no stable sort. A register could only ever show a window, and a console could tell a
full register from the first hundred rows of it only by guessing from whether the list came back
full.

**Closed by** `FuelPageResponse<T>` on transactions, logbooks, anomalies, policies and imports,
carrying `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last` and `sort` —
identical in shape to the fleet envelope. `sort` is validated against a per-resource allow-list
rather than interpolated, and **every ordering ends in `id`**: rows sharing a sort value would
otherwise be free to swap places between requests, and a page boundary falling inside such a group
silently skips or repeats records. Page size is capped at 200.

**In the UI.** The registers are server-paged with a real total. `useClientWindow` and the
"showing the first N" warning that went with it are deleted.

---

## 5. Anomaly queue could not be filtered — **closed**

**Found.** `GET /anomalies` accepted `siteCode`, `status` and `size`. `dueBefore` existed on the
repository and was reachable only from the sweep scheduler. Assignee, type, severity and materiality
had no parameter at all, so the console filtered them in the browser — which made "breaching SLA"
mean "breaches among the first two hundred cases", precisely the queue an operator must not be given.

**Closed by** `type`, `severity`, `assignee` (contains-match), `unassigned`, `material`, `openOnly`,
`dueBefore` and `transactionId`. `openOnly` with `dueBefore` is the breaching-SLA queue as a real
query.

**In the UI.** Every filter and all four queue views reach the service. The counters above the table
come from the dashboard endpoint, which counts them across the site rather than across a page.

---

## 6. Dashboard published five figures — **closed**

**Found.** `GET /dashboard` returned `transactionCount`, `fuelVolume`, `fuelSpend`,
`reconciledCount`, `exceptionCount`, `sourceUpdatedAt` and a computed `stale`, all from a view over
`fuel_transactions` alone. No anomaly, logbook or import figures, so the console counted them from
whatever list it could fetch and captioned them as derived.

**Closed by** ten more indicators, counted by the service across the whole site:
`awaitingReconciliation`, `openAnomalies`, `anomaliesBreachingSla`, `materialOpenAnomalies`,
`unassignedAnomalies`, `pendingLogbookReviews`, `draftLogbooks`, `importBatches`,
`importBatchesWithErrors` and `lastImportAt`.

**In the UI.** The "counted from the records this console fetched" section is gone. The only
remaining derived panel is the spend trend, which is still bucketed by day in the browser because
there is no time-series endpoint — and still says so.

---

## 7. Logbook `RETURNED` resubmits to `RESUBMITTED`, not `DRAFT` — open

**Documented.** `S168_Fuel_Domain_And_State_Model.md`: "review may move to `RETURNED`, then back to
`DRAFT`".

**Found.** `DriverLogbook.submit` sends a `RETURNED` record to **`RESUBMITTED`**, a ninth status the
document does not mention. `startReview` accepts both `SUBMITTED` and `RESUBMITTED`, so the loop
closes correctly — the documented `DRAFT` step simply never happens, and
`FuelMandatoryScenariosEndToEndTest#return_resubmit_and_approve_a_logbook` asserts the implemented
behaviour.

**Still open.** The code is right and the document is wrong. The UI follows the code. Correcting
`S168_Fuel_Domain_And_State_Model.md` is a documentation change nobody has made yet.

---

## 8. Transaction statuses `VALIDATING`, `MATCHED` and `REJECTED` are unreachable — open

**Found.** `capture` writes `RECEIVED`; `reconcile` writes `RECONCILED` or `EXCEPTION`;
`voidTransaction` writes `VOIDED`. Nothing writes the other three.

**Still open, deliberately.** Removing enum values is a breaking change to stored data, and
implementing the intermediate transitions is a design decision rather than a defect fix. The UI
offers all seven in the status filter — the enum is the contract — and the lifecycle panel marks the
three as not currently produced.

---

## 9. No fuel-side evidence upload — resolved by design

Fuel records reference evidence by UUID only. Registration lives in the fleet module
(`POST /api/v1/fleet/evidence`) and is not re-exposed under `/api/v1/fuel`. Every evidence field in
the fuel UI is a reference input pointing at Evidence & audit, exactly as the fleet workflow closure
dialog does. Recorded so the next reader does not go looking for a fuel-side upload.

---

## 10. No per-record history, and `GET /fleet/audit/records` returned 500 — **closed**

**Found.** No fuel aggregate had a history endpoint. Every fuel state change *was* recorded through
`AuditPort` with a before and after image, and the fleet module already exposed a search over those
records — but **that endpoint returned HTTP 500 on every call**, with or without parameters:

```
InvalidDataAccessResourceUsageException: JDBC exception executing SQL
  [ERROR: could not determine data type of parameter $11]
  ... where (? is null or are1_0.action=?) ...
```

The derived JPQL expressed each optional filter as `(:param is null or column = :param)`, which
Hibernate renders as `(? is null or column = ?)` — and PostgreSQL cannot infer a type for a parameter
whose only appearance is a bare `IS NULL` test. Probed four ways, all five hundred. **Not confined to
fuel: the Fleet module's Evidence & audit screen calls the same endpoint.**

**Closed by** two changes. `AuditRecordSearch` / `AuditRecordSearchImpl` replace the derived query
with a Criteria implementation that adds a predicate only for a filter actually supplied — an absent
filter contributes no SQL, so there is no untyped null to infer, and the query can use its indexes
instead of being wrapped in `OR` tests that defeat them. And four history endpoints:
`GET /api/v1/fuel/{transactions|logbooks|anomalies|policies}/{id}/history`, each authorised against
the record itself so a caller cannot enumerate another site's history through them.

Verified: all four filter combinations return 200, and **this also repairs the Fleet Evidence & audit
screen**, which was broken for the same reason.

**In the UI.** The three detail screens show the real recorded transitions in chain order with the
actor who made each, and the status the record moved to, read from the audit entry's post-image. The
reconstructed-from-timestamps timeline and its caveat are gone.

---

## 11. "No overlapping active policy" documented but unenforced — **closed**

**Found.** The domain document stated the invariant. `FuelPolicy`'s constructor could not check it —
a record knows nothing about its siblings — `createPolicy` did not check either, and there was no
database constraint. Two ACTIVE policies with overlapping periods could be created for one site and
the second was silently accepted. With an overlap, `findApplicablePolicy` returns whichever row the
ordering surfaces, so the rules a transaction is judged against and the policy version stamped on its
reconciliation stop being reproducible.

**Closed by** `findOverlappingActivePolicies` and a check inside the same transaction that writes the
record, raising `FUEL_POLICY_PERIOD_OVERLAP` (409) with the conflicting policies in `details`.
Periods are half-open, so a successor beginning exactly where its predecessor ends is legal.

**In the UI.** The client-side overlap warning is gone — the service refuses it, and a dialog that
warns about something the service enforces is telling the operator half a story. The dialog states
the rule; a violation surfaces as the service's own error.

---

## 12. A duplicate CSV upload returned an unmapped 500 — **closed**

**Found.** `uq_fuel_import_file` on `(site_code, source_system, file_hash)` refused the re-import
correctly, but the violation was raised by a plain `JdbcTemplate.update` at the *end* of the import,
after every row had been captured, and `FleetApiExceptionHandler` had no handler for
`DataIntegrityViolationException`. The response was Spring's default body, not the SFL envelope. No
data was corrupted — capture is idempotent on `(siteCode, sourceSystem, providerTransactionId)` — but
the operator got a bare 500.

**Closed by** a hash check *before* any row is processed, raising `FUEL_IMPORT_ALREADY_PROCESSED`
(409) naming the batch that already holds the file, with a `DuplicateKeyException` catch in the
repository as a second line of defence against a race.

---

## 13. The CSV report refuses `Accept: application/json` — resolved in the client

`GET /api/v1/fuel/reports/transactions.csv` is declared `produces="text/csv"`. Spring intersects that
with the request's `Accept`, so a client sending the console's standard `Accept: application/json`
got **406 Not Acceptable** and the report was never generated. Found by probing with the exact
headers the shared API client sends.

Not a service defect — it is correct content negotiation. `downloadFile` in `shared/api/client.ts`
sends `Accept: text/csv, application/json`: the first so the report is produced, the second so an
authorisation refusal, which comes back as a JSON envelope, is not itself rejected for the wrong
content type. Recorded because it is a trap for any other client.

---

## What is still worth doing

- **Gap 7** — correct `S168_Fuel_Domain_And_State_Model.md` to describe `RESUBMITTED`.
- **Gap 8** — decide whether `VALIDATING`, `MATCHED` and `REJECTED` should be implemented or removed.
- **A time-series endpoint** for fuel spend and volume by day, so the dashboard's one remaining
  derived panel can stop bucketing in the browser.
- **An anomaly aggregation endpoint** (counts by type) so the by-type chart stops reading a page.
- **`GET /api/v1/fuel/imports/{id}/rows`**, paged, for a batch with thousands of rows; the detail
  read currently returns them all.
