# sfl-facilities-service

The **IFIMP** deployable: S152 CAFM/IWMS, S153 CMMS and S159 Room & Resource Booking.

| | |
|---|---|
| Port | `8091` |
| Database | `sfl_facilities_service` (`localhost:5441`) · e2e `sfl_facilities_service_e2e` (`localhost:55441`) |
| Schema | `facilities` |
| Migrations | V1–V8 |
| Tests | 147 |

## What is built

| System | State |
|---|---|
| **S152 CAFM / IWMS** | **Built.** The estate (site → building → floor → space), zones and membership, device references, the facility asset register, the readiness engine, the dashboard, hash-chained audit, runtime configuration and idempotency |
| S153 CMMS | **Spine only** — fault → work order, `OPEN → ASSIGNED → CLOSED`. Predates S152. V6 adds the `facility_asset_id` and `room_id` links for the real build to use |
| S159 Room & Resource Booking | **Not started.** S152 models bookable spaces and their readiness; S159 will model bookings against them |

S152 is deliberately first: the C9 mapping makes S153 a "sub-system of CAFM (S152)" and puts S158 and
S159 under it too, so the space and asset registry has to exist before anything can reference it.

## Running it

```powershell
cd ..\..                                            # repository root
.\use-sfl-env.ps1                                   # JDK 17 + database URLs
docker compose -f compose.facilities-db.yml up -d
cd services
mvn -pl sfl-facilities-service -am test
mvn -pl sfl-facilities-service -am spring-boot:run
```

| URL | What |
|---|---|
| <http://localhost:8091/actuator/health> | health |
| <http://localhost:8091/swagger-ui.html> | Swagger UI — 49 paths |
| <http://localhost:8091/v3/api-docs> | OpenAPI JSON |
| <http://localhost:8091/> | the pre-S152 facilities dashboard page (ADR 0006 keeps it until a React module replaces it) |

There is no sign-in locally (`sfl.security.enabled=false`); the actor comes from the `X-SFL-*` headers,
which Swagger UI declares globally so they can be filled in once.

## Package layout

```
gh.edu.clet.sfl.facilities
├── shared/          the CAFM platform every IFIMP module inherits
│   ├── api/         actor resolution, correlation ID, the error envelope
│   ├── application/ authorisation, governance, the outbox and the ports
│   ├── domain/      audit chain, error codes, record metadata, the permission matrix
│   └── infrastructure/persistence/  audit, idempotency, runtime config, outbox
├── masterdata/      S152 estate: sites, buildings, floors, spaces, zones, devices, assets
├── readiness/       S152 readiness: checklists, assessments, blockers, locks
├── dashboard/       S152-05 read model
└── maintenance/     S153 fault and work-order spine
```

`shared` is the platform, not a junk drawer: audit, idempotency, runtime configuration, actor
resolution, the error envelope and the permission matrix all live there **so S153 and S159 inherit them
rather than rebuilding them**. That is the whole argument for building CAFM first.

Dependency rule, enforced by `FacilitiesArchitectureTest`: `api → application → domain`,
`infrastructure → application/domain`, nothing points into `infrastructure`, and the domain imports no
framework.

## Boundary rules

- Own the `facilities` schema only. No cross-schema foreign key; `assetReferenceId` points at
  AVAMP-Lite by value.
- Publish cross-service changes through the service outbox (`facilities.outbox_messages`). No drainer
  exists yet — events are recorded, not delivered.
- Consume external events idempotently through the service inbox.
- Vendor payloads go through adapters; no vendor model reaches a domain package.
- Store evidence references and hashes only — never large files or CCTV video.

## The rule this service exists to enforce

> A space cannot be marked READY while a critical blocker is open.

`SRS-SFL-S152-01`. It holds on both paths — a derived assessment and a manual override — because both
route through `ReadinessPolicy`. Everything else in the readiness module records facts; that decides
what they mean.

## Documentation

| Document | |
|---|---|
| [API inventory](../../docs/facilities/S152_API_Inventory.md) | every endpoint, permission and error code |
| [Domain and state model](../../docs/facilities/S152_Domain_And_State_Model.md) | aggregates, lifecycles, the readiness rules |
| [Event contracts](../../docs/facilities/S152_Event_Contracts.md) | outbox events and their future consumers |
| [Requirement traceability](../../docs/facilities/S152_Requirement_Traceability_Matrix.md) | every SRS clause → code → test, including what is deferred |
| [Migration plan](../../docs/facilities/S152_Migration_Plan.md) | V5–V8 and the version allocation |
| [Test plan](../../docs/facilities/S152_Test_Plan.md) | what each layer proves, and the coverage gaps |
| [Operations and verification](../../docs/facilities/S152_Operations_And_Verification_Guide.md) | the end-to-end walkthrough, troubleshooting, configuration keys |
| [Gap and conflict report](../../docs/facilities/S152_Gap_And_Conflict_Report.md) | what existed before, deviations from the brief, defects found by running it |

## Known gaps

- **No authentication.** `sfl.security.enabled=false`; the actor is asserted by header. Authorisation is
  enforced correctly against whatever actor it is given, but identity is not yet verified anywhere in
  this platform.
- **No outbox drainer.** Events are recorded and never published.
- **No React UI.** Until an IFIMP module exists in `frontend/sfl-operations-ui` and `S152` is added to
  `SystemCode` in `programmeModel.ts`, a `FACILITIES_MANAGER` signing in to the operations dashboard
  still sees an empty sidebar, and the static page here is the only facilities UI.
