# ADR 0004 — S174 Emergency Mass Notification as a separate deployable service

- Status: Accepted
- Date: 2026-07-27
- Deciders: SFL platform / F&L Health, Safety & Security Unit
- Supersedes/relates: [0002 build-buy hybrid integration strategy](0002-build-buy-hybrid-integration-strategy.md);
  Architecture Implementation Guide §0D-note (SSEMP split) and §0E (Life-Safety & Emergency Invariants).

> ADR number: `0003` was already taken by `0003-java-spring-boot-migration.md`, so this decision is
> recorded as `0004`.

## Context

The Phase 1 workplan (§4.2) and the microservices build workflow (SEC-07) place S174 Emergency Mass
Notification **inside** `sfl-safety-security-service` as the `emergencycomms` sub-context of SFL.SSEMP,
sharing that service's schema (`safety_security`) and a `/api/v1/security/emergency/...` path.

The Architecture Implementation Guide already flags the tension:

- §0D-note — *"SSEMP is too coarse … split into sub-contexts … Emergency Communications (mass
  notification). Life-safety's higher criticality and certification sensitivity must not be coupled to
  visitor badges."*
- §0E — the emergency path needs a **fast lane** (CT-20 / NFR-S2 latency), **break-glass** send without
  per-message approval, and a **degraded-mode** fallback if Core is unavailable.
- `sfl-safety-security-service` is currently a stub (no shared SSEMP runtime exists yet to embed into).

## Decision

Implement S174 as a **separate, independently deployable Spring Boot microservice**,
`sfl-emergency-notification-service` (artifact `sfl-emergency-notification-service`, package root
`gh.edu.clet.sfl.emergencynotification`, schema `emergency_notification`, local port 8095), still part of
**SFL.SSEMP / Emergency Communications**.

## Rationale

Emergency notification has availability, latency and blast-radius characteristics that differ materially
from the rest of SSEMP and justify an independent deployment lifecycle:

1. **Independent availability.** An emergency broadcast must be able to run when other SSEMP contexts
   (visitor, CCTV, HSE) are degraded or being deployed. Coupling would tie its uptime to unrelated churn.
2. **Fast-lane processing (CT-20 / NFR-S2).** The detection→notification path must bypass non-essential
   enrichment and meet a latency target; a dedicated service can size and isolate that path.
3. **Provider callback handling.** SMS/email/push/voice/siren/signage providers post delivery and
   acknowledgement callbacks at high, bursty volume; isolating this ingest protects other contexts.
4. **Retry / dead-letter handling.** Outbound fan-out needs its own outbox, backoff, dead-letter and
   privileged replay without competing with unrelated SSEMP traffic.
5. **Degraded-mode behaviour.** The emergency path must record an edge-triggered / provider-direct
   fallback when Core is unavailable — a service-level concern best owned by a dedicated deployable.
6. **Blast-radius isolation.** A fault, deploy or resource spike in visitor/CCTV/HSE must not be able to
   suppress an emergency broadcast, and vice-versa.

## Consequences / boundaries

- S174 remains part of **SFL.SSEMP / Emergency Communications**; the SSEMP platform grouping is unchanged
  conceptually — only the deployable is split, consistent with §0D-note.
- S174 integrates with safety-security, fleet-logistics, facilities and dashboards **through APIs and
  events only** (canonical `sfl.ssemp.*` events). No other service may read or write the
  `emergency_notification` schema; external references are held by ID only, with **no cross-schema FKs**.
- **SFL never sits inside the certified life-safety actuation path.** Fire/intrusion vendor systems sound
  alarms and trigger evacuation hardware independently and remain authoritative and certified. SFL
  **observes, governs, records and supplements** (mass notification, dashboards, evidence). The
  `LifeSafetyEventPort` is observe-only and `AccessControlLockdownPort`/`CctvEvidencePort` are seams only.
- **Break-glass** pre-authorized templates + roles may activate during a declared emergency **without**
  per-message approval; approval is recorded **after the fact** and gates closure. Approval-before-send
  applies only to routine notices and must never gate a life-safety broadcast.
- The primary REST base path is `/api/v1/emergency`; `/api/v1/security/emergency` may be offered later
  only as a documented compatibility alias if a consumer requires the original workplan path.
- Controlled deviation: the workplan §4.2 grouping is preserved as the *conceptual* boundary
  (`emergencycomms`), but the *deployable* is separated. This ADR is the record of that deviation.
