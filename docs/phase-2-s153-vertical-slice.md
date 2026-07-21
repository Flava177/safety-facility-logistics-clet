# Phase 2: S153 Facility Fault and Work Order Vertical Slice

## Objective

Phase 2 implements the first real working SFL business workflow for S153 - Computerized Maintenance Management System.

The goal is not to complete all maintenance features. The goal is to prove the platform pattern end to end:

```text
API command
-> IFIMP application use case
-> Domain aggregate
-> Repository port
-> Audit hook
-> Workflow task hook
-> Outbox message
-> RabbitMQ-ready event name
-> API response
```

## Implemented Scope

This slice supports:

- Reporting a facility fault.
- Creating a work order from a facility fault.
- Getting facility fault details.
- Getting work order details.
- Domain events for facility fault reporting, work order creation, assignment and closure.
- Audit hook through `IAuditWriter`.
- Workflow task hook through `IWorkflowTaskScheduler`.
- Outbox hook through `IOutboxStore`.
- RabbitMQ-ready event names from the Phase 1 event catalog.
- API endpoints for backend clients.
- Mobile API endpoints for future mobile clients.
- Unit tests for the first domain and application rules.

## API Endpoints

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/v1/ifimp/facility-faults` | Report a facility fault. |
| GET | `/api/v1/ifimp/facility-faults/{id}` | Get facility fault details. |
| POST | `/api/v1/ifimp/facility-faults/{id}/work-order` | Create a work order from a facility fault. |
| GET | `/api/v1/ifimp/work-orders/{id}` | Get work order details. |
| POST | `/api/mobile/v1/ifimp/facility-faults` | Mobile-facing fault reporting. |
| GET | `/api/mobile/v1/ifimp/facility-faults/{id}` | Mobile-facing fault lookup. |
| POST | `/api/mobile/v1/ifimp/facility-faults/{id}/work-order` | Mobile-facing work order creation. |
| GET | `/api/mobile/v1/ifimp/work-orders/{id}` | Mobile-facing work order lookup. |

## Why This Slice Comes First

S153 is a good first vertical slice because it touches the same platform concerns that most of the 13 Phase 1 systems need:

- Validation and command handling.
- Domain state transitions.
- Workflow creation.
- Audit trail.
- Evidence-ready operational records.
- Event-driven integration.
- Portal/mobile API readiness.
- Future PostgreSQL persistence.
- Future RabbitMQ publishing through the outbox.

## Relationship to the 13 Systems

The same implementation pattern can be repeated for the other Phase 1 systems:

| System Type | How the S153 pattern helps |
|---|---|
| Build systems | Use the same command, aggregate, repository, audit, workflow and outbox structure. |
| Buy and Integrate systems | Replace the command source with a vendor adapter/inbox message, then use the same audit/outbox/read-model pattern. |
| Hybrid systems | Vendor systems provide raw/device events, while SFL owns workflow, escalation, dashboard and reporting. |

## Current Persistence Position

This implementation uses in-memory repositories and in-memory outbox/audit adapters so the slice runs immediately.

The next persistence step is to add EF Core/PostgreSQL implementations behind the existing repository and outbox ports. The application service and API contracts should not need to change when persistence is swapped.

## Current Messaging Position

This implementation writes RabbitMQ-ready outbox records using the event catalog names:

- `sfl.ifimp.facility-fault-reported.v1`
- `sfl.ifimp.work-order-created.v1`

The next messaging step is to add a worker that reads pending outbox records and publishes them to RabbitMQ using the repository-approved RabbitMQ pattern.
