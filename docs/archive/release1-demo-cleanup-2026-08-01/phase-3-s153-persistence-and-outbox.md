# Phase 3: S153 PostgreSQL Persistence and Outbox Processing

## Objective

Phase 3 hardens the S153 Computerized Maintenance Management System slice by moving it from in-memory storage to PostgreSQL-ready persistence and database-backed outbox processing.

This phase prepares the database model and infrastructure code. The actual EF Core migration creation and database update should be run manually after confirming the local PostgreSQL 18 connection settings.

## Implemented

Phase 3 adds:

- EF Core/Npgsql persistence package setup.
- `SflDbContext` for PostgreSQL persistence.
- Design-time DbContext factory for migration commands.
- PostgreSQL table mappings for:
  - `ifimp.facility_faults`
  - `ifimp.work_orders`
  - `platform.audit_events`
  - `messaging.outbox_messages`
- Database-backed repositories for facility faults and work orders.
- Database-backed audit writer.
- Database-backed outbox store.
- Unit-of-work boundary so business records, audit records and outbox messages can be committed together.
- API and Mobile API wiring to use PostgreSQL-backed persistence.
- Worker service outbox publisher that reads pending outbox rows and publishes through `IIntegrationEventBus`.
- PostgreSQL 18 Docker compose alignment.
- EF model integration test that validates table/schema mapping without requiring a live database.

## Current RabbitMQ Position

The worker publishes through `IIntegrationEventBus`. The current RabbitMQ implementation remains the safe placeholder from Phase 1 until the approved repository RabbitMQ README/pattern is applied.

The worker is ready for real publishing once `RabbitMqIntegrationEventBus` is connected to the approved RabbitMQ client implementation.

## Local PostgreSQL 18 Configuration

The development connection string currently uses the same defaults as `deploy/env/.env.example`:

```text
Host=localhost;Port=5432;Database=sfl;Username=sfl;Password=change-me;Include Error Detail=true
```

Before running migrations, confirm one of these options:

1. Start the Docker dev stack, which creates the `sfl` database/user using the `.env.example` defaults.
2. Or create the PostgreSQL 18 database and user manually.
3. Or override the connection string using the `SFL_CONNECTION_STRING` environment variable.

## Visual Studio Package Manager Console Steps

Recommended Visual Studio setup:

1. Set startup project to `SFL.Api`.
2. Open Package Manager Console.
3. Set Default Project to `SFL.Infrastructure.Persistence`.
4. Confirm `src/SFL.Api/appsettings.Development.json` has your PostgreSQL 18 connection string.
5. Run:

```powershell
Add-Migration InitialSflPersistence -Context SflDbContext -OutputDir Migrations
Update-Database -Context SflDbContext
```

## CLI Alternative

From the solution folder:

```powershell
dotnet ef migrations add InitialSflPersistence --project src/SFL.Infrastructure.Persistence --startup-project src/SFL.Api --context SflDbContext --output-dir Migrations
dotnet ef database update --project src/SFL.Infrastructure.Persistence --startup-project src/SFL.Api --context SflDbContext
```

If `dotnet ef` is not installed, install the EF CLI tool first or use Visual Studio Package Manager Console.

## Post-Migration Smoke Test

After running `Update-Database`, start `SFL.Api` and test:

1. `POST /api/v1/ifimp/facility-faults`
2. `GET /api/v1/ifimp/facility-faults/{id}`
3. `POST /api/v1/ifimp/facility-faults/{id}/work-order`
4. `GET /api/v1/ifimp/work-orders/{id}`

Then check PostgreSQL tables:

- `ifimp.facility_faults`
- `ifimp.work_orders`
- `platform.audit_events`
- `messaging.outbox_messages`

A successful fault report should create one facility fault row, one audit row and one outbox row.

A successful work order creation should update the fault row, create one work order row, create one audit row and create one outbox row.

## Next Phase After Migration

After the database migration and smoke test are complete, the next implementation step is to wire the real RabbitMQ publisher according to the approved team RabbitMQ README/pattern.
