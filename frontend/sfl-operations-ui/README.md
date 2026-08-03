# SFL Operations UI

The shared React front end for the **Safety, Facilities & Logistics Directorate** (CLET). Six
systems have screens, across three services:

| System | Module | Service | Port |
| --- | --- | --- | --- |
| S152 — Facility management (CAFM/IWMS) | `modules/facilities` | `sfl-facilities-service` | `8091` |
| S153 — Maintenance management (CMMS) | `modules/facilities` | `sfl-facilities-service` | `8091` |
| S166 — Fleet and vehicle management | `modules/fleet` | `sfl-fleet-logistics-service` | `8093` |
| S168 — Fuel and driver logbooks | `modules/fuel` | `sfl-fleet-logistics-service` | `8093` |
| S171 — Courier and dispatch | `modules/dispatch` | `sfl-fleet-logistics-service` | `8093` |
| S174 — Emergency mass notification | `modules/emergency` | `sfl-emergency-notification-service` | `8095` |

S166 was first and is the reference module. As of ADR 0006 this dashboard is the **only** interface —
all five per-service static pages are now redirects into it.

Built on the Aurora React template (MUI v7, Vite, TypeScript, MUI X Data Grid, ECharts), rebranded
to the SFL palette:

| Token        | Value     | Used for                                             |
| ------------ | --------- | ---------------------------------------------------- |
| Primary navy | `#051B2B` | App bar, side navigation, primary surfaces, headings |
| Gold accent  | `#B8950D` | Active navigation, key actions, highlights, charts   |
| White        | `#FFFFFF` | Work surfaces — tables, cards, dialogs               |

---

## A standalone application

This dashboard used to be packaged into `sfl-fleet-logistics-service` and served from `/ui` on the
service's own origin. It is not any more. The backend serves no user interface at all, and this is
now one client of its APIs — deployed on its own, replaceable without touching a service.

Two things follow from that, and both are easy to trip over:

**Every API call is cross-origin, so CORS applies to all of it.** Same-origin serving used to make
that invisible. Each service reads `SFL_CORS_ALLOWED_ORIGINS`; `http://localhost:5005` is in the
defaults, so `npm run dev` works out of the box, but any other port needs adding. The symptom of
getting this wrong is distinctive: the screen fails in the browser while the same request succeeds
from `curl`.

**The API base URLs are baked in at build time.** Vite inlines them, so pointing the bundle at
another environment means rebuilding it, not re-deploying it.

## Running it

There is no sign-in step in local development: `sfl.security.enabled` is `false`, so the services
accept the `X-SFL-*` actor headers this dashboard sends.

### Dashboard and backend together — hot reload (recommended)

From the repository root:

```powershell
.\scripts\dev\run-fleet-dev.ps1
```

The Fleet service runs in its own window on 8093 and Vite serves this dashboard on
<http://localhost:5005> with hot module replacement.

| URL                                     | What                 |
| --------------------------------------- | -------------------- |
| <http://localhost:5005>                 | Operations dashboard |
| <http://localhost:8093/swagger-ui.html> | Swagger UI           |
| <http://localhost:8093/v3/api-docs>     | OpenAPI JSON         |

### Backend on its own

```powershell
.\start-backend.ps1
```

Starts the databases and the facilities, fleet and emergency services — 8091, 8093 and 8095 — with
no front end. Add `-UiOrigin http://localhost:<port>` if you are running this dashboard somewhere
other than 5005.

### Doing it by hand

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
.\use-sfl-env.ps1
docker compose -f compose.service-dbs.yml up -d

cd services
mvn -pl sfl-fleet-logistics-service -am test          # optional
mvn -pl sfl-fleet-logistics-service spring-boot:run
```

Front-end only:

```powershell
cd frontend\sfl-operations-ui
npm install
npm run lint
npm run typecheck
npm run build
npm run dev
```

### The actor switcher

The account avatar in the top bar carries a **Change actor** entry in development builds. It sends
different `X-SFL-*` headers, stores them for the session and reloads.

Use it to check what ADR 0005 promises, at both grains:

| Preset | What should happen |
| --- | --- |
| Fleet manager | All five FTLMP groups. No emergency. |
| **Driver** | Fleet and fuel. **Courier & dispatch disappears** — a driver does not run the mailroom. |
| **Mailroom officer** | **Courier & dispatch only.** Same programme as the driver, the opposite half of it. |
| Emergency coordinator | Emergency notifications only. |
| Security officer | Courier & dispatch *and* emergency — it escalates dispatch exceptions. |
| Facilities manager | The no-programme page, because IFIMP has no screens yet. |

The driver and mailroom officer are the pair worth trying: both are FTLMP, and programme scoping alone
could not tell them apart. The fleet service will also refuse a driver's dashboard read with
`FLEET_UNAUTHORIZED_SCOPE` — navigation scoping is a usability control, and the service is the
enforcement point.

The avatar shows a badge while an override is in force, and **Clear and reload** returns to the
`VITE_SFL_*` values in `.env`. A production build has none of this: the panel is behind
`import.meta.env.DEV` on its dynamic import, so no chunk is emitted.

### How the dashboard is mounted

`npm run build` emits `dist/` mounted at `/`, ready to be served by any static host — nginx, a CDN,
`npm run preview`, whatever you like. Serve it with a single-page fallback so a refresh on
`/fleet/vehicles` resolves rather than 404ing; that used to be the service's job and is now the
host's.

React Router takes its basename from `import.meta.env.BASE_URL`, so if you serve the bundle under a
path prefix, set `VITE_BASENAME` and rebuild — the mount point stays configured in exactly one
place.

---

## Configuration

Copy `.env.example` to `.env` and adjust. Vite reads these at build time.

`.env.production` is loaded by `npm run build` only. All three API base URLs are set there as
explicit cross-origin addresses pointing at localhost. **Change them for any deployment that is not
a developer machine** — they are inlined into the bundle, so a wrong value is a rebuild, not a
config change.

| Variable                  | Default                 | Purpose                                            |
| ------------------------- | ----------------------- | -------------------------------------------------- |
| `VITE_FLEET_API_BASE_URL` | `http://localhost:8093` | Base URL of the Fleet & Logistics service          |
| `VITE_EMERGENCY_API_BASE_URL` | `http://localhost:8095` | Base URL of the Emergency Notification service (S174) |
| `VITE_FACILITIES_API_BASE_URL` | `http://localhost:8091` | Base URL of the Facilities service (S152)       |
| `VITE_SFL_USER`           | `fleet.operator`        | Sent as `X-SFL-User`                               |
| `VITE_SFL_DISPLAY_NAME`   | `Fleet Operator`        | Sent as `X-SFL-Display-Name`                       |
| `VITE_SFL_ROLES`          | `FLEET_MANAGER,…`       | Sent as `X-SFL-Roles` (comma-separated)            |
| `VITE_SFL_SITES`          | `CLET-HQ`               | Sent as `X-SFL-Sites` (comma-separated)            |
| `VITE_FLEET_DEV_FALLBACK` | `false`                 | Reserved switch for clearly-labelled dev fallbacks |
| `VITE_APP_PORT`           | `5005`                  | Dev/preview server port                            |
| `VITE_BASENAME`           | `/`                     | Path prefix the bundle is served under             |

### Request headers

Every request made through `shared/api/client.ts` carries:

```
X-SFL-User, X-SFL-Display-Name, X-SFL-Roles, X-SFL-Sites
X-SFL-Source-Channel: WEB
X-Correlation-ID: <uuid v4, one per request>
Idempotency-Key: <uuid v4>   # state-creating POSTs only
```

In production the same `ActorContext` is derived from the OIDC/JWT principal and the `X-SFL-*`
headers are ignored — no change is needed on this side.

### If the facilities sections are missing

`VITE_SFL_ROLES` **overrides** the built-in default role list rather than adding to it. The default
includes `FACILITIES_MANAGER` and `CENTRE_MANAGER`; an `.env` that sets `VITE_SFL_ROLES` without a
facilities role gets no facilities sections at all. That is the entitlement working, not a broken
build — but it looks identical to one, so it is worth checking first. `.env` is gitignored, so this
is per-machine.

The roles worth switching between (the account panel has presets for all of them):

| Role | What it shows |
| --- | --- |
| `FACILITIES_MANAGER` | The estate and readiness. No audit, no configuration, no operating-mode control — the matrix withholds mode from this role deliberately. |
| `CENTRE_MANAGER` | Adds the operating-mode control: declaring and standing down examination mode. |
| `IFIMP_TECHNICIAN` | Assess readiness and change asset condition. No lock, no mode, no configuration. |
| `FACILITIES_DIRECTOR` | Adds audit and integrity verification. |

### CORS

Each service must allow the dev origin. If requests fail with a CORS error, add
`http://localhost:5005` to the allowed origins in `FleetWebConfiguration`,
`EmergencyWebConfiguration` or `FacilitiesWebConfiguration`. Each must also **expose**
`X-Correlation-ID`, or the client reads `null` cross-origin and every error message loses the one
identifier that ties it to a service log.

---

## Architecture

```
src/
  shared/                 # reusable across every future SFL module
    api/                  # client, headers, correlation, envelope parsing, config
    components/           # DataState, StatusChip, SectionCard, FormDialog, fields, format…
    errors/               # FleetApiError — status, code, fieldErrors, details
    hooks/                # useApiQuery
    layout/               # SflAppShell, navigation
    validation/           # validators + useFleetForm
  modules/
    fleet/                # S166
      api/                # enums.ts, dto.ts, fleetApi.ts
      charts/             # ECharts readiness donut + exceptions bars
      components/         # IndicatorTile, DrilldownDrawer
      dialogs/            # vehicle / driver / trip / workflow action dialogs
      pages/              # dashboard, registers, details, queues, governance
    facilities/           # S152
      api/                # enums.ts, dto.ts, facilitiesApi.ts, workflow.ts
      components/         # ReadinessBlockerList, facilitiesFormat
      dialogs/            # assessment / readiness / blocker / asset / operating-mode dialogs
      pages/              # dashboard, estate registers, readiness, audit, configuration
  theme/                  # Aurora theme with the SFL palette (sflNavy / sflGold)
```

Adding the next SFL module (S159 booking, Safety & Security, Asset Visibility) means
adding `src/modules/<module>` and a navigation section — the API client, error envelope handling,
validation and layout are already shared.

**One thing is not optional when you do.** If the module is served by a new service, add its
`/actor/permissions` endpoint to `SOURCES` in `shared/layout/actorPermissions.ts`. The fail-open
there is per-*set*, not per-service: once any service answers, anything absent from the merged set is
treated as **denied**, not unknown. A module missing from that list renders with every gated control
silently hidden and no error anywhere. This is documented at the call site and cost an afternoon
during S152.

### Tests

```bash
npm run test          # vitest run
npm run test:watch
```

156 tests today, covering system entitlement per role, the S152 readiness rules, the S153 closure
evidence gate and work-order transition table, the S168 fuel-card masking rules and lifecycle
transitions, the formatting helpers, and the facilities dashboard's loading / error / stale /
restricted-drilldown states.

Two things about writing tests against the shared components. `Select` is an ARIA listbox, not a
native `<select>` — `selectOptions` will not drive it. Click the `combobox` and then the `option`,
and query the option from `screen` rather than from inside the dialog, because the list opens
through a portal. And jsdom has no layout, so `scrollIntoView` is stubbed in `src/test/setup.ts`;
without it every test that opens a listbox fails with an error about scrolling.

### Data-fetching contract

`useApiQuery` gives every screen the same four states — loading, error, empty, content — rendered
through `<DataState>`. Mutations never guess the resulting server state: each dialog refetches the
affected queries on success. There are no optimistic updates on transitions, because the service
owns the state machine and the readiness decision.

### Validation

`useFleetForm` mirrors the service's Bean Validation constraints (required, `@Size`,
`@PositiveOrZero`, year and capacity ranges) and adds the cross-field rules the service enforces
(date ranges, odometer floors, mandatory closure evidence). Client rules exist to answer the
operator sooner — they never replace the server check. On a failed submit the service's
`fieldErrors[]` are mapped back onto the same fields, and the envelope message (which carries the
SRS _Error States_ wording verbatim for SRS-defined codes) is shown as a form-level alert. Submit
buttons disable while a request is in flight.

---

## Screens

| Route                 | Screen                                                                      |
| --------------------- | --------------------------------------------------------------------------- |
| `/fleet`              | Operations dashboard — readiness, exceptions, active trips, escalations     |
| `/fleet/vehicles`     | Vehicle register (server-side filter, sort, page)                           |
| `/fleet/vehicles/:id` | Vehicle detail — readiness, compliance, service history, trips              |
| `/fleet/drivers`      | Driver register                                                             |
| `/fleet/drivers/:id`  | Driver detail — eligibility by vehicle category, assignments                |
| `/fleet/trips`        | Trip queue                                                                  |
| `/fleet/trips/:id`    | Trip workflow — assign, inspect, start, hold, close, cancel                 |
| `/fleet/workflow`     | Workflow queue with SLA standing                                            |
| `/fleet/workflow/:id` | Workflow item with immutable transition and comment history                 |
| `/fleet/compliance`   | Compliance and service exposure                                             |
| `/fleet/governance`   | Evidence lookup and registration, export requests, audit chain verification |
| `/fleet/integrations` | Telematics intake health and dead-letter replay                             |

---

## Fleet endpoints integrated

Taken from the controllers, which are the authority — where the API inventory note and the code
disagree, the code is what the dashboard was built against.

| Area         | Endpoints                                                                                                                                                                                                                                                   |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Vehicles     | `GET/POST /vehicles`, `GET/PATCH /vehicles/{id}`, `PATCH /vehicles/{id}/lifecycle`, `GET/POST /vehicles/{id}/compliance-documents`, `GET /vehicles/{id}/service-history`, `POST /vehicles/{id}/service-records`, `POST /vehicles/{id}/odometer-corrections` |
| Drivers      | `GET/POST /drivers`, `GET/PATCH /drivers/{id}`, `GET /drivers/{id}/eligibility`                                                                                                                                                                             |
| Trips        | `GET/POST /trips`, `GET /trips/{id}`, `PATCH /trips/{id}/assignment` `/start` `/hold` `/cancel` `/closure`, `GET/POST /trips/{id}/inspections`, `GET /trips/assignment-preview`                                                                             |
| Workflow     | `GET/POST /workflow-items`, `GET /workflow-items/{id}`, `PATCH …/assignment` `/progress` `/hold` `/escalation` `/cancel` `/closure` `/reopen`, `POST …/comments`, `GET …/transitions`                                                                       |
| Evidence     | `POST /evidence`, `GET /evidence/{id}`, `POST /evidence/{id}/access`, `POST /evidence/{id}/export-requests`, `PATCH /evidence/export-requests/{id}/decision`, `POST /evidence/export-requests/{id}/export`                                                  |
| Audit        | `GET /audit/records`, `GET /audit/chain/verification`                                                                                                                                                                                                       |
| Integrations | `GET /integrations/health`, `POST /integrations/messages/{id}/replay`                                                                                                                                                                                       |
| Dashboards   | `GET /dashboards/operations`, `GET /dashboards/operations/drilldowns/{indicator}`, `GET /reports/go-live-readiness`                                                                                                                                         |

---

## Scripts

| Command             | Does                                              |
| ------------------- | ------------------------------------------------- |
| `npm run dev`       | Vite dev server on `VITE_APP_PORT` (default 5005) |
| `npm run build`     | Type-checked production build into `dist/`        |
| `npm run typecheck` | `tsc --noEmit`                                    |
| `npm run lint`      | ESLint with Prettier, zero warnings tolerated     |
| `npm run pretty`    | Prettier write across the project                 |
| `npm run preview`   | Serve the production build locally                |

## Manual verification checklist

1. Run `.\scripts\dev\run-fleet-dev.ps1` from the repository root.
2. <http://localhost:5005> renders indicators, charts and the snapshot freshness chip — which also
   proves the cross-origin calls to 8093 are getting through CORS.
3. Vehicle and driver registers load, filter and page against the server.
4. Vehicle, driver, trip and workflow detail screens open from their registers.
5. Create and transition dialogs show inline errors before submit — try an empty registration
   number, an end date before the start date, and a negative odometer.
6. A backend rejection surfaces its own wording — try closing a trip with an unknown evidence ID
   and confirm the `FLEET_CLOSURE_EVIDENCE_MISSING` message appears with its correlation ID.
7. The SFL logo appears in the sidenav and the navy/gold palette is applied throughout.
8. Resize to tablet and phone widths — the sidenav becomes a drawer and tables stay scrollable.
