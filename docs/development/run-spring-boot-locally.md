# Run the SFL services locally

**Rewritten 1 August 2026.** The previous version of this page described running a single Spring Boot
application — `gh.edu.clet.sfl.SflApplication` on port 8081, started from the root `pom.xml` via
`scripts/dev/run-local.ps1`. That was the pre-migration legacy app, and it has been removed along with
the pom and the script. Everything below describes the architecture that exists.

There is no single application. There are **five deployable services**, each owning its own schema,
its own migrations and its own API boundary.

| Service | Port | Runtime database | DB port |
|---|---:|---|---:|
| `sfl-facilities-service` — S152, S153, S159 | 8091 | `sfl_facilities_service` | 5441 |
| `sfl-safety-security-service` — SSEMP | 8092 | `sfl_safety_security_service` | 5442 |
| `sfl-fleet-logistics-service` — S166, S168_fuel, S171. Serves `/ui` | 8093 | `sfl__fleet_vehicle_service` | 5443 |
| `sfl-asset-visibility-service` — AVAMP-Lite | 8094 | `sfl_asset_visibility_service` | 5444 |
| `sfl-emergency-notification-service` — S174 | 8095 | `sfl_emergency_notification_service` | 5445 |

`sfl-safety-security-service` starts and answers its health probe, and that is all it does — a
controller, a foundation migration and an application class. Its six systems are unbuilt scope, not a
broken build.

> It could not start at all until 1 August 2026: the module had **no `@SpringBootApplication` class**,
> so `spring-boot:run` failed with *"Unable to find a suitable main class"*. Four documents described it
> as a service that compiles and boots. Nothing had ever tried to launch it — it has no tests, compose
> does not run it, and CI builds without launching — so it compiled green for months while every
> document asserted the opposite.

## Databases first

```powershell
docker compose -f compose.service-dbs.yml up -d
```

Ten containers: one runtime and one end-to-end database per service, the e2e copies on `554xx`. For
single-service work use `compose.facilities-db.yml` and its siblings.

## Java

The reactor needs **JDK 17+**. This machine's `PATH` has carried Zulu 11, which Maven picks up
silently and fails with errors that never mention the JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
```

## Running one service

From `services/`:

```powershell
..\mvnw.cmd -pl sfl-facilities-service spring-boot:run
```

**Authentication is on by default since A1, and that is the point.** With no Keycloak running, every
endpoint answers `401` and `WWW-Authenticate` points at an issuer that is not there. For a laptop with
no identity provider:

```powershell
$env:SFL_SECURITY_ENABLED = 'false'
```

The service then logs a warning naming itself on every startup, stating that every endpoint is
unauthenticated and the actor is whatever the `X-SFL-*` headers claim. That warning is deliberate. The
variable is load-bearing, and an environment that simply forgets it is now **secure** rather than
open — the inverse of how this behaved before 31 July, when forgetting it left every API wide open.

To run against real identity instead, bring up Keycloak with the imported realm
(`deploy/keycloak/sfl-realm.json`) and leave the variable unset.

## Running the whole platform

```powershell
.\start-fleet.ps1
```

Starts the databases, builds the operations dashboard, and runs the fleet service, which serves the
API, Swagger and the dashboard together on 8093. For front-end work with hot reload use
`.\scripts\dev\run-fleet-dev.ps1` — service on 8093, Vite on 5005.

## Checking a service is up

```powershell
curl http://localhost:8091/actuator/health
```

The health probe stays reachable without a token by design: a load balancer cannot present one.

## IntelliJ

Import `services/pom.xml` as the Maven project, not the repository root — there is no longer a pom at
the root. Set the project JDK to 17. Each service has its own `…Application` class; run whichever you
are working on, with `SFL_SECURITY_ENABLED=false` in the run configuration.

If you see **"Invalid VCS root mapping"** on project open, `.idea/vcs.xml` has acquired a Git root
mapping for `frontend/sfl-operations-ui`, which is a directory in this repository rather than a
repository of its own. Delete that second `<mapping>` line. `.idea/` is gitignored, so it recurs per
person.

## Tests

A plain `..\mvnw.cmd test` **skips the end-to-end suites and still reports green** — 119 tests at the
last count. The Environment section of the root `README.md` has the three `SFL_*_TEST_DB_URL`
variables that un-gate them, and explains why the facilities one must point at an empty database.
