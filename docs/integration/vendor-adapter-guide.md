# Vendor Adapter Guide

## Purpose

Vendor systems should be integrated through controlled adapters. The domain modules must not directly call CCTV, access-control, fire-panel, fleet, fuel, visitor, notification or courier vendor SDKs.

## Standard Flow

```text
Vendor system
-> API / webhook / SDK / export
-> Infrastructure adapter
-> SFL.IntegrationHub inbox
-> Normalized event/data model
-> Domain workflow / read model / dashboard
-> RabbitMQ integration event
```

## Adapter Responsibilities

A vendor adapter is responsible for:

- Authenticating with the vendor system.
- Checking vendor-system health.
- Receiving webhooks or pulling data safely.
- Validating signatures, timestamps, source allowlists and schemas.
- Translating vendor payloads to SFL normalized messages.
- Recording inbound messages for idempotency.
- Publishing normalized integration events through SFL.Messaging.
- Respecting rate limits, retries and dead-letter handling.
- Avoiding vendor-specific types outside infrastructure.

## Adapter Folder Standard

Adapters live under:

```text
src/SFL.Infrastructure.ExternalSystems/{AdapterName}
```

Current Phase 1 adapter folders:

| Folder | Purpose |
|---|---|
| `Cctv` | Video management system, camera health, evidence references. |
| `AccessControl` | Door/card/biometric access events and permission sync. |
| `FleetTracking` | Vehicle location and telematics feeds. |
| `FireLifeSafety` | Fire panels, panic alerts and life-safety event feeds. |
| `FuelMonitoring` | Fuel transactions, odometer references and reconciliation inputs. |
| `VisitorManagement` | Kiosks, badge printers and turnstile/visitor devices. |
| `EmergencyNotification` | SMS, email, push, voice, siren and signage providers. |
| `CourierDispatch` | Courier provider APIs, barcode scanners and dispatch status. |

## Implementation Sequence

1. Add a simulator or fake adapter first.
2. Define inbound message schema and contract tests.
3. Add health check and authentication logic.
4. Add inbound polling or webhook receiver.
5. Normalize vendor payload to SFL message/event names.
6. Persist inbox message before business processing.
7. Publish integration event through RabbitMQ.
8. Add dashboard/read model projection.
9. Add operational monitoring and failure alerts.
10. Connect the real vendor environment after tests pass.

## Rule for Evidence

For CCTV, access control, incidents and dispatch evidence, SFL should store evidence references by default. Raw files, images or video clips should be copied into SFL storage only when approved by retention, privacy and storage policies.
