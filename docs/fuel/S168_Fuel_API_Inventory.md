# S168_fuel API Inventory

Base path: `/api/v1/fuel`. State-changing calls require correlation and idempotency metadata where applicable.

| Area | Endpoints |
|---|---|
| Policies | `POST/GET /policies`, `GET /policies/{id}` |
| Transactions | `POST/GET /transactions`, `GET /transactions/{id}`, `POST /transactions/{id}/reconcile`, `POST /transactions/{id}/void` |
| Imports | `POST /imports/csv`, `GET /imports`, `GET /imports/{id}`, `GET /imports/{id}/rows` |
| Logbooks | `POST/GET /logbooks`, `GET /logbooks/{id}`, explicit `/submit`, `/review`, `/return`, `/approve`, `/reopen`, `/cancel` transitions |
| Reconciliation | `POST /reconciliations/run`, `GET /reconciliations/{transactionId}` |
| Anomalies | `GET /anomalies`, `GET /anomalies/{id}`, explicit assign/review/explanation/decision/escalate/close/reopen operations |
| Integrations | `POST /integrations/providers/{provider}/transactions`, integration-health and replay operations |
| Dashboard/reporting | `GET /dashboard`, `GET /dashboard/daily-totals`, `GET /dashboard/anomaly-counts`, `GET /reports/transactions.csv`, drilldown links to source records |

All endpoints use the established SFL response/error envelope, site authorization, pagination and stable sorting.

## Added in the gap-closure round (29 July 2026)

Every entry below was added because a screen was doing the service's work in the browser. Each was
confirmed against the running service, not against this document.

| Endpoint | Parameters | Why it exists |
|---|---|---|
| `GET /imports/{id}/rows` | `status`, `page`, `size`, `sort` | The detail read returns every row, which a file of thousands makes unusable, and the screen filtered that array. `status` filters in SQL — a status filter over a page finds only the rejections that happen to land on the page being looked at. The count shares the predicate with the page, so the total describes the filter rather than the batch. |
| `GET /dashboard/daily-totals` | `siteCode` (required), `from`, `to` | Spend, volume and a transaction count per day, `date_trunc` to a UTC day. The spend chart bucketed a page of fetched transactions, so it described that page rather than the site. Only days with a transaction are returned; the screen supplies the empty days so its axis stays a fixed window. |
| `GET /dashboard/anomaly-counts` | `siteCode` (required) | Open anomaly cases by type, where open means not `CLOSED` or `CANCELLED` — the same definition the queue uses. The by-type chart counted a page of the queue. Carries no urgency, which is why the chart no longer claims one. |

Both dashboard reads require `FUEL_REPORT_READ`. `GET /imports/{id}/rows` inherits its authorisation
from the batch read rather than repeating it with a permission that does not exist.
