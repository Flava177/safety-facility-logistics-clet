# ADR 0002: Build, Buy and Hybrid Integration Strategy

## Status

Accepted

## Context

Phase 1 contains 13 systems across facilities, safety/security/emergency operations, fleet/transport/logistics and asset visibility support. Some systems are workflow-heavy and should be built inside SFL. Others depend on specialist certified hardware/software such as CCTV, access control, fire panels, intrusion systems, GPS tracking, fuel devices and notification gateways.

Trying to build every hardware-heavy capability from scratch would increase delivery risk, procurement risk, certification risk and maintenance burden.

## Decision

Every Phase 1 system must be classified as one of the following before implementation:

| Decision | Meaning |
|---|---|
| Build | SFL owns the workflow, data model, screens, rules, audit and reporting. |
| Buy and Integrate | A vendor system owns the hardware/device operation. SFL retrieves and displays data through approved integration methods. |
| Hybrid | A vendor system captures device/raw data while SFL owns workflow, escalation, audit, dashboarding and reporting. |

The classification is documented in `docs/phase-1-system-classification.md`.

## Consequences

SFL should be the operational command and workflow platform, not a replacement for every specialist hardware system.

Purchased systems must support integration through REST API, webhook, SDK, message export or structured export. Procurement must use `docs/integration/vendor-procurement-checklist.md` before selecting hardware or vendor products.

Vendor-specific implementation details must stay in `SFL.Infrastructure.ExternalSystems`. Domain modules must call ports/interfaces only.

The first real Phase 2 vertical slice should still be built inside SFL because it proves the platform pattern: facility fault -> work order -> workflow -> audit -> outbox -> RabbitMQ event -> read model/API.
