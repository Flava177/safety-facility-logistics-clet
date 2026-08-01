# S168 Fuel — Commit & PR Runbook

Run these on your machine (IntelliJ terminal / PowerShell), where git works and you can build first.
Everything below is currently **uncommitted** on branch `feat/s168-fuel`.

> Why you're running this and not the agent: the sandbox can't delete `.git/index.lock` on the
> mounted repo and has no network to push/PR. So commits + PRs happen here.

---

## 0. Clear the stale lock and confirm the branch

PowerShell:
```powershell
Remove-Item -Force .git\index.lock -ErrorAction SilentlyContinue
git rev-parse --abbrev-ref HEAD   # must print: feat/s168-fuel
```
(bash: `rm -f .git/index.lock`)

## 1. Build BEFORE committing (the agent could not compile)

```powershell
mvn -pl sfl-fleet-logistics-service -am test
```
Fix any compile/test issues (or send the output back to the agent). Only continue once green.
Also sanity-check: app boots on 8093, `/actuator/health` = UP, `/v3/api-docs` returns JSON,
Swagger UI loads, `/fuel/` loads, and the S166 fleet tests still pass.

## 2. Feature-oriented commits (authored as Flava177, no AI attribution)

Each commit pins author **and** committer via `-c`. Run them in order.

```bash
# C1 — docs
git add docs/fuel docs/integration/event-catalog.md
git -c user.name="Flava177" -c user.email="33349874+Flava177@users.noreply.github.com" \
  commit -m "docs(fuel): add S168 planning, operations and event-catalog updates"

# C2 — permissions, events, security & web wiring
git add services/sfl-service-common/src/main/java/gh/edu/clet/sfl/common/security/SflPermission.java \
        services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/api/FleetApiExceptionHandler.java \
        services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/config/FleetOpenApiConfiguration.java \
        services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/config/FleetSecurityConfiguration.java \
        services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/config/FleetWebConfiguration.java \
        services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/domain/event/FleetEventType.java \
        services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/domain/model/SourceChannel.java \
        services/sfl-fleet-logistics-service/src/main/resources/application.yml \
        services/sfl-fleet-logistics-service/README.md
git -c user.name="Flava177" -c user.email="33349874+Flava177@users.noreply.github.com" \
  commit -m "feat(fuel): register fuel permissions, events, security and web wiring"

# C3 — Flyway migrations
git add services/sfl-fleet-logistics-service/src/main/resources/db/migration/V10__fuel_transactions_and_policies.sql \
        services/sfl-fleet-logistics-service/src/main/resources/db/migration/V11__fuel_driver_logbooks.sql \
        services/sfl-fleet-logistics-service/src/main/resources/db/migration/V12__fuel_reconciliation_and_anomalies.sql \
        services/sfl-fleet-logistics-service/src/main/resources/db/migration/V13__fuel_imports.sql \
        services/sfl-fleet-logistics-service/src/main/resources/db/migration/V14__fuel_dashboard_and_runtime_defaults.sql \
        services/sfl-fleet-logistics-service/src/main/resources/db/migration/V15__fuel_trip_anomaly_idempotency.sql
git -c user.name="Flava177" -c user.email="33349874+Flava177@users.noreply.github.com" \
  commit -m "feat(fuel): add fuel Flyway migrations V10-V15"

# C4 — backend (domain, application, infrastructure, api)
git add services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fuel
git -c user.name="Flava177" -c user.email="33349874+Flava177@users.noreply.github.com" \
  commit -m "feat(fuel): implement fuel domain, workflow, reconciliation, integrations and APIs"

# C5 — operational dashboard
git add services/sfl-fleet-logistics-service/src/main/resources/static/fuel
git -c user.name="Flava177" -c user.email="33349874+Flava177@users.noreply.github.com" \
  commit -m "feat(fuel): add Fuel Management and Driver Logbooks dashboard"

# C6 — tests
git add services/sfl-fleet-logistics-service/src/test/java/gh/edu/clet/sfl/fleetlogistics/fuel
git -c user.name="Flava177" -c user.email="33349874+Flava177@users.noreply.github.com" \
  commit -m "test(fuel): add domain, architecture and end-to-end coverage"
```

Verify authorship of every new commit:
```bash
git log feat/s168-fuel --not fleet --format='%an <%ae> | %cn <%ce> | %s'
# every line must read: Flava177 <33349874+Flava177@users.noreply.github.com> | Flava177 <...> | <subject>
```

## 3. Push and open PR #1  →  base `fleet`

```bash
git push -u origin feat/s168-fuel
```
Then, with GitHub CLI:
```bash
gh pr create --base fleet --head feat/s168-fuel \
  --title "S168 Fuel Management & Driver Logbooks" \
  --body-file docs/fuel/S168_Fuel_Final_Implementation_Report.md
```
(Or open it in the GitHub UI: base = `fleet`, compare = `feat/s168-fuel`.)

Merge PR #1 once reviewed/green.

## 4. PR #2  →  merge `fleet` into `main`

After PR #1 is merged into `fleet`:
```bash
git fetch origin
git switch fleet
git pull
gh pr create --base main --head fleet \
  --title "Phase 1: S166 Fleet & S168 Fuel into main" \
  --body "Brings the S166 Fleet & Vehicle Management and S168 Fuel Management & Driver Logbooks systems into main. Subsequent Phase-1 systems branch from main."
```
Merge PR #2. From then on, branch each new Phase-1 system from `main`.

---

### Notes
- If `gh` isn't installed: `winget install GitHub.cli` then `gh auth login`.
- Keep the two PRs separate: PR #1 isolates the Fuel change for review against `fleet`; PR #2 is the
  integration of the reviewed `fleet` line into `main`.
- Nothing here rewrites the already-committed S166 history on `fleet`.
