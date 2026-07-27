# S171 Event Contracts

Events use `sfl.ftlmp.{event-name}.v1`, the existing SFL envelope, the shared transactional outbox
(`IntegrationEventPublisher` → `outbox_messages`, at-least-once delivery) and are registered as
`FleetEventType` constants. State changes and outbox records commit atomically (publisher is
`@Transactional(MANDATORY)`). Payloads carry references and classifications only — never signature
binaries, unmasked recipient PII or seal secrets. The two pre-seeded catalog events are reused verbatim.

| Event | Trigger |
|---|---|
| `sfl.ftlmp.dispatch-item-registered.v1` | A courier item is registered in the outbound register |
| `sfl.ftlmp.inbound-item-registered.v1` | An inbound mail item is registered |
| `sfl.ftlmp.inbound-item-distributed.v1` | Inbound item distributed with recorded acknowledgement (item closed) |
| `sfl.ftlmp.inbound-item-undelivered.v1` | Scheduled sweep flags an undelivered/unclaimed inbound item after its window |
| `sfl.ftlmp.dispatch-created.v1` *(pre-seeded)* | A dispatch manifest is created |
| `sfl.ftlmp.dispatch-dispatched.v1` | A sealed manifest is dispatched (leaves the warehouse) |
| `sfl.ftlmp.custody-handover-recorded.v1` | A chain-of-custody hop is recorded |
| `sfl.ftlmp.custody-gap-detected.v1` | A missing handover / broken seal / count mismatch is detected (blocks closure) |
| `sfl.ftlmp.dispatch-received.v1` *(pre-seeded)* | A clean destination receipt is confirmed |
| `sfl.ftlmp.dispatch-receipt-variance.v1` | A receipt variance opens an exception (seal/tamper variants flagged for SSEMP) |
| `sfl.ftlmp.dispatch-return-reconciled.v1` | A return leg reconciles cleanly against the manifest |
| `sfl.ftlmp.dispatch-return-discrepancy.v1` | A return shortfall/extra/broken seal blocks custody closure |
| `sfl.ftlmp.dispatch-scan-mismatch.v1` | A scanned item does not match its manifest entry |
| `sfl.ftlmp.dispatch-exception-assigned.v1` | An exception case receives an accountable owner/SLA |
| `sfl.ftlmp.dispatch-exception-approved.v1` | A manager accepts the documented exception |
| `sfl.ftlmp.dispatch-exception-rejected.v1` | A manager rejects the explanation |
| `sfl.ftlmp.dispatch-exception-escalated.v1` | Manual or SLA escalation occurs |

## Inbound (consumed via the shared secure inbox)

Scanner/label events and courier-carrier status updates arrive through `FleetIntegrationApplicationService.receive(...)`:
HMAC signature + timestamp window, per-source allowlist, schema validation, inbox persistence before
domain processing, and idempotency by `(source_system, idempotency_key)`. Duplicate messages are safely
ignored (`FLEET_INTEGRATION_DUPLICATE_MESSAGE` → HTTP 200). A scan that does not match the manifest entry
produces a `SCAN_MISMATCH` exception and the `dispatch-scan-mismatch.v1` event.

## Idempotency identity

- Provider/scan message identity: `(source_system, idempotency_key, siteCode)` via the inbox.
- Edge receipt identity: `(dispatchId, captureCorrelationId)` plus the envelope idempotency key.
- Outbound delivery: at-least-once; consumers must be idempotent. Failed deliveries retry with backoff
  and dead-letter (visible on integration health); privileged replay via `DISPATCH_INTEGRATION_REPLAY`.

All events above are added to `docs/integration/event-catalog.md` under `### SFL.FTLMP`.
