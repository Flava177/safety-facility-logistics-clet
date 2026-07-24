# S168_fuel Migration Plan

Applied S166 migrations are immutable. S168 starts after `V9_1`.

| Migration | Tables/content |
|---|---|
| V10 | `fuel_policies`, `fuel_transactions`, `fuel_odometer_observations`, constraints and lookup indexes |
| V11 | `driver_logbooks`, logbook-to-transaction links and lifecycle constraints |
| V12 | `fuel_reconciliations`, rule results, anomaly cases and case history |
| V13 | import batches/rows, provider configuration/inbox extensions and idempotency constraints |
| V14 | dashboard snapshots/read indexes and configurable sweep defaults |
| V15 | idempotent missing-logbook anomaly key including trip reference |

Rules: UUID identifiers, `TIMESTAMPTZ`, UTC, site scope, audit metadata, correlation/source channel,
optimistic version, no cross-schema foreign keys, decimal numeric types for quantity/money, and indexes for
site/date/status/provider/anomaly queues.
