# S174 Event Contracts

Events use `sfl.ssemp.{event-name}.v1`, the shared `IntegrationEventEnvelope`, and the service's own
transactional outbox (state change + outbox row commit atomically; a drainer delivers at-least-once).
Payloads carry references and classifications only — never message bodies, unmasked recipient PII or
provider secrets. The two pre-seeded catalog events are reused verbatim.

| Event | Trigger |
|---|---|
| `sfl.ssemp.emergency-notification-activated.v1` *(pre-seeded)* | A mass notification campaign is approved/activated (routine activate or break-glass). |
| `sfl.ssemp.emergency-notification-status-received.v1` *(pre-seeded)* | Delivery or acknowledgement status is received from the notification provider. |
| `sfl.ssemp.emergency-template-created.v1` | A notification template is created. |
| `sfl.ssemp.emergency-activation-submitted.v1` | A routine activation is submitted for approval. |
| `sfl.ssemp.emergency-activation-approved.v1` | A routine activation is approved. |
| `sfl.ssemp.emergency-break-glass-activated.v1` | A break-glass activation fires without pre-approval. |
| `sfl.ssemp.emergency-after-action-approved.v1` | After-the-fact approval/justification is recorded for a break-glass activation. |
| `sfl.ssemp.emergency-all-clear-sent.v1` | An all-clear is issued for an active activation. |
| `sfl.ssemp.emergency-acknowledgement-received.v1` | A recipient acknowledgement is recorded. |
| `sfl.ssemp.emergency-activation-closed.v1` | An activation is closed with reason/summary/evidence. |
| `sfl.ssemp.emergency-drill-completed.v1` | A drill run completes and records performance metrics. |

## Inbound (consumed via the secure integration inbox)

Provider delivery-status and acknowledgement callbacks arrive at
`POST /api/v1/emergency/provider-callbacks/{provider}/…` and pass through the inbox: HMAC (or mTLS)
verification, per-source allowlist, schema validation, inbox persistence **before** domain processing, and
idempotency by `(source_system, idempotency_key, siteScope)`. Unsigned/untrusted/schema-invalid payloads
are rejected before any domain side effect. Duplicate messages are safely ignored (HTTP 200 with the
duplicate error code). Confirmed status updates raise `emergency-notification-status-received.v1`.

## Idempotency identity

- Provider callback identity: `(source_system, idempotency_key, siteScope)` via the inbox.
- Delivery receipt identity: `(activationId, provider, providerMessageId)`.
- Acknowledgement identity: `(activationId, recipientRef)`.
- Activation request identity: the envelope `Idempotency-Key` header.
- Outbound delivery: at-least-once with backoff + dead-letter (visible on integration health);
  privileged replay via `EMERGENCY_INTEGRATION_REPLAY`.

All events above are added to `docs/integration/event-catalog.md` under `### SFL.SSEMP`.
