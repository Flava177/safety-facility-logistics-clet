# ADR 0005 — Programme-scoped portals and navigation entitlement

- Status: Accepted — principle and mechanism (a). Mechanism (b) deferred, see below.
- Date: 2026-07-29
- Deciders: SFL platform / F&L directorate
- Relates: [0004 S174 as a separate deployable](0004-s174-emergency-notification-as-separate-service.md);
  [phase-1-system-classification](../phase-1-system-classification.md);
  [microservices-realignment](../architecture/microservices-realignment.md)

## Context

Phase 1 is **13 systems grouped under 4 programme modules**, delivered as **5 deployable services**.
Those three numbers do not line up one-to-one, and the mismatch is the whole point of this record:

| Programme | Systems | Deployable service(s) |
| --- | --- | --- |
| **SFL.IFIMP** — facilities & infrastructure | S152 CAFM/IWMS, S153 CMMS, S159 Room & resource booking | `sfl-facilities-service` |
| **SFL.SSEMP** — safety, security & emergency | S160 Visitor, S160a Access control, S161 CCTV/VMS, S162 Intrusion & alarms, S162a Fire & life safety, S163 HSE incident | `sfl-safety-security-service` |
| **SFL.SSEMP** — emergency communications | S174 Emergency mass notification | `sfl-emergency-notification-service` *(split by ADR 0004)* |
| **SFL.FTLMP** — fleet, transport & logistics | S166 Fleet & vehicle, S168 Fuel & driver logbooks, S171 Courier & dispatch | `sfl-fleet-logistics-service` |
| **SFL.AVAMP** — asset & device visibility | *(cross-cutting reference layer for all 13, not one of the 13)* | `sfl-asset-visibility-service` |

So: **launching a programme means starting its services, not one service.** FTLMP is one deployable
carrying three systems. SSEMP is two deployables carrying seven systems. A programme is a *user-facing
grouping*; a service is a *deployment unit*; the two are related but not the same thing, and neither
is a screen.

The operational reason this matters is not architectural tidiness. It is that a driver or a head of
fleet signing in should see fleet, fuel and dispatch — **not CCTV access management, not intrusion
detection, not visitor badges.** A dashboard that shows a user seven systems they have no business in
is worse than one that shows three: it buries the work they came to do, and it advertises capability
they will be refused if they click it.

## Decision

**Navigation is scoped by programme entitlement, not by what happens to be deployed behind it.**

1. A user sees the programmes they are entitled to, and within a programme, the systems their roles
   grant. A fleet operator sees FTLMP. A SOC operator sees SSEMP. **A manager or superadmin sees
   everything** — that is the exception the rule exists to make meaningful, not a contradiction of it.
2. **Deployment topology is invisible to the operator.** That S174 is its own service and S166/S168/S171
   share one is an availability decision (ADR 0004), not something a user should be able to infer from
   a sidebar. Two services in one programme appear as one programme; three systems in one service
   appear as three systems.
3. **Programme membership is a property of the system, not of the service it happens to ship in.** S174
   is SSEMP. It stays SSEMP in every user-facing surface regardless of which deployable serves it, and
   regardless of which bundle its screens are built into today.
4. Cross-programme reach is by API and event only, as ADR 0004 and the realignment plan already require.
   A screen in one programme must not read another programme's database, and a nav entry must not be the
   only thing standing between a user and data they are not entitled to — the **service** authorises
   every call, and the nav merely stops offering work that will be refused.

## Mechanism — decided

**(a) One bundle, entitlement-filtered navigation.** Implemented.

- `shared/layout/programmeModel.ts` holds the role → programme mapping and the derivation. It imports
  **nothing** — no config, no `import.meta.env`, no React — which is what lets "who sees what" be
  exercised directly rather than only through a running application. 25 cases are checked against it,
  including every persona the rule exists for, both fake roles that were in `.env`, and the shipped
  default list.
- `shared/layout/programmes.ts` is the thin part: read the actor's roles, apply an optional
  `VITE_SFL_PROGRAMMES` override, expose `entitledTo` and `portalLabel`.
- `NavSection` gains `programme`. The sidebar renders only entitled sections; `RequireProgramme`
  refuses the routes of an unentitled programme, so a bookmark or a typed address is answered properly
  instead of producing a screen full of `403`s.
- The landing route is the actor's **first entitled destination**, not the fleet dashboard — which was
  only ever the right answer for a fleet user.

**Entitlement is derived from roles, not from a separate list**, because that is what IAM will do: a
role already implies a programme, and a second list would drift out of step with the first. When IAM
lands, `programmesFor(roles)` is replaced by a claim and nothing else changes.

**(b) A bundle per programme** stays deferred. It is the better long-term answer for blast radius, but
it strands a single-module SSEMP portal until the other six SSEMP systems have screens — and (a) does
not block it: a section that already declares its programme can be split out unchanged.

## Current state — and the one thing that no longer leaks

`frontend/sfl-operations-ui` is served by `sfl-fleet-logistics-service` from `/ui/`, and three of its
four navigation groups are FTLMP systems: fleet, fuel, dispatch. It had a fixed `directorate.module`
label reading "Fleet & Logistics", which was only ever true for a fleet user; that constant is gone and
the shell now names whichever programme the actor is actually looking at.

**The fourth group, "Emergency notifications", is SSEMP.** It was built into this bundle because that is
where the shared component kit, design system and API client live, and building a second application for
one module would have forked the design system — the one thing the module playbook forbids. The module
itself is correct: it addresses `sfl-emergency-notification-service` on its own port through the
client's `service: 'emergency'` routing, holds no fleet data and shares no schema. **Only its placement
was the problem**, and it is now declared `programme: 'SSEMP'` and filtered accordingly: a fleet
operator no longer sees it, and `/emergency` answers with an explanation rather than the screen.

The bundle therefore still *contains* four modules while showing a single-programme user only their
own. That is the intended end state of mechanism (a), and it is why (b) can wait.

## What is still open

**IAM.** There is no centralised auth and no Zitadel wiring. Roles reach the services through `X-SFL-*`
development headers and the dashboards read the same values from `VITE_SFL_ROLES`, so entitlement today
is derived from a header the client sets for itself. That is acceptable for what this is — see the
consequence below — and it is the one piece that must change before any of this means anything about
identity.

## Consequences

- Every `NavSection` carries a programme. One field, and it is the same field mechanism (b) would need,
  so choosing (a) now closes nothing off.
- `directorate.module` is removed. The portal names itself from entitlement via `portalLabel()`,
  because a fixed label was a claim about the reader that the application had no basis for.
- Filtering is driven by development role headers and is therefore a **usability** control, not a
  security control — and it would remain one even with IAM, because a hidden link protects nothing.
  The services already authorise every call independently: S174 refuses an unentitled actor with
  `EMERGENCY_UNAUTHORIZED_SCOPE` whether or not the nav entry was shown. **Navigation entitlement must
  never be relied on as the enforcement point.** `RequireProgramme` says so in its own docblock, where
  somebody will be reading it at the moment they are tempted to.
- Two role names in the shipped configuration were **not real** `SflRole` constants —
  `FLEET_DISPATCHER` and `FLEET_AUDITOR`. Every service silently dropped them, so they had been
  granting nothing. Replaced with `DISPATCH_CONTROLLER` and `FLEET_REPORTING_VIEWER`. Worth knowing
  that an unrecognised role fails quietly, in the services and here alike.
- When IAM lands, programme entitlement should come from the token, alongside the site scopes the
  services already read from `site_scopes`.
