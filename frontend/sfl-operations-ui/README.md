# SFL Operations UI

The shared React front end for the **Safety, Facilities & Logistics Directorate** (CLET). The first
module implemented is **S166 — Fleet and Vehicle Management**, built against
`sfl-fleet-logistics-service` on port `8093`.

Built on the Aurora React template (MUI v7, Vite, TypeScript, MUI X Data Grid, ECharts), rebranded
to the SFL palette:

| Token        | Value     | Used for                                             |
| ------------ | --------- | ---------------------------------------------------- |
| Primary navy | `#051B2B` | App bar, side navigation, primary surfaces, headings |
| Gold accent  | `#B8950D` | Active navigation, key actions, highlights, charts   |
| White        | `#FFFFFF` | Work surfaces — tables, cards, dialogs               |

---

## Running the whole stack locally

Run from the repository root unless stated otherwise.

### 1. Environment and databases

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
.\use-sfl-env.ps1
docker compose -f compose.service-dbs.yml up -d
```

### 2. Backend tests (recommended)

```powershell
cd services
mvn -pl sfl-fleet-logistics-service -am test
cd ..
```

### 3. Start the Fleet service on port 8093

```powershell
cd services
mvn -pl sfl-fleet-logistics-service spring-boot:run
cd ..
```

Verify it is up:

- API docs — <http://localhost:8093/v3/api-docs>
- Swagger UI — <http://localhost:8093/swagger-ui.html>
- Health — <http://localhost:8093/actuator/health>

### 4. Start the front end

```powershell
cd frontend\sfl-operations-ui
npm install
npm run lint
npm run typecheck
npm run build
npm run dev
```

The dev server listens on <http://localhost:5005> and opens on the Fleet operations workspace.

---

## Configuration

Copy `.env.example` to `.env` and adjust. Vite reads these at build time.

| Variable                  | Default                 | Purpose                                            |
| ------------------------- | ----------------------- | -------------------------------------------------- |
| `VITE_FLEET_API_BASE_URL` | `http://localhost:8093` | Base URL of the Fleet & Logistics service          |
| `VITE_SFL_USER`           | `fleet.operator`        | Sent as `X-SFL-User`                               |
| `VITE_SFL_DISPLAY_NAME`   | `Fleet Operator`        | Sent as `X-SFL-Display-Name`                       |
| `VITE_SFL_ROLES`          | `FLEET_MANAGER,…`       | Sent as `X-SFL-Roles` (comma-separated)            |
| `VITE_SFL_SITES`          | `CLET-HQ`               | Sent as `X-SFL-Sites` (comma-separated)            |
| `VITE_FLEET_DEV_FALLBACK` | `false`                 | Reserved switch for clearly-labelled dev fallbacks |
| `VITE_APP_PORT`           | `5005`                  | Dev/preview server port                            |

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

### CORS

The Fleet service must allow the dev origin. If requests fail with a CORS error, add
`http://localhost:5005` to the allowed origins in `FleetWebConfiguration`.

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
  theme/                  # Aurora theme with the SFL palette (sflNavy / sflGold)
```

Adding the next SFL module (Facilities, Safety & Security, Asset Visibility, Emergency
Notification) means adding `src/modules/<module>` and a navigation section — the API client, error
envelope handling, validation and layout are already shared.

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

Taken from the controllers, not from the API inventory document — see
`docs/fleet/S166_Frontend_Gap_Register.md` for where the two disagree.

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

1. Fleet service running on 8093, front end on 5005.
2. `/` redirects to `/fleet` and the dashboard renders indicators, charts and the snapshot
   freshness chip.
3. Vehicle and driver registers load, filter and page against the server.
4. Vehicle, driver, trip and workflow detail screens open from their registers.
5. Create and transition dialogs show inline errors before submit — try an empty registration
   number, an end date before the start date, and a negative odometer.
6. A backend rejection surfaces its own wording — try closing a trip with an unknown evidence ID
   and confirm the `FLEET_CLOSURE_EVIDENCE_MISSING` message appears with its correlation ID.
7. The SFL logo appears in the sidenav and the navy/gold palette is applied throughout.
8. Resize to tablet and phone widths — the sidenav becomes a drawer and tables stay scrollable.
