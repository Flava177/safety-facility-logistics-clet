# S171 Domain and State Model

Package `gh.edu.clet.sfl.fleetlogistics.dispatch.domain`. All aggregates are immutable Java records with
explicit transition methods returning copies (mirroring `FuelAnomalyCase`). Shared value objects
`RecordMetadata`, `SiteCode`, `SourceChannel` are reused from the fleet package; persistence owns the
optimistic-lock version.

## Aggregates

| Aggregate | Identity and invariant |
|---|---|
| `CourierItem` | `(siteCode, itemNumber)` unique among active; direction/type/sensitivity fixed at registration; `chainOfCustodyRequired` derived (CONFIDENTIAL/SECRET sensitivity or SEALED_BAG/EXAMINATION_PAPER/EXAMINATION_DEVICE/SEALED_MATERIAL type); status advances only through allowed transitions; closes only on a clean outcome |
| `Dispatch` | `(siteCode, manifestNumber)` unique; carries route, assigned handler, optional S166 trip/vehicle/driver soft refs, item count, seal IDs; cannot be dispatched empty or unsealed; cannot close while an open exception or unresolved custody gap exists |
| `DispatchManifestItem` | Links one `CourierItem` to one `Dispatch` with expected seal ID and expected quantity; return status per line |
| `CustodyHandover` | Append-only hop with monotonically increasing `sequenceNo` per dispatch; records transferring + receiving custodian, time and seal state; never mutated after recording |
| `DispatchReceipt` | `(dispatchId, captureCorrelationId)` unique (idempotent edge capture); verifies seal state, expected vs verified count, recipient signature; outcome CLEAN or VARIANCE |
| `ReturnReconciliation` | One reconciliation per dispatch return leg; reconciles returned vs manifest; outcome MATCHED or DISCREPANCY |
| `DispatchExceptionCase` | One accountable case per detected variance occurrence; SLA, explanation, decision, evidence and closure reason gated; blocks related closure while open |
| `ScanImportBatch` / `ScanImportRow` | File-level scan ingestion result with idempotent per-row outcomes and retained validation errors |
| `DispatchDashboardSnapshot` | Read model keyed by scope; carries indicators, reconciliation counts, freshness/stale flag |

## Enumerations

- `ItemDirection`: `INBOUND`, `OUTBOUND`
- `ItemType`: `CONFIDENTIAL_CORRESPONDENCE`, `CERTIFICATE`, `SEALED_MATERIAL`, `EXAMINATION_PAPER`, `SEALED_BAG`, `EXAMINATION_DEVICE`, `ORDINARY_MAIL`
- `ItemSensitivity`: `ORDINARY`, `CONFIDENTIAL`, `SECRET`
- `ItemStatus`: `RECEIVED`, `STAGED`, `DISPATCHED`, `IN_TRANSIT`, `DELIVERED`, `RETURNED`, `EXCEPTION`, `CLOSED`
- `DispatchStatus`: `DRAFT`, `SEALED`, `DISPATCHED`, `IN_TRANSIT`, `RECEIVED`, `RETURNED`, `RECONCILED`, `CLOSED`, `EXCEPTION`
- `CustodyHop`: `WAREHOUSE_STAGING`, `DISPATCH`, `TRANSIT`, `CENTRE_RECEIPT`, `HALL_DEPLOYMENT`, `COLLECTION`, `RETURN`
- `SealState`: `INTACT`, `BROKEN`, `REPLACED`, `MISSING`
- `ReceiptOutcome`: `CLEAN`, `VARIANCE`
- `VarianceType`: `BROKEN_SEAL`, `SHORT_COUNT`, `OVER_COUNT`, `WRONG_RECIPIENT`, `MISSING_SIGNATURE`
- `ReturnOutcome`: `MATCHED`, `DISCREPANCY`
- `DispatchExceptionCase.Type`: `UNREGISTERED_ITEM`, `CUSTODY_GAP`, `RECEIPT_VARIANCE`, `SCAN_MISMATCH`, `UNDELIVERED_ITEM`, `RETURN_DISCREPANCY`
- `DispatchExceptionCase.Severity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `DispatchExceptionCase.Status`: `DETECTED`, `ASSIGNED`, `UNDER_REVIEW`, `AWAITING_EXPLANATION`, `EXPLANATION_RECEIVED`, `APPROVED`, `REJECTED`, `ESCALATED`, `CLOSED`, `HELD`, `CANCELLED`, `REOPENED`
- `DispatchExceptionCase.Decision`: `APPROVED`, `REJECTED`

## State machines

**`CourierItem` (`ItemStatus`)** — outbound path:
`RECEIVED → STAGED → DISPATCHED → IN_TRANSIT → DELIVERED → RETURNED → CLOSED`.
Inbound path: `RECEIVED → (STAGED) → DELIVERED (on recorded distribution acknowledgement) → CLOSED`.
`EXCEPTION` is a controlled branch reachable from any active state (custody gap, undelivered, misroute,
scan mismatch); the item returns to its prior operational state or to `CLOSED` only when the exception is
resolved. `CLOSED` requires a clean terminal outcome (`DELIVERED` or `RETURNED` with no open exception).

**`Dispatch` (`DispatchStatus`)**:
`DRAFT → SEALED → DISPATCHED → IN_TRANSIT → RECEIVED → (RETURNED) → RECONCILED → CLOSED`.
`SEALED` requires ≥1 manifest item and recorded seal IDs. `EXCEPTION` branch on custody gap / receipt
variance / return discrepancy. `RECONCILED`/`CLOSED` are **blocked** while any related exception is open
or a custody gap is unresolved (`CustodyChainPolicy` + `DispatchClosurePolicy`).

**`CustodyHandover`** — not a mutable state machine; each hop is appended with the next `sequenceNo`.
`CustodyChainPolicy.detectGaps(dispatch, handovers)` flags: missing expected hop, out-of-order sequence,
non-`INTACT` seal state, and item-count mismatch against the manifest. Any flag → `CUSTODY_GAP` exception
and closure block.

**`DispatchReceipt`** — created once per capture correlation; CLEAN completes the destination leg and
allows dispatch `RECEIVED`; VARIANCE opens a `RECEIPT_VARIANCE` exception (broken-seal/tamper variants
flagged `securityRelevant` → SSEMP) and blocks closure. Edge-captured receipts replay idempotently.

**`ReturnReconciliation`** — MATCHED closes custody; DISCREPANCY (shortfall/extra/broken seal) opens a
`RETURN_DISCREPANCY` exception and blocks custody closure.

**`DispatchExceptionCase` (`Status`)** — identical lifecycle to the fuel anomaly case:
`DETECTED → ASSIGNED → UNDER_REVIEW → (AWAITING_EXPLANATION → EXPLANATION_RECEIVED) →
APPROVED|REJECTED|ESCALATED → CLOSED`, plus `HELD`/`RESUME`, `REASSIGN`, `CANCEL` and authorised `REOPEN`.
Closure requires explanation, decision, closure reason and evidence.

## Chain-of-custody reconstruction

The custody record for any dispatch is reconstructable for audit by ordering `custody_handovers` by
`sequence_no` and joining the immutable audit entries (append-only, hash-chained) and governed evidence
references (signed dispatch/receipt/scan/custody documents). No custody fact lives only in a mutable
column.
