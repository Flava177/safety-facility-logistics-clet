# S168_fuel Domain and State Model

## Aggregates

| Aggregate | Identity and invariant |
|---|---|
| `FuelPolicy` | Site/effective period/version; positive limits; no overlapping active policy for the same scope |
| `FuelTransaction` | Provider/manual identity, vehicle/driver/trip references, decimal quantity/cost, raw odometer observation, receipt reference and immutable source provenance |
| `DriverLogbook` | Driver-owned journey record; end odometer never precedes start; approved records are locked |
| `FuelReconciliation` | Immutable policy-versioned rule results that reproduce a decision |
| `FuelAnomalyCase` | One stable case per transaction/rule occurrence; accountable assignment, SLA, explanation, evidence, decision and closure |
| `FuelImportBatch` | File-level result with idempotent row outcomes and retained validation errors |

## State machines

`FuelTransaction`: `RECEIVED -> VALIDATING -> MATCHED -> RECONCILED|EXCEPTION|REJECTED`; any non-void
state may become `VOIDED` only through a privileged reasoned action.

`DriverLogbook`: `DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED`; review may move to `RETURNED`, and
a returned logbook resubmits to **`RESUBMITTED`**, not back to `DRAFT`. `APPROVED -> REOPENED` is
privileged; draft/submitted records may be `CANCELLED` with reason.

`RESUBMITTED` exists so a reviewer can tell a first submission from a corrected one — a returned
logbook that reappeared as `DRAFT` would be indistinguishable from one never submitted, and the
reviewer would have no way to know their own comments had been acted on. `startReview` accepts both
`SUBMITTED` and `RESUBMITTED`, so the review path is the same either way.

*This paragraph said "back to `DRAFT`" until 29 July 2026. The code was correct
(`DriverLogbook.submit` returns `RESUBMITTED` when the current state is `RETURNED`); the document was
wrong. Recorded as gap 7 of the frontend register, now closed by correcting this text.*

`FuelAnomalyCase`: `DETECTED -> ASSIGNED -> UNDER_REVIEW -> AWAITING_EXPLANATION ->
EXPLANATION_RECEIVED -> APPROVED|REJECTED|ESCALATED -> CLOSED`. Hold, reassignment, cancellation and
reopen are explicit privileged operations.

## Odometer authority

Fuel and logbooks retain raw observations. The Fleet vehicle aggregate owns the accepted reading. Only a
newer plausible reading advances it through `FleetOdometerPort`; regressions and implausible jumps are anomaly
inputs and never overwrite Fleet state.
