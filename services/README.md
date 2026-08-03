# SFL backend services

Java 17 · Spring Boot 4.1 · Maven multi-module. Five deployables plus a shared kernel.

| Module                               | Programme  | Systems          | Port   |
| ------------------------------------ | ---------- | ---------------- | ------ |
| `sfl-facilities-service`             | IFIMP      | S152, S153, S159 | `8091` |
| `sfl-safety-security-service`        | SSEMP      | —                | `8092` |
| `sfl-fleet-logistics-service`        | FTLMP      | S166, S168, S171 | `8093` |
| `sfl-asset-visibility-service`       | AVAMP-Lite | —                | `8094` |
| `sfl-emergency-notification-service` | SSEMP      | S174             | `8095` |
| `sfl-service-common`                 | —          | Shared kernel    | —      |

`sfl-safety-security-service` is a scaffold — one class and a migration. Nothing is built behind it.

`sfl-service-common` is the shared kernel: the actor principal, RBAC, and the error and event
envelopes. It is a library, not a service.

## These services serve no user interface

Every one of them is API-only. `sfl-fleet-logistics-service` used to build the React dashboard with
a project-local Node install, copy it into `static/ui` and serve it; the facilities and emergency
services served pages of their own and later redirected into it. All of that is gone — the Node
toolchain, the `-Pui` profile, the resource copying, the SPA fallback resolver and the static pages.
A clean `package` now produces a jar with no static content in it at all.

The reason is replaceability: a front end can be swapped without touching a service. It also means
**CORS is the entire contract with the UI**, where before it was a development convenience. Each
service reads `sfl.cors.allowed-origins` (`SFL_CORS_ALLOWED_ORIGINS`), defaulting to the usual local
front-end ports. Set it explicitly per deployment. A UI whose origin is missing fails in the browser
while every equivalent `curl` succeeds, so the services log their allowed origins on startup —
that turns a silent rejection into a one-line diagnosis.

## Databases

Each deployable owns a separate PostgreSQL database boundary — a runtime database and a separate
end-to-end one, as local Docker containers.

| Service                              | Runtime database                     | Port   | E2E database                             | Port    |
| ------------------------------------ | ------------------------------------ | -----: | ---------------------------------------- | ------: |
| `sfl-facilities-service`             | `sfl_facilities_service`             | `5441` | `sfl_facilities_service_e2e`             | `55441` |
| `sfl-safety-security-service`        | `sfl_safety_security_service`        | `5442` | `sfl_safety_security_service_e2e`        | `55442` |
| `sfl-fleet-logistics-service`        | `sfl__fleet_vehicle_service`         | `5443` | `sfl__fleet_vehicle_service_e2e`         | `55443` |
| `sfl-asset-visibility-service`       | `sfl_asset_visibility_service`       | `5444` | `sfl_asset_visibility_service_e2e`       | `55444` |
| `sfl-emergency-notification-service` | `sfl_emergency_notification_service` | `5445` | `sfl_emergency_notification_service_e2e` | `55445` |

All ten are `postgres:16-bookworm`, user `sfl`, password `sfl`, database named per the table. Start
them all:

```powershell
docker compose -f compose.service-dbs.yml up -d
```

`.\start-backend.ps1` at the repository root does this and then starts the services, so it is the
one command for a working local system.

For single-service work, use `compose.facilities-db.yml`, `compose.safety-security-db.yml`,
`compose.fleet-db.yml`, `compose.asset-visibility-db.yml` or `compose.emergency-db.yml`.

## Build and test

From `services/`, with `JAVA_HOME` pointing at JDK 17:

```powershell
..\mvnw.cmd test                                              # the whole reactor
..\mvnw.cmd -pl sfl-facilities-service test                   # one service
..\mvnw.cmd -pl sfl-service-common,sfl-facilities-service install
..\mvnw.cmd -pl sfl-fleet-logistics-service -am spring-boot:run
```

Node is not involved in any of these. From the repository root, `.\start-backend.ps1` starts the
databases and the services together.

**Two things about running the tests.**

Testcontainers is skipped in the Windows development environment — the Java Docker client cannot
reach the named pipe — so the end-to-end suites report as skipped rather than failing. A plain
`mvnw test` therefore goes green while whole suites never ran. CI fails the build if anything
skipped; see the root README for the three environment variables that enable them locally.

Unit tests passing is not the same as the service starting. 135 unit tests were green while
`sfl-facilities-service` could not boot at all; dropping the schema and running against real
PostgreSQL found eight defects in minutes. Run it against a real database before calling it done.

## Package layout

Every service follows the same shape:

```
gh.edu.clet.sfl.<service>
├── shared/          the platform each module in this service inherits
│   ├── api/         actor resolution, correlation ID, the error envelope
│   ├── application/ authorisation, governance, the outbox and the ports
│   ├── domain/      audit chain, error codes, record metadata, permission matrix
│   └── infrastructure/persistence/
└── <module>/        one package per system, each with api / application / domain / infrastructure
```

`shared` is the platform, not a junk drawer. Audit, idempotency, runtime configuration, actor
resolution, the error envelope and the permission matrix all live there so later modules inherit
them rather than rebuilding them.

The dependency rule, enforced by an ArchUnit test in each service: `api → application → domain`,
`infrastructure → application/domain`, nothing points into `infrastructure`, and the domain imports
no framework.

## API conventions

- Paths are `/api/v1/<domain>/<resource>`.
- Every response uses the `{data, error}` envelope.
- `Idempotency-Key` is honoured on state-creating POSTs only.
- `X-Correlation-ID` flows end to end, and is **exposed** as a CORS response header — without that
  the browser client reads `null` cross-origin and every error loses the one identifier tying it to
  a service log.
- Error codes carry the SRS's own wording for SRS-defined codes.

There is no authentication locally: `sfl.security.enabled=false` and the actor is asserted through
`X-SFL-*` headers, which Swagger UI declares globally so they can be filled in once. Authorisation
is enforced correctly against whatever actor it is given — but identity is not yet verified. In
production the same actor context is derived from the OIDC/JWT principal and the headers are
ignored.

## Migrations

**There is no migration command to run.** Flyway is enabled in every service and runs on startup,
against whichever database that service is pointed at. Bring the containers up, start the service,
and the schema is built and brought up to date before it finishes booting. Current counts: 27 in
fleet, 14 in facilities, 8 in emergency, 2 in asset-visibility, 1 in safety-security.

`ddl-auto: validate` is the check on that. Hibernate compares the schema Flyway produced against the
entity mappings, and a service that disagrees with its own database **refuses to start** rather than
running against a schema it half-fits. A boot failure right after adding a migration is almost
always this, and it is telling you something true.

Two rules when writing one:

- Use `VARCHAR(n)` with a length `CHECK`, never `CHAR(n)` — Hibernate rejects the latter at schema
  validation, and the failure does not name the column.
- Alter and backfill rather than drop and recreate.

## Boundary rules

- A service owns its schema only. No cross-schema foreign keys; cross-service identifiers are held
  by value.
- Cross-service changes are published through the service outbox. No drainer exists yet — events
  are recorded, not delivered.
- External events are consumed idempotently through the service inbox.
- Vendor payloads go through adapters. No vendor model reaches a domain package.
- Store evidence references and hashes only — never large files or CCTV video.
