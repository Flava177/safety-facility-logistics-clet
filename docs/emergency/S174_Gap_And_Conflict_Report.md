# S174 Gap and Conflict Report

Reconciles the S174 SRS (main authority) with the Phase-1 workplan, the architecture implementation guide
(§0D-note, §0E), the phase-1 classification, the microservices build workflow (SEC-07/08) and the event
catalog, and records the resolved decision for each conflict. Resolutions follow SRS wording and §0E
invariants.

| # | Conflict / ambiguity | Sources | Resolution |
|---|---|---|---|
| C-01 | Module name: SRS "Emergency **Mass Notification** System"; conversational "Emergency Mass **Communication**". | SRS S174 header; chat | Use **SRS wording** ("Emergency Mass Notification") in code, docs, API titles and Swagger. |
| C-02 | Deployable placement: workplan §4.2 / SEC-07 place S174 **inside** `sfl-safety-security-service` (`emergencycomms`, schema `safety_security`); this build makes it a **separate deployable**. | Workplan §4.2; build workflow SEC-07; Arch §0D-note/§0E | Implement `sfl-emergency-notification-service` (schema `emergency_notification`, port 8095) as a separate deployable, still SFL.SSEMP. Recorded in [ADR 0004](../adr/0004-s174-emergency-notification-as-separate-service.md). |
| C-03 | Approval model: SRS-S174-02 "request approval / approve" vs §0E "break-glass without per-message approval". | SRS S174-02; Arch §0E | **Routine** activations require approval-before-send; **break-glass** (declared emergency, authorised role + pre-authorised template) sends **without** pre-approval and records **after-the-fact** approval. Break-glass must never be gated by routine approval. |
| C-04 | Governance vs actuation: SFL "activates channels" vs certified life-safety systems. | Classification (Hybrid); Arch §0E | SFL **governs** activation, audience selection, incident linkage, acknowledgement tracking and audit; vendors deliver messages. **SFL never sits in the certified life-safety actuation path** — `LifeSafetyEventPort` observe-only; `AccessControlLockdownPort`/`CctvEvidencePort` are seams only. |
| C-05 | Provider-neutral channels vs real vendor integration. | Vendor adapter guide; ADR 0002 | Provider-neutral `NotificationGatewayPort` + recorded/simulator adapters first; vendor SDKs/types stay inside infrastructure adapters, never in domain/application. Channels: SMS, Email, Push, Voice, Siren, Digital Signage. |
| C-06 | Core mode vs degraded/edge fallback. | Arch §0E; §0B | Core path is outbox-driven fan-out; a **degraded-mode** activation records the fallback path (edge-triggered / provider-direct) and reconciliation metadata so it is reconstructable when Core returns. |
| C-07 | Base path: SRS/this build `/api/v1/emergency`; workplan alias `/api/v1/security/emergency`. | Workplan §4.2; chat | Primary `/api/v1/emergency`; `/api/v1/security/emergency` only as a documented compatibility alias if a consumer needs it (not built unless justified). |
| C-08 | Event names: workflow doc lists `sfl.security.emergency-notification-sent.v1`; catalog uses `sfl.ssemp.*`. | Build workflow; event catalog | Use the **canonical event-catalog** `sfl.ssemp.*` names; the two pre-seeded events are reused verbatim; justified lifecycle events are added under `sfl.ssemp.*` and documented in the catalog. |
| C-09 | Fast lane (CT-20 / NFR-S2 latency target "TBC"). | Arch §0E; CT-20 | Represent the fast-lane path explicitly and measure it with a recorded adapter/timer; the numeric latency target stays a commissioning concern (NFR-S2 TBC). |
| C-10 | Shared foundations: audit/outbox/inbox/evidence/runtime-config live **per-service** (not in `sfl-service-common`); safety-security is a stub. | Repo inspection | The new service carries its own foundations following the established SFL patterns (mirroring the built-out sibling services); only the shared kernel (envelope, actor, RBAC enums) comes from `sfl-service-common`. |
| C-11 | Integration boundaries with safety-security, fleet-logistics, facilities, dashboards. | Workplan; classification | Integration by APIs/events only; incident linkage by ID via `IncidentLinkPort`; no cross-schema FKs; no other service writes `emergency_notification`. |

## Notes

- The SRS S174-01…05 requirement text is the shared operational-records / workflow / evidence-audit /
  integration / dashboard template applied to Emergency Notification; every SRS error-state string is
  treated as user-facing contract and asserted verbatim in tests.
- No unresolved critical or high-severity gap remains open at implementation start.
