# S168_fuel API Inventory

Base path: `/api/v1/fuel`. State-changing calls require correlation and idempotency metadata where applicable.

| Area | Endpoints |
|---|---|
| Policies | `POST/GET /policies`, `GET /policies/{id}` |
| Transactions | `POST/GET /transactions`, `GET /transactions/{id}`, `POST /transactions/{id}/reconcile`, `POST /transactions/{id}/void` |
| Imports | `POST /imports/csv`, `GET /imports/{id}` |
| Logbooks | `POST/GET /logbooks`, `GET /logbooks/{id}`, explicit `/submit`, `/review`, `/return`, `/approve`, `/reopen`, `/cancel` transitions |
| Reconciliation | `POST /reconciliations/run`, `GET /reconciliations/{transactionId}` |
| Anomalies | `GET /anomalies`, `GET /anomalies/{id}`, explicit assign/review/explanation/decision/escalate/close/reopen operations |
| Integrations | `POST /integrations/providers/{provider}/transactions`, integration-health and replay operations |
| Dashboard/reporting | `GET /dashboard`, `GET /reports/transactions.csv`, drilldown links to source records |

All endpoints use the established SFL response/error envelope, site authorization, pagination and stable sorting.
