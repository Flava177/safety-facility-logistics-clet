# S174 API Inventory

Base path `/api/v1/emergency`. All endpoints use the shared `ApiResponse`/`ApiError` envelope, dev-header
/JWT actor resolution, site-scoped authorization, `X-Correlation-ID`, and idempotency (`Idempotency-Key`).
Workflow transitions are explicit endpoints (no generic status PATCH).
Swagger at `http://localhost:8095/swagger-ui.html` and `/v3/api-docs`.

| Tag | Endpoints | Primary permission |
|---|---|---|
| Emergency Templates | `POST /templates`, `GET /templates`, `GET /templates/{id}` | `EMERGENCY_TEMPLATE_MANAGE` / `_READ` |
| Emergency Scenarios | `POST /scenarios`, `GET /scenarios` | `EMERGENCY_SCENARIO_MANAGE` / `_READ` |
| Audience Groups | `POST /audience-groups`, `GET /audience-groups` | `EMERGENCY_AUDIENCE_MANAGE` / `_READ` |
| Recipient Zones | `POST /recipient-zones`, `GET /recipient-zones` | `EMERGENCY_AUDIENCE_MANAGE` / `_READ` |
| Activations | `POST /activations`, `GET /activations`, `GET /activations/{id}`, `POST /activations/{id}/submit`, `/approve`, `/reject`, `/activate`, `/all-clear`, `/close`, `/after-action-approval`, `GET /activations/{id}/status` | `EMERGENCY_ACTIVATION_CREATE` / `_APPROVE` / `_SEND` / `EMERGENCY_ALL_CLEAR_SEND` / `EMERGENCY_AFTER_ACTION_APPROVE` |
| Break Glass | `POST /activations/break-glass` | `EMERGENCY_BREAK_GLASS_SEND` |
| Delivery and Acknowledgements | `POST /provider-callbacks/{provider}/delivery-status`, `POST /provider-callbacks/{provider}/acknowledgements` | `EMERGENCY_INTEGRATION_INGEST` |
| Drills | `POST /drills`, `GET /drills`, `POST /drills/{id}/complete` | `EMERGENCY_ACTIVATION_CREATE` |
| Integrations | `GET /integrations/health`, `POST /integrations/outbox/{messageId}/replay` | `EMERGENCY_INTEGRATION_REPLAY` |
| Dashboards and Reports | `GET /dashboard`, `GET /reports/activations.csv` | `EMERGENCY_REPORT_READ` / `_EXPORT` |

## Conventions

- **Envelope**: `ApiResponse<T>(data, error)`; errors carry `EmergencyErrorCode.code()` + the SRS
  user-facing message + `correlationId`.
- **Idempotency**: activation creation and break-glass creation replay the original activation for the same
  `Idempotency-Key` and payload. Provider callbacks also carry a body idempotency key; duplicates return the
  safe-ignore code.
- **Error mapping** (`EmergencyApiExceptionHandler`): 400 validation, 403 unauthorized scope/approval,
  404 not found, 409 conflict/version/invalid-transition, 422 closure-evidence/retention, 200
  duplicate-safely-ignored / stale-but-served.
- **Break-glass** (`POST /activations/break-glass`) fires without pre-approval for an authorised
  `EMERGENCY_BREAK_GLASS_SEND` actor and a break-glass-eligible template/scenario; closure is blocked
  until `POST /activations/{id}/after-action-approval` is recorded.
- **Compatibility alias**: `/api/v1/security/emergency/...` is documented but not built unless a consumer
  requires the original workplan path (see gap report C-07).
