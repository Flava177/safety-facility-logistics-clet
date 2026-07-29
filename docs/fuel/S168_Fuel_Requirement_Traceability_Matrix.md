# S168_fuel Requirement Traceability Matrix

| Requirement | Current delivery | Verification status |
|---|---|---|
| SRS-SFL-S168fuel-01 Operational records | Core policy, transaction/import, odometer-observation and logbook persistence/API implemented | Partial: domain and one PostgreSQL workflow exist; API, site-scope, lifecycle/archive and duplicate-path coverage remains open |
| SRS-SFL-S168fuel-02 Workflow | Core logbook transitions, reconciliation, anomaly decisions, SLA escalation and sweeps implemented | Partial: hold/reassignment/cancellation/notifications, complete rule set and mandatory workflow scenarios remain open |
| SRS-SFL-S168fuel-03 Evidence/audit | State changes call the existing audit port and records carry evidence references | Partial: governed evidence/export flows and fuel-specific audit integrity/tamper tests remain open |
| SRS-SFL-S168fuel-04 Integrations | Signed inbox reuse, provider endpoint, CSV import, Fleet reference/odometer port and outbox events implemented | Partial: strict fuel schema/version tests, replay, Finance/Audit delivery, retry/dead-letter E2E remain open |
| SRS-SFL-S168fuel-05 Dashboards/reports | Basic site totals, freshness flag, transaction CSV and initial responsive dashboard implemented | Partial: required snapshot dimensions, filters, drilldowns and operational screens remain open |

## Cross-cutting acceptance

- Core records carry site scope, actor/time metadata, source channel and correlation ID.
- Mutable core aggregates use optimistic locking and explicit transitions.
- Swagger, `/fuel/`, PostgreSQL migrations and the existing S166 regression suite require a final rerun after
  the latest S168 changes.
