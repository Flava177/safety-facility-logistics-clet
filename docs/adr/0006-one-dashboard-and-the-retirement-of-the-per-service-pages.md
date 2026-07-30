# ADR 0006 — One dashboard, and the retirement of the per-service pages

- Status: **Proposed.** Nothing has been deleted. Decision (1) — the naming — is applied. Decisions
  (2) and (3) need a call from the directorate.
- Date: 2026-07-30
- Deciders: SFL platform / F&L directorate
- Relates: [0005 programme-scoped portals](0005-programme-scoped-portals-and-navigation-entitlement.md);
  [microservices-realignment](../architecture/microservices-realignment.md);
  [SFL Operations UI module playbook](../frontend/SFL_Operations_UI_Module_Playbook.md)

## Context

There are **five** hand-rolled static pages served directly by the Spring Boot services, not four. The
count in earlier notes was wrong: it missed the one that matters most.

| # | Served by | Route | Title today | Size | Superseded by the dashboard? |
| --- | --- | --- | --- | --- | --- |
| 1 | `sfl-facilities-service` (8091) | `/` | SFL Operations Console | 1 406 lines | **No** |
| 2 | `sfl-fleet-logistics-service` (8093) | `/fleet/` | SFL Fleet Operational Console | 411 lines | Yes |
| 3 | `sfl-fleet-logistics-service` (8093) | `/fuel/` | Fuel Management and Driver Logbooks | 527 lines | Yes |
| 4 | `sfl-fleet-logistics-service` (8093) | `/dispatch/` | Mailroom / Courier and Dispatch Tracking | 292 lines | Yes |
| 5 | `sfl-emergency-notification-service` (8095) | `/emergency/` | Emergency Mass Notification | 158 lines | Yes |

Alongside them, `sfl-fleet-logistics-service` packages the React **SFL Operations dashboard** into its
own static resources at `/ui`, built from `frontend/sfl-operations-ui`. It is the only service that
does; the dashboard's emergency screens are served from 8093 and call 8095 across origins, which is
why 8095's CORS list matters.

So the same three systems — fleet, fuel, dispatch — currently have two user interfaces each, and
emergency has two. That is the state this record is about.

### The word

These are called consoles throughout the codebase and the documents. They should not be. **Everything
a user opens is a dashboard.** A console is a thing an administrator types into; a dashboard is a thing
an operator reads and acts on, which is what all of these are and all of these should remain. The
naming is not cosmetic — "console" quietly licensed the per-service, admin-shaped, one-page-per-service
design that ADR 0005 has already ruled against.

## Decision

### 1. Everything is a dashboard — applied

The word "console" is retired from user-visible text, page titles, headings, READMEs, code comments and
documentation. The SFL Operations dashboard is *the* dashboard; anything still served per service is a
programme dashboard until it is folded in.

### 2. Retire pages 2–5 by redirecting them — recommended, not done

The four superseded pages should stop being separate interfaces. The cheapest honest way to do that is
**not** deletion: replace each `index.html` with a redirect to the corresponding dashboard route.

| Retire | Redirect to |
| --- | --- |
| `/fleet/` | `/ui/fleet` |
| `/fuel/` | `/ui/fuel` |
| `/dispatch/` | `/ui/dispatch` |
| `/emergency/` (8095) | `http://<fleet-host>/ui/emergency` — configurable, since 8095 does not serve the bundle |

That removes 1 388 lines of divergent interface, keeps every URL printed in every runbook working, and
needs no coordinated documentation change. Deleting the directories outright is the follow-up once
nothing points at them.

**Why they are safe to retire.** The dashboard does not merely duplicate them; on every screen this
round touched, the dashboard is the more truthful of the two. The static pages were built against the
same endpoints in the same weeks, so they carry the same client-side aggregation the dashboard has just
had removed — the fifty-vehicle compliance fan-out, the browser-side day bucketing, the page-scoped
filters. Keeping them means keeping two answers to the same question, one of which is worse.

They are already disagreeing. Opened side by side on 30 July 2026 against the same running service,
`/fleet/` reported every indicator as zero while `/ui/fleet` reported three vehicles, one expired
compliance document and one readiness blocker. Neither is wrong: the static page defaults its site
scope to `ACCRA` and the dashboard to `CLET-HQ`. But the static page gives no hint that its zeros are a
scoping artefact — it prints the raw snapshot JSON into a `<pre>` block and leaves the reader to work it
out. That is the drift the two-interface arrangement produces, and it produces it in the direction that
matters: a screen that says nothing is wrong when it has not looked.

### 3. Keep page 1 until IFIMP has dashboard screens — recommended

`sfl-facilities-service`'s page is **not** superseded and must not be retired with the others. It is the
only user interface for the whole of SFL.IFIMP:

- create and browse sites, buildings, floors and rooms
- report a facility fault; the fault register
- work order controls and the work order list
- register and browse asset references
- the access snapshot and recent-records panels

The React dashboard has **four modules** — `fleet`, `fuel`, `dispatch`, `emergency` — and no facilities
module at all. Against ADR 0005's four programmes that leaves the coverage at:

| Programme | Dashboard coverage |
| --- | --- |
| **FTLMP** | Complete — 5 navigation groups |
| **SSEMP** | Emergency notifications only. Visitor, access control, CCTV, intrusion, fire and HSE have no screens |
| **IFIMP** | **None.** Only the static page on 8091 |
| **AVAMP** | **None** |

Deleting page 1 would remove the only way to reach a programme module. Building IFIMP screens in the
dashboard is the real fix, and it is a programme of work rather than a cleanup — so page 1 stays,
renamed, and is written down as a known temporary interface rather than left to look like a peer of the
dashboard.

It has one defect worth recording while it stays: it loads Bootstrap 5.3.3 and Bootstrap Icons 1.11.3
from `cdn.jsdelivr.net` at runtime. It therefore does not work on an isolated network and takes a
third-party runtime dependency the dashboard does not have. That is an argument for replacing it,
not for patching it.

## What retiring pages 2–5 would break

Everything below was checked, not assumed.

**One automated test.** `FleetPostgresEndToEndTest` (line 69) fetches `/fleet/index.html` and asserts
the body contains `Fleet operational console`. A redirect changes that response, so the assertion has
to move to the dashboard's own `/ui/index.html` — which is the better check anyway, since it is what
users are served. This is the only test that touches any of the five.

**Routing and security configuration.**

- `FleetWebConfiguration` — six view controllers forwarding `/fleet`, `/fleet/`, `/fuel`, `/fuel/`,
  `/dispatch`, `/dispatch/` to their `index.html`.
- The fleet security chain permits `/fleet/**`, `/fuel/**`, `/dispatch/**` unauthenticated.
- `EmergencyWebConfiguration` — two view controllers for `/emergency` and `/emergency/`.
- The emergency security chain permits the same.

A redirect keeps all of it valid unchanged. A deletion means removing each.

**Eleven documentation references**, all of which would need repointing on deletion and none of which
would need touching on a redirect:

| Document | Reference |
| --- | --- |
| `README.md` | `http://localhost:8093/ui/`; `http://localhost:8095/emergency/` |
| `frontend/sfl-operations-ui/README.md` | `http://localhost:8093/ui/` |
| `services/sfl-fleet-logistics-service/README.md` | `/dispatch/`; `/fuel/` |
| `services/sfl-emergency-notification-service/README.md` | `/emergency/` |
| `docs/fleet/S166_Operations_And_Verification_Guide.md` | `/fleet/`; `/fleet/index.html` |
| `docs/dispatch/S171_Final_Implementation_Report.md` | `/dispatch/` |
| `docs/dispatch/S171_Operations_And_Verification_Guide.md` | `/dispatch/` |
| `docs/emergency/S174_Operations_And_Verification_Guide.md` | `/emergency/` |
| `docs/emergency/S174_Emergency_Frontend_Gap_Register.md` | the asset filenames |

**Nothing else.** No deployment script, container definition, reverse-proxy rule or dashboard link
references any of the five. The dashboard never links out to them.

## The one real capability that would be lost

Pages 2–5 carry a small actor panel, and page 1 carries a full one: user, roles and sites can be typed
into the page and the next request carries them. The dashboard reads its actor from build-time
`VITE_SFL_*` variables, so changing role means editing `.env` and restarting Vite.

That mattered little when navigation was the same for everyone. **It matters now**, because ADR 0005
made navigation depend on the actor's roles: checking that a driver does not see CCTV, or that a
facilities manager sees only IFIMP, is exactly the kind of check the static pages made cheap and the
dashboard currently makes tedious.

So the recommendation carries a precondition: **add a development-only actor switcher to the dashboard
before retiring pages 2–5.** It writes the four `X-SFL-*` header values to session storage, reads them
in `shared/api/client.ts` ahead of the environment defaults, and is compiled out of a production build.
That is a small piece of work and it should not be skipped, because otherwise a real testing capability
is lost in the name of tidiness.

## Consequences

- Two interfaces per system becomes one. The dashboard is the interface; a service serves an API,
  Swagger and the bundle.
- IFIMP and AVAMP are visibly uncovered rather than quietly half-covered by a page that looks like a
  peer of the dashboard. That is the honest position and it makes the next piece of work obvious.
- The word "console" stops appearing anywhere a user can see, so the per-service, admin-shaped design
  it licensed stops looking like the house style.
- One test assertion, eight configuration entries and eleven document references are the entire cost of
  the change, and a redirect defers all but the test.

## Not decided here

- **When** IFIMP and AVAMP dashboard screens get built. That is a workplan question.
- Whether the emergency service should also package the dashboard bundle so it can serve `/ui` when
  8093 is not deployed. It is a real question for a programme-by-programme launch — SSEMP could then
  launch without FTLMP — and it is not blocking anything today.
