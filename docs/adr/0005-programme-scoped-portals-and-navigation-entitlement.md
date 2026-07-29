# ADR 0005 — Programme-scoped portals and navigation entitlement

- Status: Accepted (principle) / Open (mechanism — see "What is not yet decided")
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

## Current state — and the one thing that does not yet conform

`frontend/sfl-operations-ui` is, today, **the FTLMP portal**. It is served by
`sfl-fleet-logistics-service` from `/ui/`, its `directorate.module` says "Fleet & Logistics", and three
of its four navigation groups are FTLMP systems: fleet, fuel, dispatch.

**The fourth group, "Emergency notifications", is SSEMP.** It was built into this bundle because that is
where the shared component kit, design system and API client live, and building a second application for
one module would have forked the design system — the one thing the module playbook forbids. The module
itself is correct: it addresses `sfl-emergency-notification-service` on its own port through the
client's `service: 'emergency'` routing, holds no fleet data and shares no schema. **Only its placement
in the sidebar crosses a programme boundary**, and today every user of that bundle sees it.

That is the exact leak this ADR exists to prevent, and it is recorded here rather than quietly left,
because it will look deliberate to whoever reads the navigation file next.

## What is not yet decided

**The mechanism**, because it depends on work that has not been done: **IAM is not integrated.** There is
no centralised auth and no Zitadel wiring yet. Roles reach the services through `X-SFL-*` development
headers, and the dashboards read the same values from `VITE_SFL_ROLES` — so there is currently no
authenticated identity for a portal to scope itself against.

Two candidate mechanisms, to be chosen when IAM lands:

- **(a) One bundle, entitlement-filtered navigation.** Each nav section declares its programme; the shell
  renders only the sections the actor is entitled to. Cheap, reversible, and it is what IAM claims will
  drive anyway. Keeps one component kit and one design system.
- **(b) A bundle per programme.** The shared kit stays shared; each programme's service serves its own
  build. Correct long-term and better for blast radius, but it strands a single-module SSEMP portal until
  the other six SSEMP systems have screens.

**Neither is blocked on the other.** (a) is the near-term step and is compatible with (b) later: a
section that already declares its programme is a section that can be split out unchanged.

## Consequences

- `navigation.ts` gains a programme on every section, whichever mechanism is chosen. Doing that early
  costs one field and makes both paths open.
- The `directorate.module` label becomes per-programme rather than fixed to "Fleet & Logistics".
- Until IAM is integrated, any filtering is driven by development role headers and is therefore a
  **usability** control, not a security control. The services already authorise every call
  independently — S174 refuses an unentitled actor with `EMERGENCY_UNAUTHORIZED_SCOPE` whether or not
  the nav entry was shown — and that must stay true. **Navigation entitlement must never be relied on
  as the enforcement point.**
- When IAM lands, programme entitlement should come from the token, alongside the site scopes the
  services already read from `site_scopes`.
