# Vendor and Hardware Procurement Integration Checklist

## Purpose

Before buying any CCTV, access-control, fire-safety, intrusion, fleet, fuel, visitor, notification, courier or facility hardware/software package, the vendor must be assessed for integration readiness.

SFL should be able to retrieve, display, audit and act on data from the vendor system without becoming tightly coupled to that vendor.

## Mandatory Checks

| Area | Required Question | Required Outcome |
|---|---|---|
| API access | Does the product provide a documented REST API, SDK, webhook, message export or database export? | At least one approved integration method must exist. |
| Authentication | Does the API support secure authentication such as OAuth2, client credentials, signed tokens, mTLS or strong API keys? | Shared passwords or manual exports are not acceptable for production integrations. |
| Authorization | Can API access be restricted by role, permission and scope? | SFL service accounts must use least privilege. |
| Webhooks/events | Can the system push events such as alarms, device status, access decisions, delivery updates or vehicle locations? | Event push is preferred for operational alerts. |
| Polling | If webhooks are not available, does the API support safe incremental polling? | Polling must support timestamps, cursors or version tokens. |
| Data export | Can operational data be exported in a structured format? | CSV-only exports are acceptable only for low-risk batch imports. |
| Evidence access | Can the system provide evidence references, clips, snapshots or exported documents with retention metadata? | Evidence must include source, timestamp, hash or reference ID where possible. |
| Device health | Can the system report device online/offline/fault status? | Device health is required for dashboards. |
| Audit logs | Does the vendor system keep audit logs for user/admin/API actions? | Audit logs must be exportable or queryable. |
| Sandbox | Is there a test/sandbox environment or simulator? | Required before live integration. |
| Documentation | Are API docs, event schemas, error codes, rate limits and sample payloads available? | Documentation must be stored with the integration pack. |
| Rate limits | Are API rate limits documented and acceptable? | SFL adapter must respect limits and use retries/backoff. |
| Licensing | Is API/webhook/SDK access included in licensing? | Hidden API licensing must be confirmed before purchase. |
| Data ownership | Can SFL export or retain the data it needs? | Vendor lock-in must be assessed. |
| Security | Does the product support encryption in transit and secure credential rotation? | Required for production. |
| Compliance | Does the product support retention, privacy, biometric handling and audit requirements? | Required for CCTV, biometrics and life-safety data. |
| Support | Does the vendor support integration incidents and version changes? | SLA and support channel must be agreed. |

## Preferred Integration Order

1. Webhook/event feed for operational alerts.
2. REST API for commands, queries and reconciliation.
3. SDK only when API is insufficient and licensing is clear.
4. Structured export for low-frequency reporting/import.
5. Direct database access only by exception and never as the default integration model.

## Procurement Gate

A vendor product should not be approved for SFL Phase 1 integration unless it passes the mandatory integration checks or a risk exception is signed off.

Every purchased system must have an integration owner, service account strategy, test environment, sample payloads, event mapping, data-retention plan and contract-test plan.
