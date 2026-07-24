# S168_fuel Event Contracts

Events use `sfl.ftlmp.{event-name}.v1`, the existing SFL envelope, transactional outbox and at-least-once
delivery. Payloads contain references and classifications, not receipt binaries or unmasked card data.

| Event | Trigger |
|---|---|
| `sfl.ftlmp.fuel-transaction-received.v1` | Valid manual/provider transaction accepted once |
| `sfl.ftlmp.fuel-transaction-reconciled.v1` | All active rules pass |
| `sfl.ftlmp.fuel-transaction-rejected.v1` | Transaction fails identity/schema/business validation |
| `sfl.ftlmp.fuel-exception-detected.v1` | One or more reconciliation rules create an exception |
| `sfl.ftlmp.fuel-anomaly-assigned.v1` | Case receives accountable owner/SLA |
| `sfl.ftlmp.fuel-anomaly-approved.v1` | Manager accepts the documented exception |
| `sfl.ftlmp.fuel-anomaly-rejected.v1` | Manager rejects the explanation/transaction |
| `sfl.ftlmp.fuel-anomaly-escalated.v1` | Manual or SLA escalation occurs |
| `sfl.ftlmp.driver-logbook-submitted.v1` | Driver declares and submits a logbook |
| `sfl.ftlmp.driver-logbook-returned.v1` | Reviewer requests correction |
| `sfl.ftlmp.driver-logbook-approved.v1` | Manager approves and locks a logbook |
| `sfl.ftlmp.driver-logbook-overdue.v1` | Scheduled compliance sweep detects a missing/overdue logbook |

Provider transaction identity is `(sourceSystem, providerTransactionId, siteCode)` and webhook/import
idempotency is additionally protected by the envelope idempotency key.
