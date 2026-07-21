# SFL Phase 1 Microservices Build Workflow Plan

Version: 1.0  
Date: July 2026  
Basis: `SFL_SRS.docx`, `SFL_Phase1_System_Architecture_Implementation_Guide_v2.md`, Cluster 9 source mapping  
Purpose: Prompt-ready workplan for building the Phase 1 SFL services while excluding systems or components that should be purchased and integrated.

## 1. Architecture Decision

SFL Phase 1 should be delivered as a set of deployable microservices inside the wider CLET microservices ecosystem. Do not build 13 separate applications. Group the Phase 1 scope into bounded-context services and integrate purchased systems through adapters.

Recommended deployable services:

| Service | Owns | Phase 1 Systems |
|---|---|---|
| `sfl-facilities-service` | Facilities master data, readiness, maintenance, room/resource booking | S152, S153, S159 |
| `sfl-safety-security-service` | Visitor management, access/CCTV/alarm event governance, life-safety monitoring, HSE incidents, emergency notification orchestration | S160, S160a, S161, S162, S162a, S163, S174 |
| `sfl-fleet-logistics-service` | Fleet register, driver/vehicle readiness, fuel/logbooks, courier and dispatch chain-of-custody | S166, S168_fuel, S171 |
| `sfl-asset-visibility-service` | Phase 1 asset/device reference layer; future RFID/barcode inventory baseline | S168 as AVAMP-Lite only |

Shared CLET ecosystem services should not be duplicated inside SFL:

| Shared Service | SFL Usage |
|---|---|
| Identity / IAM | Authentication, MFA, OIDC/JWT, service accounts, coarse roles |
| API Gateway | External routing, TLS, rate limits, client access boundary |
| Notification Service | Email, SMS, push, voice, siren/signage provider dispatch |
| Audit / Evidence Service | Tamper-evident audit, evidence metadata, file/object references, export logs |
| Integration Gateway / Event Broker | Vendor adapters, webhooks, Kafka/RabbitMQ topics, retries, dead letters |
| Reporting / Analytics | Executive dashboards, BI, cross-service projections |
| Document / Object Storage | Evidence files, CCTV export packages, certificates, attachments |

## 2. Build vs Buy Decision Matrix

The software team should build CLET-owned workflow, records, governance, dashboards, evidence, integration adapters and service APIs. Specialist hardware platforms and commodity provider channels should be purchased or procured, then integrated.

| System | Build Scope | Buy / Integrate Scope | Decision |
|---|---|---|---|
| S152 CAFM / IWMS | Build Phase 1 facilities register, sites/buildings/rooms/zones, readiness metadata, service control APIs and dashboards. | Optional commercial CAFM/IWMS such as Archibus, Planon or FMx if CLET wants a mature full IWMS later. | Build Phase 1 core; keep adapter-ready for future CAFM product. |
| S153 CMMS | Build work request, work order, assignment, SLA, preventive maintenance, closure evidence and dashboard workflows. | Optional vendor maintenance/parts catalogues; contractor systems if available. | Build. |
| S159 Room & Resource Booking | Build booking workflow, approval, conflict checking, setup tasks, no-show rules and readiness links. | Calendar provider, room panels or signage hardware if procured. | Build plus integrate calendar/panels. |
| S160 Visitor Management | Build visitor pre-registration, host approval, badge record, check-in/out, roll-call and visit audit. | Badge printer/scanner hardware; optional watchlist source if approved. | Build plus integrate hardware. |
| S160a Physical Access Control | Build access event ingestion, door/reader mapping, override workflow, access exception queue and dashboards. | Door controllers, card readers, biometric readers, access-control vendor software. | Buy hardware/vendor platform; build integration and governance. |
| S161 CCTV / VMS | Build camera registry, health projection, incident linkage, evidence request/export approval and audit trail. | Cameras, NVR/VMS platform, video storage, CCTV analytics if required. | Buy VMS/hardware; build integration and evidence governance. |
| S162 Intrusion Detection | Build alarm event queue, acknowledgement, escalation, incident linkage and dashboard. | Intrusion panels, monitoring contracts, sensors, armed-response integration. | Buy hardware/service; build integration and workflow. |
| S162a Fire / Life-Safety | Build event monitoring, panel fault tracking, inspection reminders, emergency workflow linkage and dashboards. | Certified fire alarm panels, smoke detectors, panic buttons, sprinkler controllers. | Buy certified systems; build monitoring/governance only. |
| S163 HSE Incident / Near-Miss | Build incident reporting, severity, RCA, CAPA, evidence, investigation and reports. | None mandatory beyond file/object storage and shared notification. | Build. |
| S166 Fleet & Vehicle Management | Build vehicle register, driver eligibility, compliance, service status, assignment readiness and dashboard. | GPS/telematics provider later; vehicle maintenance vendor data if available. | Build core; integrate external providers where procured. |
| S168_fuel Fuel & Driver Logbooks | Build fuel records, odometer, driver logbook, reconciliation, anomaly workflow and dashboard. | Fuel card/provider imports, POS/fuel station files, GPS correlation if procured. | Build core; integrate fuel provider. |
| S171 Mailroom / Courier & Dispatch | Build courier item register, dispatch manifest, chain-of-custody, sealed dispatch, receipt and exception workflows. | Barcode/RFID scanner hardware, courier provider APIs where available. | Build core; integrate scanner/provider. |
| S174 Emergency Mass Notification | Build emergency scenarios, templates, approvals, break-glass orchestration, acknowledgements and audit. | SMS, email, voice, push, siren, digital signage providers. | Build orchestration; buy channel providers. |
| S168 Asset Tagging / RFID / Barcode Inventory | Build AVAMP-Lite: asset/device IDs, location/custody references, device-to-location mapping and evidence references. | RFID/barcode scanners, tags, full inventory platform features for Phase 2. | Build AVAMP-Lite now; full S168 in Phase 2. |

## 3. Microservice Boundary Rules

Each SFL service must own its database and business rules.

Required rules:

- Each service has its own PostgreSQL database or schema and migration history.
- No service reads or writes another service's tables.
- No cross-service foreign keys.
- Cross-service state changes use events and sagas.
- Synchronous calls are allowed only for query/reference needs that genuinely require immediate answers.
- All service-to-service calls use service identity through the API gateway or service mesh.
- Every service publishes integration events through an outbox table.
- Every consumer is idempotent and stores processed message IDs durably.
- Vendor systems never write directly into service databases.
- Evidence files and CCTV exports are stored in object/document storage; services store references and hashes.

Recommended Spring Boot service structure:

```text
sfl-{domain}-service/
  src/main/java/.../
    api/
    application/
      command/
      query/
      workflow/
      port/
    domain/
      model/
      event/
      policy/
    infrastructure/
      persistence/
      messaging/
      integration/
      security/
  src/main/resources/
    db/migration/
    application.yml
  src/test/java/.../
  Dockerfile
  pom.xml or build.gradle
```

## 4. Delivery Workflow Overview

Build in seven waves. Each wave should produce working software, tests, API contracts and prompt-ready follow-up tasks.

| Wave | Name | Goal | Output |
|---|---|---|---|
| 0 | Ecosystem Foundation | Establish shared conventions, service skeletons, security, messaging, migrations and observability. | Four bootable Spring Boot services plus local Docker stack. |
| 1 | Asset Reference Baseline | Build AVAMP-Lite first so all other services can reference rooms, devices, vehicles and custody items consistently. | `sfl-asset-visibility-service` with asset/device/location APIs. |
| 2 | Facilities Core | Build facilities register, maintenance and room booking workflows. | `sfl-facilities-service` MVP. |
| 3 | Safety / Security Core | Build visitor, HSE, SOC queue, access/CCTV/alarm/life-safety integration workflows and emergency orchestration. | `sfl-safety-security-service` MVP. |
| 4 | Fleet / Logistics Core | Build fleet, fuel/logbook and dispatch workflows. | `sfl-fleet-logistics-service` MVP. |
| 5 | Cross-Service Readiness | Add sagas, event projections and dashboards for hall readiness, incident response and dispatch traceability. | End-to-end cross-service workflows. |
| 6 | Commissioning | Run acceptance, integration, failover, security, audit and operational readiness tests. | Go-live evidence and sign-off pack. |

## 5. Wave 0 - Ecosystem Foundation

Goal: make every service runnable, observable, secure and integration-ready before business workflows are built.

### Build Tasks

| Task ID | Prompt Topic | Output |
|---|---|---|
| W0-01 | Scaffold four Spring Boot services | `sfl-facilities-service`, `sfl-safety-security-service`, `sfl-fleet-logistics-service`, `sfl-asset-visibility-service` |
| W0-02 | Add shared service conventions | Package layout, DTO style, error envelope, validation style, exception handling |
| W0-03 | Configure PostgreSQL migrations | Separate DB/schema per service with Flyway or Liquibase |
| W0-04 | Configure OAuth2 resource server | JWT validation, role extraction, site-scope claims |
| W0-05 | Add service-to-service security baseline | Client credentials or gateway/service-mesh auth pattern |
| W0-06 | Add Kafka/RabbitMQ integration baseline | Producer/consumer config, retry, dead-letter topic/queue |
| W0-07 | Add transactional outbox pattern | Outbox table, publisher job, event envelope |
| W0-08 | Add idempotent consumer pattern | Processed-message table and duplicate handling |
| W0-09 | Add OpenAPI contracts | `/v3/api-docs`, Swagger UI, versioned routes |
| W0-10 | Add observability | OpenTelemetry tracing, structured logs, metrics, health probes |
| W0-11 | Add Docker Compose dev stack | Postgres, broker, Redis, identity mock/Keycloak, all SFL services |
| W0-12 | Add architecture tests | No cross-service persistence access, package dependency rules |

### Acceptance Criteria

- Each service starts locally and exposes `/actuator/health`.
- Each service can run migrations independently.
- A test event can be published through outbox and consumed idempotently.
- APIs reject unauthenticated requests and accept valid JWTs.
- Trace/correlation IDs appear in logs and events.

## 6. Wave 1 - Asset Visibility Baseline (`sfl-asset-visibility-service`)

Goal: create the stable reference layer for physical items used by all Phase 1 services.

### Build Scope

| Capability | Requirements |
|---|---|
| Asset/device register | Create and maintain asset/device references for rooms, cameras, readers, panels, vehicles, dispatch items and equipment. |
| Location mapping | Link assets/devices to site, building, floor, room, zone or vehicle. |
| Custody references | Track current custodian or responsible unit where needed. |
| External references | Store vendor IDs, serial numbers, tag IDs and source system references. |
| Evidence references | Link assets/devices to evidence metadata without storing files. |
| Lookup APIs | Provide query APIs for other services to resolve asset/device/location references. |

### Events

| Event | Producer | Consumers |
|---|---|---|
| `sfl.asset.asset-registered.v1` | Asset service | Facilities, Safety, Fleet, Reporting |
| `sfl.asset.asset-location-changed.v1` | Asset service | Facilities, Safety, Fleet, Reporting |
| `sfl.asset.device-status-reference-updated.v1` | Asset service | Safety, Facilities, Reporting |

### Explicitly Not Phase 1

- Full RFID/barcode stocktake workflow.
- Financial asset register replacement.
- Depreciation, capitalization or disposal accounting.
- Large-scale scanner fleet management.

## 7. Wave 2 - Facilities Core (`sfl-facilities-service`)

Systems: S152, S153, S159.

### Build Scope

| Epic | System | Build Output |
|---|---|---|
| FAC-01 Facility master data | S152 | Sites, buildings, floors, rooms, zones, readiness attributes and service ownership. |
| FAC-02 Facility readiness | S152 | Readiness checklist templates, room/hall readiness status, blockers and readiness score. |
| FAC-03 Work requests | S153 | Fault reporting, categorisation, priority/severity, attachments and source channel. |
| FAC-04 Work orders | S153 | Assignment, SLA, technician/vendor update, evidence closure and verification. |
| FAC-05 Preventive maintenance | S153 | Schedules, recurring tasks, compliance dates and overdue alerts. |
| FAC-06 Room booking | S159 | Booking request, conflict checks, approvals, setup tasks and no-show handling. |
| FAC-07 Examination mode locks | S159 / S152 | Prevent conflicting room changes once a hall is confirmed ready for examination mode. |
| FAC-08 Facilities dashboard | S152 / S153 / S159 | Open faults, readiness risk, SLA breaches, bookings and unavailable rooms. |

### Purchased / Integrated Components

- Optional future commercial CAFM/IWMS platform.
- Calendar provider.
- Room display panels or digital signage, if procured.
- BMS/IoT telemetry is Phase 2, but adapter seams should exist.

### Key Events

| Event | Trigger |
|---|---|
| `sfl.facilities.facility-created.v1` | Site/building/room/zone created |
| `sfl.facilities.work-order-created.v1` | Work order opened |
| `sfl.facilities.work-order-closed.v1` | Work order verified and closed |
| `sfl.facilities.room-booking-approved.v1` | Booking approved |
| `sfl.facilities.hall-readiness-blocked.v1` | Critical readiness item fails |
| `sfl.facilities.hall-readiness-confirmed.v1` | Hall passes readiness checks |

## 8. Wave 3 - Safety and Security Core (`sfl-safety-security-service`)

Systems: S160, S160a, S161, S162, S162a, S163, S174.

### Build Scope

| Epic | System | Build Output |
|---|---|---|
| SEC-01 Visitor lifecycle | S160 | Pre-registration, host approval, badge record, check-in/out, visitor status and roll-call feed. |
| SEC-02 Access event governance | S160a | Reader/door references, access granted/denied ingestion, forced-door/tamper events, override workflow. |
| SEC-03 CCTV governance | S161 | Camera references, camera health, incident linkage, footage request, export approval and retention hold. |
| SEC-04 Intrusion alarm workflow | S162 | Alarm ingestion, SOC queue, acknowledgement, false-alarm reason, escalation and closure. |
| SEC-05 Life-safety monitoring | S162a | Fire/smoke/panic/fault event ingestion, inspection tracking, emergency trigger and CMMS handoff. |
| SEC-06 HSE incident workflow | S163 | Incident/near-miss reporting, severity, RCA, CAPA, evidence and closure validation. |
| SEC-07 Emergency notification orchestration | S174 | Templates, scenarios, zones, approval, break-glass send, channel fan-out and acknowledgement tracking. |
| SEC-08 SOC dashboard | S160a / S161 / S162 / S162a / S163 / S174 | Open alerts, visitor counts, camera health, denied access, active incidents and emergency status. |

### Purchased / Integrated Components

- Access-control hardware and vendor software.
- Biometric/card readers.
- CCTV cameras, NVR/VMS and video storage.
- Intrusion panels, sensors and monitoring contracts.
- Certified fire/life-safety panels and panic hardware.
- SMS/email/voice/push/siren/signage providers.

### Key Events

| Event | Trigger |
|---|---|
| `sfl.security.visitor-checked-in.v1` | Visitor checked in |
| `sfl.security.access-denied-recorded.v1` | Access denied event ingested |
| `sfl.security.camera-offline-detected.v1` | Camera health failure detected |
| `sfl.security.evidence-export-approved.v1` | CCTV/evidence export approved |
| `sfl.security.alarm-raised.v1` | Intrusion/life-safety event classified |
| `sfl.security.hse-incident-reported.v1` | HSE incident submitted |
| `sfl.security.emergency-notification-sent.v1` | Emergency notification sent |

## 9. Wave 4 - Fleet and Logistics Core (`sfl-fleet-logistics-service`)

Systems: S166, S168_fuel, S171.

### Build Scope

| Epic | System | Build Output |
|---|---|---|
| LOG-01 Vehicle register | S166 | Vehicles, compliance documents, service status, availability and responsible unit. |
| LOG-02 Driver eligibility | S166 | Driver profile reference, licence/compliance status, assignment restrictions and availability. |
| LOG-03 Vehicle assignment | S166 | Vehicle/driver assignment, readiness checks, trip/dispatch link and exception handling. |
| LOG-04 Fuel transactions | S168_fuel | Fuel entry/import, receipt, odometer, limit check and reconciliation. |
| LOG-05 Driver logbooks | S168_fuel | Driver log entry, route/use notes, odometer start/end, submission and review. |
| LOG-06 Fuel anomaly workflow | S168_fuel | Variance rules, manager review, evidence and closure. |
| LOG-07 Courier item register | S171 | Item, sender, recipient, confidentiality level, package/seal reference and status. |
| LOG-08 Dispatch manifests | S171 | Manifest creation, custody handoff, loading, dispatch, receipt confirmation and variance closure. |
| LOG-09 Logistics dashboard | S166 / S168_fuel / S171 | Vehicle readiness, fuel anomalies, dispatch in transit, overdue receipts and custody gaps. |

### Purchased / Integrated Components

- Fuel card/provider import or file feed.
- GPS/telematics platform, if procured now or in Phase 2.
- Barcode/RFID scanners for dispatch, if procured.
- External courier provider APIs where available.

### Key Events

| Event | Trigger |
|---|---|
| `sfl.logistics.vehicle-assigned.v1` | Vehicle assigned |
| `sfl.logistics.vehicle-unavailable.v1` | Compliance/service blocker detected |
| `sfl.logistics.fuel-anomaly-detected.v1` | Fuel variance rule fails |
| `sfl.logistics.dispatch-manifest-issued.v1` | Dispatch leaves custody point |
| `sfl.logistics.dispatch-receipt-confirmed.v1` | Recipient confirms receipt |
| `sfl.logistics.dispatch-exception-raised.v1` | Missing receipt, seal break or variance |

## 10. Wave 5 - Cross-Service Readiness Workflows

Cross-service workflows should be implemented as sagas/process managers. They should not use distributed transactions or cross-service database joins.

### Workflow A - Examination Hall Readiness

| Step | Owner Service | Description |
|---|---|---|
| 1 | Facilities | Generate hall readiness checklist for session/site/hall. |
| 2 | Asset | Resolve room, workstation, CCTV, access reader, fire panel and device references. |
| 3 | Facilities | Validate power/network/furniture/room readiness tasks. |
| 4 | Safety | Validate camera health, access reader status, life-safety panel status and open incidents. |
| 5 | Logistics | Validate dispatch/device delivery or transport support where needed. |
| 6 | Facilities | Compute readiness score and block if critical failures exist. |
| 7 | Reporting | Publish command dashboard status. |

### Workflow B - Emergency Incident Response

| Step | Owner Service | Description |
|---|---|---|
| 1 | Safety | Ingest panic/fire/alarm/HSE/CCTV/access event or manual report. |
| 2 | Safety | Classify severity and create incident. |
| 3 | Safety | Trigger emergency notification scenario if required. |
| 4 | Facilities | Receive facility impact tasks such as evacuation route, power, room closure or maintenance response. |
| 5 | Logistics | Receive vehicle/dispatch/responder movement tasks where needed. |
| 6 | Audit/Evidence | Preserve evidence metadata and action history. |
| 7 | Reporting | Update SOC/executive dashboard. |

### Workflow C - Secure Dispatch Chain-of-Custody

| Step | Owner Service | Description |
|---|---|---|
| 1 | Logistics | Create courier item or exam dispatch manifest. |
| 2 | Asset | Resolve package/device/seal/vehicle references. |
| 3 | Logistics | Record custody handoff and vehicle loading. |
| 4 | Safety | Add escort/security requirements where classified or exam-critical. |
| 5 | Logistics | Track receipt confirmation and exceptions. |
| 6 | Audit/Evidence | Store receipt, seal, variance and handoff evidence. |
| 7 | Reporting | Update dispatch dashboard and readiness impact. |

### Workflow D - CCTV Evidence Request

| Step | Owner Service | Description |
|---|---|---|
| 1 | Safety | User submits evidence request linked to incident/access event/complaint. |
| 2 | Safety | Approver reviews purpose, retention and permission. |
| 3 | Safety | Adapter requests footage export/reference from VMS. |
| 4 | Audit/Evidence | Store evidence metadata, hash and export log. |
| 5 | Safety | Link evidence reference to investigation. |
| 6 | Reporting | Update evidence request metrics. |

## 11. Wave 6 - Commissioning and Acceptance

| Gate | Tests | Evidence |
|---|---|---|
| G1 Service Readiness | Health checks, migrations, config, secrets, service auth | Deployment report |
| G2 API Contract Readiness | OpenAPI validation, consumer contract tests | API contract pack |
| G3 Event Readiness | Outbox, publish/consume, retry, dead-letter, idempotency | Messaging test report |
| G4 Security Readiness | RBAC, site scope, service auth, privileged action audit | Security test report |
| G5 Vendor Integration Readiness | Signed webhooks, schema validation, raw payload storage, replay | Integration test report |
| G6 Workflow Readiness | UAT for each Phase 1 workflow | UAT sign-off |
| G7 Evidence Readiness | Hashing, metadata, retention, export approval, access logs | Evidence governance report |
| G8 Edge / Degraded Mode Readiness | WAN-loss capture for approved workflows and reconciliation | Failover test report |
| G9 Operational Dashboard Readiness | Facilities, SOC, logistics and executive dashboards | Dashboard sign-off |
| G10 Go-Live Readiness | Training, SOPs, support model, open risk acceptance | Management approval |

## 12. Prompt Template for Future Build Tasks

Use this template when asking Codex to build each feature.

```text
Build [EPIC-ID] for [service-name].

Context:
- SRS references: [requirement IDs]
- System(s): [Sxxx names]
- Service boundary: [service owns these records and rules]
- External systems: [buy/integrate items, if any]

Functional scope:
- [capability 1]
- [capability 2]
- [capability 3]

Domain model:
- Aggregates/entities: [...]
- Value objects: [...]
- Status lifecycle: [...]

API:
- Endpoints: [...]
- Request/response DTOs: [...]
- Auth roles/site-scope rules: [...]

Persistence:
- Tables/migrations: [...]
- Indexes/constraints: [...]
- Outbox events: [...]

Events:
- Publishes: [...]
- Consumes: [...]
- Idempotency rules: [...]

Validation and errors:
- [rules]
- [error states]

Acceptance:
- Unit tests
- Integration tests
- API tests
- Event/outbox tests
- Security/site-scope tests
```

## 13. Recommended Prompt Order

Use this order for implementation prompts:

1. Scaffold all four services and Docker Compose foundation.
2. Add shared security/JWT/site-scope conventions.
3. Add migration, outbox, idempotency and event envelope patterns.
4. Build `sfl-asset-visibility-service` AVAMP-Lite.
5. Build `sfl-facilities-service` facility register.
6. Build `sfl-facilities-service` work orders and maintenance.
7. Build `sfl-facilities-service` room booking and readiness.
8. Build `sfl-safety-security-service` visitor lifecycle.
9. Build access-control event integration governance.
10. Build CCTV governance and evidence request workflow.
11. Build intrusion/life-safety event queues.
12. Build HSE incident/CAPA workflow.
13. Build emergency notification orchestration.
14. Build `sfl-fleet-logistics-service` vehicle register and assignment.
15. Build fuel/logbook reconciliation and anomaly workflow.
16. Build courier/dispatch chain-of-custody.
17. Build cross-service hall readiness saga.
18. Build cross-service emergency incident saga.
19. Build cross-service secure dispatch saga.
20. Build dashboards and reporting projections.
21. Run commissioning and acceptance test pack.

## 14. Non-Negotiable Build Principles

- Build fewer, stronger services rather than 13 tiny services.
- Build CLET-owned workflow and governance; buy specialist hardware/provider systems.
- Keep assets as a standalone service, starting with AVAMP-Lite.
- Use events for cross-service workflows.
- Never share databases between services.
- Never let vendor models leak into domain entities.
- Never store CCTV video or large evidence files in service databases.
- Make every write auditable.
- Make every consumer idempotent.
- Make every workflow site-scoped.
- Keep Phase 1 focused; defer full RFID/barcode inventory, GPS/telematics depth, BMS/IoT depth and commercial IWMS depth unless formally pulled forward.
