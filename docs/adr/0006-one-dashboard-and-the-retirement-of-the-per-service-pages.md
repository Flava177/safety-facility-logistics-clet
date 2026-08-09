# ADR 0006 - One dashboard, and the retirement of the per-service pages

- Status: **Accepted and implemented**, 30 July 2026. All three decisions are in effect. **All five**
  pages are now redirects - decision 3 kept the facilities page only until IFIMP had dashboard
  screens, and S152 is those screens, so it retired on the same day under the condition it was kept
  under (see [Decision 3, discharged](#3-keep-page-1-until-ifimp-has-dashboard-screens--discharged)).
  The actor switcher that decision (2) depended on is built.
- Date: 2026-07-30
- Deciders: SFL platform / F&L directorate
- Relates: [0005 programme-scoped portals](0005-programme-scoped-portals-and-navigation-entitlement.md);
  [microservices-realignment](../architecture/microservices-realignment.md);
  [SFL Operations UI module playbook](../frontend/SFL_Operations_UI_Module_Playbook.md)

## Context

There are **five** hand-rolled static pages served directly by the Spring Boot services, not four. The
count in earlier notes was wrong: it missed the one that matters most.

| # | Served by | Route | Title when this was written | Size | Superseded by the dashboard? |
| --- | --- | --- | --- | --- | --- |
| 1 | `sfl-facilities-service` (8091) | `/` | SFL Operations Console | 1 406 lines | **No** |
| 2 | `sfl-fleet-logistics-service` (8093) | `/fleet/` | SFL Fleet Operational Console | 411 lines | Yes |
| 3 | `sfl-fleet-logistics-service` (8093) | `/fuel/` | Fuel Management and Driver Logbooks | 527 lines | Yes |
| 4 | `sfl-fleet-logistics-service` (8093) | `/dispatch/` | Mailroom / Courier and Dispatch Tracking | 292 lines | Yes |
| 5 | `sfl-safety-security-service` (8092) | `/emergency/` | Emergency Mass Notification | 158 lines | Yes |

Page 1 is now titled **SFL Facilities Dashboard**; pages 2–5 are redirects. The titles above are what
they were called when the problem was written down, kept so the record reads against the state it
describes.

Alongside them, `sfl-fleet-logistics-service` packages the React **SFL Operations dashboard** into its
own static resources at `/ui`, built from `frontend/sfl-operations-ui`. It is the only service that
does; the dashboard's emergency screens are served from 8093 and call 8092 across origins, which is
why 8092's CORS list matters. (That port was 8095 until S174's deployable was folded into SSEMP on
5 August 2026; the path `/emergency/` and the API base `/api/v1/emergency` did not move.)

So the same three systems - fleet, fuel, dispatch - had two user interfaces each, and emergency had
two. That is the state this record was written against.

### The word

These are called consoles throughout the codebase and the documents. They should not be. **Everything
a user opens is a dashboard.** A console is a thing an administrator types into; a dashboard is a thing
an operator reads and acts on, which is what all of these are and all of these should remain. The
naming is not cosmetic - "console" quietly licensed the per-service, admin-shaped, one-page-per-service
design that ADR 0005 has already ruled against.

## Decision

### 1. Everything is a dashboard - applied

The word "console" is retired from user-visible text, page titles, headings, READMEs, code comments and
documentation. The SFL Operations dashboard is *the* dashboard; anything still served per service is a
programme dashboard until it is folded in.

### 2. Retire pages 2–5 by redirecting them - done

The four superseded pages should stop being separate interfaces. The cheapest honest way to do that is
**not** deletion: replace each `index.html` with a redirect to the corresponding dashboard route.

| Retire | Redirect to |
| --- | --- |
| `/fleet/` | `/ui/fleet` |
| `/fuel/` | `/ui/fuel` |
| `/dispatch/` | `/ui/dispatch` |
| `/emergency/` (8092) | `http://<fleet-host>/ui/emergency` - configurable, since 8092 does not serve the bundle |

That removes 1 388 lines of divergent interface, keeps every URL printed in every runbook working, and
needs no coordinated documentation change. Deleting the directories outright is the follow-up once
nothing points at them.

**As built.** Each route redirects with a 302 from a Spring view controller, and each directory keeps a
single `index.html` - a notice page naming the screen that replaced it, with a two-second meta refresh
so it can be read. The eight stylesheet and script files are deleted; that is where the 1 388 lines
went.

The fleet redirects are registered **only when the dashboard bundle is present**. Sending somebody to
`/ui/fleet` when `/ui` is not being served would replace a working page with a bare 404, so without the
bundle the request falls through to the notice page, which says where the screens went and how to build
them. The emergency service never packages the bundle, so its target is `sfl.dashboard.base-url`
(default `http://localhost:8093/ui`); behind a gateway that becomes a same-origin prefix and nothing
else changes.

`/fleet/index.html` - the spelling in the S166 guide and in the e2e test - keeps working, and the test
now asserts the notice page names `/ui/fleet`. It asserts the page rather than the redirect on purpose:
the redirect is conditional on the bundle and the test has to pass either way.

**Why they are safe to retire.** The dashboard does not merely duplicate them; on every screen this
round touched, the dashboard is the more truthful of the two. The static pages were built against the
same endpoints in the same weeks, so they carry the same client-side aggregation the dashboard has just
had removed - the fifty-vehicle compliance fan-out, the browser-side day bucketing, the page-scoped
filters. Keeping them means keeping two answers to the same question, one of which is worse.

They are already disagreeing. Opened side by side on 30 July 2026 against the same running service,
`/fleet/` reported every indicator as zero while `/ui/fleet` reported three vehicles, one expired
compliance document and one readiness blocker. Neither is wrong: the static page defaults its site
scope to `ACCRA` and the dashboard to `CLET-HQ`. But the static page gives no hint that its zeros are a
scoping artefact - it prints the raw snapshot JSON into a `<pre>` block and leaves the reader to work it
out. That is the drift the two-interface arrangement produces, and it produces it in the direction that
matters: a screen that says nothing is wrong when it has not looked.

### 3. Keep page 1 until IFIMP has dashboard screens - discharged

`sfl-facilities-service`'s page is **not** superseded and must not be retired with the others. It is the
only user interface for the whole of SFL.IFIMP:

- create and browse sites, buildings, floors and rooms
- report a facility fault; the fault register
- work order controls and the work order list
- register and browse asset references
- the access snapshot and recent-records panels

The React dashboard has **four modules** - `fleet`, `fuel`, `dispatch`, `emergency` - and no facilities
module at all. Against ADR 0005's four programmes that leaves the coverage at:

| Programme | Dashboard coverage |
| --- | --- |
| **FTLMP** | Complete - 5 navigation groups |
| **SSEMP** | Emergency notifications only. Visitor, access control, CCTV, intrusion, fire and HSE have no screens |
| **IFIMP** | **None.** Only the static page on 8091 |
| **AVAMP** | **None** |

(That table is the state this decision was written against. See the discharge note below for what it
reads now.)

Deleting page 1 would remove the only way to reach a programme module. Building IFIMP screens in the
dashboard is the real fix, and it is a programme of work rather than a cleanup - so page 1 stays,
renamed, and is written down as a known temporary interface rather than left to look like a peer of the
dashboard.

It has one defect worth recording while it stays: it loads Bootstrap 5.3.3 and Bootstrap Icons 1.11.3
from `cdn.jsdelivr.net` at runtime. It therefore does not work on an isolated network and takes a
third-party runtime dependency the dashboard does not have. That is an argument for replacing it,
not for patching it.

**Discharged, 30 July 2026 - S152 built the screens.** The dashboard now has a `facilities` module:
fifteen screens across three navigation sections, covering the estate model this page covered and the
readiness, blocker, examination-lock, operating-mode, audit and configuration work it never did. So
`/` on 8091 redirects to `/ui/facilities` like the other four, its `index.html` becomes the same kind
of notice page, and its 861 lines of stylesheet and script are deleted. The coverage table above now
reads **IFIMP: S152 complete; S153 and S159 have no screens**.

The redirect target is configured rather than local - `sfl.dashboard.base-url`, default
`http://localhost:8093/ui` - for the same reason the emergency service's is: this service does not
package the bundle. Behind a gateway it becomes a same-origin prefix.

One thing was genuinely lost and is not hidden. The static page carried **fault reporting and work
orders**, which are S153 and have no dashboard screens. Those endpoints are unchanged and still
serve; the notice page says so and
[`docs/facilities/S152_UI_Gap_Report.md`](../facilities/S152_UI_Gap_Report.md) §2.1 records it,
along with the fact that `FacilityFaultController.findAll()` applies no site scoping and no
permission check - which is a reason to build S153 properly rather than to keep a page in front of
it.

## What retiring pages 2–5 cost

Everything below was checked before the change, and this is what it came to afterwards.

**One automated test.** `FleetPostgresEndToEndTest` was the only test touching any of the five. It
fetched `/fleet/index.html` and asserted the body contained `Fleet operational console`; it now asserts
the notice page names `/ui/fleet`. One assertion, as predicted.

**Routing configuration, rewritten rather than removed.**

- `FleetWebConfiguration` - the six view controllers that forwarded `/fleet`, `/fuel` and `/dispatch`
  to their own `index.html` now redirect to `/ui/fleet`, `/ui/fuel` and `/ui/dispatch`, through one
  `retire(…)` helper so the three cannot drift apart.
- `EmergencyWebConfiguration` - its two now redirect to `sfl.dashboard.base-url` + `/emergency`.
- Both security chains are **unchanged**. `/fleet/**`, `/fuel/**`, `/dispatch/**` and `/emergency/**`
  still need to be reachable, because each still serves a notice page.

**No documentation repointing.** The eleven references checked beforehand all still resolve, which was
the whole argument for redirecting rather than deleting. The guides gained a line each saying the route
is now a redirect, because a runbook that does not mention it would leave the reader wondering whether
they had the wrong URL.

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

So the recommendation carried a precondition, and it was met before the pages were retired: **a
development-only actor switcher in the dashboard.** It sits behind the account avatar in the top bar,
carries presets for a fleet manager, a driver, an emergency coordinator, a facilities manager and an
administrator, and takes free text for user, roles, sites and programmes.

Three details are worth recording, because each was a decision rather than an implementation detail.

- **It stores to the session and reloads the page.** Programme entitlement, the navigation sections and
  the site list are module-level constants computed once at import. Making them reactive would mean
  threading the actor through `programmeModel`, `programmes` and `navigation`, and a half-updated
  entitlement is precisely the state in which a role check stops being worth running. A reload
  recomputes everything from one source, which is also what signing in as somebody else really does.
- **Session storage, not local.** An override that survived a browser restart would eventually be
  forgotten and mistaken for the real default. The avatar carries a badge while one is in force for the
  same reason.
- **It is genuinely absent from a production build**, and the first attempt was not. Guarding the render
  with the dev flag still emitted a 4.57 kB `ActorSwitcher` chunk, because `lazy(() => import(…))` at
  module scope is a real edge in the module graph whatever the JSX does with the result. The guard has
  to sit on the import - `import.meta.env.DEV ? lazy(…) : null` - which Vite folds to `false` before
  Rollup runs. Verified by building and searching `dist` for strings unique to the panel, including its
  storage key and its button label: none present, no chunk emitted.

It immediately earned its place. Switching to the driver preset showed the emergency section leave the
sidebar and the portal label change to Fleet, Transport & Logistics - and the fleet service
independently refused the dashboard read with `FLEET_UNAUTHORIZED_SCOPE`, which is the two layers doing
their separate jobs in one screenshot. The facilities preset lands on the no-programme page, which is
the IFIMP gap stated rather than described.

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
  8093 is not deployed. It is a real question for a programme-by-programme launch - SSEMP could then
  launch without FTLMP - and it is not blocking anything today.

---

## Amendment, 6 August 2026 - one bundle, four origins, one address

- Status: **Accepted and implemented.** Extends this record. It answers the question left open under
  *Not decided here* - "whether the emergency service should also package the dashboard bundle" -
  with **yes, and so should every other service.**

### What changed and what did not

**What did not change is the decision this ADR is about.** There is still exactly one dashboard, built
once from `frontend/sfl-operations-ui`, and still one design system. Nothing here reopens mechanism (b)
of ADR 0005 or splits the bundle per programme.

**What changed is how many origins serve it.** It was served by `sfl-fleet-logistics-service` alone,
from `/ui/`, and every other service's screens reached back to 8093 for their own HTML. That had two
consequences that only look small until you run one service:

- **Starting SSEMP meant starting FTLMP.** The emergency screens were served from 8093 and called
  8092, so 8092 alone gave you an API and no interface. The same was true of facilities. A
  programme-by-programme launch was not possible, which is the exact scenario this record's open
  question named.
- **The address carried the deployment topology**, which ADR 0005 decision (2) says a user should not
  be able to infer. `/ui/` on the fleet port is a fleet address, and a facilities manager reaching
  their estate through it learned something true about the build and nothing useful about their work.

### The decision

**Every service packages the bundle and serves it at `/home`.** Four addresses, one shape:

| Origin | Serves | `/api/v1/system/info` answers |
| --- | --- | --- |
| `http://localhost:8091/home` | IFIMP screens | `IFIMP` |
| `http://localhost:8092/home` | SSEMP screens | `SSEMP` |
| `http://localhost:8093/home` | FTLMP screens | `FTLMP` |
| `http://localhost:8090/home` | all three | `ALL` |

The fourth is `sfl-portal-service`: a fifth deployable that owns an address and nothing else - no
datasource, no broker, no security chain. **It aggregates by being told it is the portal, not by
proxying.** The bundle it serves is byte-identical to the other three; the only difference is the
answer to `system/info`, which switches the same code from one platform's screens to all of them. No
reverse proxy, no gateway, no second build.

That is what makes the claim in ADR 0005's amendment - that the origin is part of the entitlement
question - implementable without a routing layer. A service does not need to know what the portal is,
and the portal does not need to know what the services serve.

### Mechanism

- `SflDashboardAutoConfiguration` in `sfl-service-common` registers a resource handler for `/home/**`
  against `classpath:/static/home/`, with view controllers for `/home` and `/home/` and an SPA
  fallback so a refresh on `/home/fleet/vehicles` resolves rather than 404ing. It is wired through
  `AutoConfiguration.imports`, so a service packages the bundle by depending on the shared library and
  putting the files in place - there is no per-service web configuration to keep in step.
- `VITE_BASENAME=/home/` in both `.env` files. This was the cause of the white page during the change:
  the two `.env` files pinned `/ui/` and quietly overrode the Vite config default, so the bundle
  mounted at a path nothing served.
- The `ui` Maven profile is marked `<inherited>false</inherited>`. Without it, every child module
  inherits the profile's plugins, resolves `${project.basedir}` to its own directory, goes looking for
  `services/<module>/../frontend`, and - in the case of `sfl-service-common` - unpacked 97 MB of Node
  into `services/frontend/` before failing. The dashboard is built exactly once, at the top of the
  reactor.

### Consequences

- **A programme launches on its own.** `SFL_PORT=8092` and nothing else gives an operator the SSEMP
  dashboard, its Swagger and its API on one address. The four IntelliJ run configurations are that
  fact made clickable: three services and one that starts all of them behind the portal.
- **No cross-service call serves a page.** Each origin serves its own HTML and its own API; the only
  cross-origin traffic left is the portal's screens calling the three services, which is what CORS
  lists already cover.
- **`/ui` is retired**, along with the last address that named a service in a path a user reads.
  `/home` says what it is, and says the same thing on every port.
- The open question above is closed. The remaining one - whether the bundle should eventually be split
  per programme - is ADR 0005 mechanism (b) and is unaffected: a bundle that already asks its origin
  which platform it serves is one that can be built per platform without changing a single component.
