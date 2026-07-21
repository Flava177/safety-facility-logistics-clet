# Phase 1 System Classification

## Purpose

Phase 1 covers 13 fast-track systems across the Safety, Facilities and Logistics domain. The platform must not become 13 disconnected applications, and it must not attempt to rebuild specialist hardware systems that are better delivered by certified vendor products.

This classification guides delivery decisions before each module is built.

## Decision Model

Use one of three delivery decisions for each Phase 1 system.

| Decision | Meaning | When to Use |
|---|---|---|
| Build | The SFL platform owns the workflow, rules, screens, data model and operational process. | The process is internal, approval-heavy, workflow-heavy, audit-heavy or unique to the organization. |
| Buy and Integrate | A specialist vendor product owns the device operation or hardware control. SFL retrieves data and displays it through approved APIs, SDKs, exports or webhooks. | CCTV, access control, fire panels, intrusion alarms, telematics, fuel devices, mass notification gateways and similar systems. |
| Hybrid | The vendor system captures raw/device data while SFL owns the operational workflow, dashboard, audit, escalation, reporting and cross-system event flow. | Most device-assisted operations where SFL needs governance and consolidated visibility. |

## Phase 1 Classification Matrix

| ID | System | Core Platform | Delivery Decision | SFL Ownership | External/Vendor Ownership | Integration Direction |
|---|---|---|---|---|---|---|
| S152 | Computer-Aided Facility Management / IWMS | SFL.IFIMP | Hybrid | Facility register, spaces, zones, readiness model, operational dashboards, events and reporting. | Optional IWMS/CAFM package for advanced space planning, drawings and facility records. | Pull/push facility master data through API/export. Keep SFL location IDs stable. |
| S153 | Computerized Maintenance Management System | SFL.IFIMP | Build first, adapter-ready | Fault reports, work orders, assignments, SLA tracking, approvals, closure evidence and audit trail. | Optional CMMS vendor if procured later for advanced maintenance management. | Build core Phase 2 vertical slice in SFL. Add CMMS adapter only if vendor becomes source of record. |
| S159 | Room and Resource Booking System | SFL.IFIMP | Build | Booking requests, conflict checks, approvals, setup tasks, readiness checks and room/resource calendar. | Optional calendar/email system integration. | Publish booking events and integrate calendar notifications where approved. |
| S160 | Visitor Management System | SFL.SSEMP | Hybrid | Visitor pre-registration, host approval, visit purpose, badge request, check-in/out audit, roll-call views. | Badge printer, kiosk, turnstile or access-control product where required. | SFL owns visitor workflow; adapter syncs badge/access permissions and receives check-in/device events. |
| S160a | Physical Access Control Integration | SFL.SSEMP | Buy and Integrate | Access policy visibility, exceptions, overrides, investigation links and dashboarding. | Door controllers, biometric/card readers, access-control server and device enrollment. | Integrate through vendor API/webhook/export. Never hard-code vendor SDKs in domain modules. |
| S161 | CCTV / Video Management Integration | SFL.SSEMP | Buy and Integrate | Camera inventory reference, camera health display, incident linkage, evidence request workflow and audit. | Cameras, NVR/VMS, recording, retention, playback, clip export and device management. | Integrate with VMS API/SDK. Store evidence references, not raw video, unless approved. |
| S162 | Intrusion Detection and Alarm Monitoring | SFL.SSEMP | Buy and Integrate | SOC queue, alarm acknowledgement workflow, escalation, incident linkage and reporting. | Alarm panels, sensors, zones and certified monitoring equipment. | Receive alarms as webhooks/API polling/messages. Normalize to SFL alarm events. |
| S162a | Fire-Safety and Life-Safety Monitoring | SFL.SSEMP | Buy and Integrate | Fire/life-safety event visibility, emergency workflow trigger, drill records, inspection follow-up and audit. | Fire panels, smoke detectors, panic devices, sirens and certified safety systems. | Integrate read-only or approved event feeds. Do not replace certified life-safety control logic. |
| S163 | Health and Safety Incident / Near-Miss Reporting | SFL.SSEMP | Build | Incident reports, near-miss capture, investigation, corrective actions, risk rating, evidence and dashboards. | Optional medical/insurance/regulator integrations if required. | Build workflow in SFL and publish incident/corrective-action events. |
| S166 | Fleet and Vehicle Management | SFL.FTLMP | Hybrid | Vehicle register, compliance, assignments, readiness, service state and operational reporting. | Optional GPS/telematics, tracker, dashcam or fleet device vendor. | SFL owns fleet workflow; adapter receives location/health/usage events. |
| S168_fuel | Fuel Management and Driver Logbooks | SFL.FTLMP | Hybrid | Fuel reconciliation, driver logbooks, exception review, approvals and audit trail. | Fuel pump/card/vendor platform, odometer capture devices and telematics. | Pull fuel transactions and reconcile with driver/vehicle records. |
| S171 | Mailroom / Courier and Dispatch Tracking | SFL.FTLMP | Build with optional integrations | Courier register, dispatch chain-of-custody, sealed items, receipt confirmation and dashboards. | Barcode scanners, courier provider APIs or label printers. | Build core workflow in SFL. Integrate carrier APIs and scanner events when available. |
| S174 | Emergency Mass Notification | SFL.SSEMP | Hybrid | Activation approval, audience selection, incident linkage, acknowledgement tracking and audit. | SMS/email/push/voice/siren/signage gateway providers. | SFL governs activation; vendor sends messages and returns delivery/acknowledgement status. |

## Role of SFL.AVAMP

SFL.AVAMP acts as the Phase 1 asset visibility and device reference layer. It supports all 13 systems by providing stable references for assets and devices such as cameras, access readers, fire panels, vehicles, rooms, fuel devices, dispatch items and safety equipment.

AVAMP should not become a separate duplicate asset system in Phase 1. It should provide the minimum common reference model needed for integration, evidence, dashboards and device health.

## Implementation Rule

A domain module must never depend directly on a vendor API, vendor SDK, RabbitMQ client, Redis client, Keycloak SDK or PostgreSQL implementation detail.

The allowed flow is:

```text
Domain/Application module
-> Port/interface
-> Infrastructure adapter
-> Vendor/API/SDK/device system
```

Events and read models must be used to share data across modules. Direct module-to-module business calls should be avoided except through approved contracts and only where the architecture explicitly allows it.
