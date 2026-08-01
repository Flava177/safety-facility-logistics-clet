# S168_fuel Requirement Traceability Matrix

| Requirement | Current delivery | Verification status |
|---|---|---|
| SRS-SFL-S168fuel-01 Operational records | Policy, transaction/import, odometer-observation, logbook, anomaly, reconciliation and fuel-card records implemented with site scope, metadata and migrations | Done for Release 1 demo; live provider master-data import remains external integration work |
| SRS-SFL-S168fuel-02 Workflow | Transaction capture/reconcile/void, policy versioning, daily/monthly policy/card limits, anomaly lifecycle, SLA escalation/sweeps and logbook workflow implemented | Done for Release 1 demo; E2E coverage pins pass/fail, card override and policy fallback paths |
| SRS-SFL-S168fuel-03 Evidence/audit | State changes call the audit port, evidence references are carried through transactions/logbooks/anomalies and reconciliation history stores per-rule outcomes | Done for Release 1 demo |
| SRS-SFL-S168fuel-04 Integrations | Signed inbox reuse, provider endpoint, CSV import, Fleet reference/odometer port, outbox events, replay and fuel-card registry API/UI implemented | Done with recorded/import adapters; live provider and Finance/Audit acknowledgement contracts remain deferred |
| SRS-SFL-S168fuel-05 Dashboards/reports | Dashboard, transactions, reconciliation, policies, fuel cards, imports, anomalies, logbooks, provider integration and CSV/report paths implemented in the React shell | Done for Release 1 demo |

## Cross-cutting acceptance

- Core records carry site scope, actor/time metadata, source channel and correlation ID.
- Mutable core aggregates use optimistic locking and explicit transitions.
- Verification: `sfl-fleet-logistics-service` ran 423 tests with 0 failures/errors/skips against the
  Docker E2E database on `localhost:55443`; the SFL Operations UI ran 156 tests and a clean production build.
