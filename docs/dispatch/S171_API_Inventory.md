# S171 API Inventory

Base path `/api/v1/dispatch`. All endpoints use the established SFL `ApiResponse`/`ApiError` envelope,
`FleetActorResolver` dev-header/JWT actor resolution, site-scoped authorization, pagination with stable
sorting, correlation IDs, and idempotency for state-changing requests (mandatory for edge receipts and
scan imports). Controlled updates use optimistic locking; workflow transitions are explicit endpoints
(no generic PATCH-status).

| Area | Endpoints | Primary permission |
|---|---|---|
| Dispatch Items | `POST /items`, `GET /items`, `GET /items/{id}`, `POST /items/{id}/stage`, `POST /items/{id}/misroute` | `DISPATCH_ITEM_REGISTER` / `DISPATCH_ITEM_MANAGE` / `DISPATCH_ITEM_READ` |
| Inbound Mail | `POST /inbound`, `GET /inbound`, `POST /inbound/{id}/distribute` (acknowledgement = signature/scan) | `DISPATCH_INBOUND_REGISTER` / `DISPATCH_INBOUND_DISTRIBUTE` |
| Dispatch Manifests | `POST /manifests`, `GET /manifests`, `GET /manifests/{id}`, `POST /manifests/{id}/items`, `POST /manifests/{id}/seal`, `POST /manifests/{id}/dispatch` | `DISPATCH_MANIFEST_CREATE` / `DISPATCH_MANIFEST_READ` |
| Chain of Custody | `POST /custody` (record handover), `GET /custody?dispatchId=`, `GET /custody/{dispatchId}/gaps` | `DISPATCH_CUSTODY_RECORD` / `DISPATCH_MANIFEST_READ` |
| Dispatch Receipts | `POST /receipts` (edge-capable, idempotent), `GET /receipts?dispatchId=`, `GET /receipts/{id}` | `DISPATCH_RECEIPT_CONFIRM` |
| Return Reconciliation | `POST /returns/reconcile`, `GET /returns?dispatchId=`, `GET /returns/{id}` | `DISPATCH_RETURN_RECONCILE` |
| Dispatch Exceptions | `GET /exceptions`, `GET /exceptions/{id}`, explicit `/assign`, `/review`, `/request-explanation`, `/explain`, `/decide`, `/escalate`, `/close`, `/hold`, `/resume`, `/reassign`, `/cancel`, `/reopen` | `DISPATCH_EXCEPTION_READ` / `_MANAGE` / `_APPROVE` / `_ESCALATE` |
| Dispatch Integrations (scans) | `POST /scans/imports` (CSV batch, idempotent), `GET /scans/imports/{id}`, `POST /integrations/scanners/{provider}/events`, `POST /integrations/carriers/{carrier}/status` | `DISPATCH_INTEGRATION_INGEST` |
| Integration health/replay | `GET /integrations/health`, `POST /integrations/outbox/{id}/replay` | `DISPATCH_INTEGRATION_REPLAY` |
| Dispatch Dashboards | `GET /dashboard` (filters: site, date range, sensitivity, handler, centre, status, severity, trip, mode) | `DISPATCH_REPORT_READ` |
| Reports | `GET /reports/items.csv`, `GET /reports/exceptions.csv` | `DISPATCH_REPORT_READ` / `DISPATCH_REPORT_EXPORT` |

## Conventions

- **Pagination**: `page`, `size` query params with a bounded max; responses carry stable sort keys
  (site/date/id). List filters are additive and site-scoped.
- **Idempotency**: `Idempotency-Key` header honoured on all state-changing POSTs; edge receipts also
  carry a body `captureCorrelationId`; scan imports carry a batch reference. Replays return the original
  result, mismatched fingerprints return `409`.
- **Correlation**: `X-Correlation-ID` echoed into `ApiError.correlationId` and audit/outbox records.
- **Errors**: mapped by the shared `FleetApiExceptionHandler` — 400 validation, 403 unauthorized scope,
  404 not found, 409 conflict/version/invalid-transition, 422 closure-evidence/retention/schema, 503
  integration-not-configured, 200 duplicate-safely-ignored / stale-but-served.
- **Swagger**: tags Dispatch Items, Dispatch Manifests, Chain of Custody, Dispatch Receipts, Return
  Reconciliation, Inbound Mail, Dispatch Exceptions, Dispatch Integrations, Dispatch Dashboards and
  Reports. Available at `http://localhost:8093/swagger-ui.html` and `/v3/api-docs`.
