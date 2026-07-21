# Portal → API → PostgreSQL Persistence

The web portal now persists everything through `SFL.Api`, which writes to PostgreSQL.
The old in-memory `PortalMaintenanceStore` has been removed.

## Flow

```
WebPortal (Blazor)  →  MaintenanceApiClient (HttpClient)  →  SFL.Api
   →  FacilityMaintenanceApplicationService  →  EF Core repositories  →  PostgreSQL
   (+ audit_events, outbox_messages written in the same transaction)
```

Because writes go through the application service, every create/assign/close also writes an
audit record and an outbox event — exactly like the API does directly.

## What changed

- Portal calls real endpoints; data survives restarts and reloads.
- Facility Faults: **report** + **create work order** persist. Edit/delete were removed
  (an audited maintenance record is not hard-edited or deleted).
- Work Orders now follow the real domain lifecycle:
  **Open → Assign (owner) → Start → Complete → Close**.
  (`Close` is only allowed once a work order is `Completed`, enforced by the domain.)
- New API endpoints added (contracts for the original four are unchanged):
  - `GET  /api/v1/ifimp/facility-faults`              (list)
  - `GET  /api/v1/ifimp/work-orders`                  (list)
  - `POST /api/v1/ifimp/work-orders/{id}/assignment`  `{ assignedTo, assignedBy }`
  - `POST /api/v1/ifimp/work-orders/{id}/start`
  - `POST /api/v1/ifimp/work-orders/{id}/completion`
  - `POST /api/v1/ifimp/work-orders/{id}/closure`     `{ closedBy }`

## One-time setup

1. Confirm `src/SFL.Api/appsettings.Development.json` has your PostgreSQL connection string
   (`ConnectionStrings:DefaultConnection`), or set `SFL_CONNECTION_STRING`.
2. Apply the migration so the tables exist:

   ```powershell
   dotnet ef database update --project src/SFL.Infrastructure.Persistence --startup-project src/SFL.Api --context SflDbContext
   ```

   (Or run the Docker dev stack which creates the `sfl` database/user.)

## Running (both projects must run together)

- **API**: `dotnet run --project src/SFL.Api`  → listens on `https://localhost:7120`.
- **Portal**: `dotnet run --project src/SFL.WebPortal.Razor`.

In Visual Studio: set **multiple startup projects** (SFL.Api + SFL.WebPortal.Razor) to Start.

The portal reads the API base URL from `Api:BaseUrl` in `appsettings.json`
(default `https://localhost:7120/`). In Development the portal trusts the ASP.NET Core
dev certificate automatically, so HTTPS server-to-server calls work without extra setup.

## Resilience

If the API or database is not running, the portal's list calls fail softly (empty lists,
no crash), and write actions show a toast with the API error message instead of breaking the page.

## Verify persistence

1. Sign in, report a fault, create a work order, assign/start/complete/close it.
2. Restart the portal — the records are still there (loaded from PostgreSQL).
3. Optionally query the tables: `ifimp.facility_faults`, `ifimp.work_orders`,
   `platform.audit_events`, `messaging.outbox_messages`.
