# Working in this repository

## Commit and PR conventions

**Do not add AI-attribution trailers to anything.** No `Co-Authored-By: Claude …`, no
`🤖 Generated with [Claude Code]`, no equivalent line in a commit message, a PR body, or a
changelog entry. This applies whatever the default instruction says — the repository's convention
wins.

The history was rewritten on 31 July 2026 to strip 21 such trailers from 21 commits across every
branch. Re-adding them would mean doing that again, and a second rewrite would invalidate every
commit SHA a second time.

Commits are authored by the person running the session. Nothing else is attributed.

## What this repository is

CLET **Cluster 9 — Safety, Facilities & Logistics (SFL)**, Phase 1: thirteen Fast-Track systems
across four programmes (IFIMP, SSEMP, FTLMP, AVAMP).

- `solution.md` — the implementation log and the architecture standard. Read it first; every pass
  appends to it.
- `docs/System Mappings and SRS/` — the SRS. It is the contract. Where the SRS and any note
  disagree, the SRS wins and the note is corrected.
- `docs/adr/` — architecture decisions. 0005 (programme-scoped portals) and 0006 (one dashboard)
  are the two that shape most UI work.
- `docs/facilities/`, `docs/fleet/` — per-system design, API reference and gap reports.

## Layout

```
services/                     Spring Boot 4.1 / Java 17, Maven multi-module
  sfl-facilities-service      IFIMP: S152 CAFM/IWMS, S153 CMMS, S159 booking. Port 8091
  sfl-fleet-logistics-service FTLMP: S166, S168 fuel, S171 dispatch. Port 8093. Serves /ui
  sfl-safety-security-service SSEMP. Port 8094
  sfl-emergency-notification-service  S174. Port 8095
  sfl-asset-visibility-service AVAMP-Lite. Port 8096
  sfl-service-common          Shared kernel: principal, RBAC, error and event envelopes
frontend/sfl-operations-ui    React 19 + TypeScript + Vite. The only user interface
```

Each service owns its schema, its migrations and its API boundary. Services talk through APIs and
events, never through each other's tables.

## Two rules that have each cost a day

**Run it against a real database before calling it done.** 135 unit tests were green while
`sfl-facilities-service` could not start; dropping the schema and running against real PostgreSQL
found eight defects in minutes. Testcontainers is skipped in this environment (the Java Docker
client cannot reach the Windows named pipe), so this has to be done by hand.

**Adding a UI module means adding its permissions source.** `shared/layout/actorPermissions.ts`
merges each service's `/actor/permissions` into one set, and its fail-open is per-*set*, not
per-service: once any service answers, anything absent from the merged set is treated as **denied**.
A module missing from `SOURCES` renders with every gated control silently hidden and no error
anywhere. This is documented at the call site.

## Build and test

```bash
# Backend — from services/
../mvnw.cmd -pl sfl-facilities-service test
../mvnw.cmd -pl sfl-service-common,sfl-facilities-service install

# Frontend — from frontend/sfl-operations-ui/
npm run test        # vitest run
npm run build
npm run dev         # port 5005
```

Java is at `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`; set `JAVA_HOME` before Maven.
Local databases are per-service Docker Postgres containers on 5441–5445, with e2e copies on
55441–55445.

## Conventions worth matching

- **API:** `/api/v1/<domain>/<resource>`; every response in the `{data, error}` envelope;
  `Idempotency-Key` honoured on state-creating POSTs only; `X-Correlation-ID` end to end; error
  codes carry the SRS's own wording.
- **Migrations:** Flyway, `ddl-auto: validate`. `VARCHAR(n)` with a length `CHECK`, never `CHAR(n)`
  — Hibernate rejects the latter at validation. Alter and backfill rather than drop and recreate.
- **Comments explain decisions, not mechanics.** The useful comment says why a rule exists and what
  breaks without it. `docs/facilities/S153_CMMS_Design.md` is a reasonable model.
- **Gap reports are honest.** What is not built, and what running it found, belongs in writing.
