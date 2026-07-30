# SFL Phase 1 — Gap Closure Build Prompt

The canonical copy of the prompt for the gap-closure round. Paste it whole.

Written 29 July 2026, after S166, S168, S171 and S174 shipped their UI modules. The intent is to
**close every recorded gap on the four systems already built before a fifth is started**, so the
pattern the next module copies is the finished one rather than the one with holes in it.

---

## The prompt

> Close every remaining gap on the four SFL systems that already have UI modules — **S166 Fleet,
> S168 Fuel, S171 Courier & Dispatch, S174 Emergency Mass Notification** — before any new system is
> started. Work on the branch `fix/phase1-gap-closure`.
>
> ### Read first
>
> 1. `docs/frontend/SFL_Operations_UI_Module_Playbook.md` — the whole thing, including §10, §11 and
>    §12. It is the record of what four modules already learned; do not rediscover it.
> 2. The four gap registers. **These are the specification for this round** — every gap is numbered,
>    evidenced and has a "to close" note:
>    - `docs/fleet/S166_Frontend_Gap_Register.md` — 12 gaps
>    - `docs/fuel/S168_Fuel_Frontend_Gap_Register.md` — 13 gaps, 11 already closed
>    - `docs/dispatch/S171_Dispatch_Frontend_Gap_Register.md` — 8 gaps
>    - `docs/emergency/S174_Emergency_Frontend_Gap_Register.md` — 12 gaps
> 3. `docs/architecture/microservices-realignment.md` and `docs/adr/0005-*` — the programme, system
>    and service map, and the navigation rule.
>
> ### What the platform is
>
> **13 systems, 4 programme modules, 5 deployable services, 5 databases.** Those counts do not line
> up and that is deliberate. `sfl-fleet-logistics-service` (8093) holds fleet, fuel and dispatch as
> three modules of one deployable; `sfl-emergency-notification-service` (8095) is its own deployable
> but still SFL.SSEMP. No service reads another's schema. Keep it that way.
>
> Start what you need: `.\start-fleet.ps1` for 8093, and from `services/`,
> `..\mvnw.cmd -pl sfl-emergency-notification-service spring-boot:run` for 8095.
>
> ### Non-negotiable
>
> - **Establish the contract from the running service**, never from documentation. The S168 API
>   inventory listed three endpoints that do not exist; the S166 one disagrees with its controllers
>   in six places. When a document and the code disagree, the code wins and the document gets fixed.
> - **No mock data.** Anything that cannot be sourced stays a recorded gap.
> - **Do not fork the design system.** `src/shared` is the component library. Additions to it are
>   additive; nothing existing changes behaviour for an earlier module.
> - **Every ordering ends in `id`**, so a page boundary cannot skip or repeat a record.
> - **Backwards compatibility is not required** where a shape is wrong — change it and fix all
>   callers, as the S168 round did. Compiling is not the bar; a stale incremental build reported
>   BUILD SUCCESS while the E2E tests still called old signatures. Use `clean test-compile`.
> - British English. Sentence case. No emoji.
> - **Update each gap register as you go** — mark closed, state what closed it, and keep the
>   evidence. A register that still lists a closed gap is worse than no register.
>
> ### Round 1 — the shared backend pattern (closes 6 gaps across 2 modules)
>
> Dispatch gaps 1, 2, 4 and emergency gaps 1, 2, 4 are **the same three gaps S168 already solved**.
> Do them together, once, and port the solution rather than reinventing it:
>
> - `FuelPageResponse`, `FuelRepository.FuelPage<T>` and `Paging(page, size, sort)` with
>   `MAX_SIZE = 200`
> - the `Where` / `Order` / `page` helpers and per-resource sort allow-lists in `JdbcFuelRepository`
> - `FuelApplicationService.history(resourceType, id, actor)`
>
> **Definition of done.**
> 1. Every dispatch and emergency collection returns a paged envelope with `content`, `page`, `size`,
>    `totalElements`, `totalPages`, `first`, `last`, `sort`. Both `useClientWindow` bindings and the
>    `WindowNotice` banners are then **deleted**, not left dormant — including
>    `shared/hooks/useClientWindow.ts` and `shared/components/WindowNotice.tsx` if nothing else needs
>    them.
> 2. Dispatch exceptions filter server-side on manifest, item, severity, assignee, security relevance
>    and SLA standing. Emergency activations filter server-side on mode, priority, incident reference
>    and a date range. Every "Filters the loaded records." helper text on those screens is then
>    removed, because it is no longer true.
> 3. Both services expose a transition-history read per aggregate. Emergency already **writes** a
>    full history via `saveActivationHistory` and reads it nowhere — expose it. The reconstructed
>    timeline in `ActivationDetailPage` and its `DerivedNote` caption are then replaced by the real
>    thing.
>
> ### Round 2 — S174 Emergency, the remaining gaps
>
> Do **gap 3 first**: it is the most consequential gap on the platform. This service publishes no
> read of its inbound provider feed, and that feed is the only thing that ever writes `delivered`,
> `failed` and `acknowledged`. A screen showing 480 sent and 0 delivered cannot distinguish "no
> provider configured" from "every callback rejected for a bad signature". Add the same
> `IntegrationInboxPort` read the fleet-logistics service already has — processed count, rejected
> count, recent messages with source, event type, attempt count and status — and replace the
> explanatory panel on `EmergencyIntegrationPage` with it.
>
> Then **gap 11**, which is the only gap on the platform with a compliance consequence: the CSV
> export silently truncates at 500 activations. Page the query or state the truncation in the file.
>
> Then gaps 5, 6, 7, 8 and 12 — detail endpoints, master-data correction and lifecycle, the
> unreachable transitions, per-recipient delivery detail, and a dashboard breakdown. **Gap 6 has a
> sharp edge worth fixing properly**: `AudienceGroup.recipientCount` cannot be corrected through any
> endpoint, and a group sized at zero sends to nobody while reporting a completely successful
> broadcast.
>
> ### Round 3 — S171 Dispatch, the remaining gaps
>
> Gaps 3, 5, 6, 7 and 8: a scan-batch list, dashboard volume counts, structured custody gaps instead
> of the `REASON@HOP(detail)` string the client currently parses, site-wide custody and receipt
> reads, and item detail on manifest lines. When gap 6 lands, delete `parseCustodyGap`.
>
> ### Round 4 — S166 Fleet, all 12
>
> Nothing here has been closed. Take them in this order:
>
> - **The four `MISSING`** — telematics/movement history, standalone periodic inspection, evidence
>   search, integration inbox search. These are real absences and need endpoints.
> - **The four `WORKAROUND`** — readiness has no endpoint of its own, evidence has no search,
>   compliance is only reachable per vehicle. Each is a screen doing arithmetic the service should
>   own.
> - **The six `DOC`** — the API inventory disagrees with the controllers. **Fix the documents to
>   match the code**, and say in the register that the code was correct. Do not change working
>   endpoints to match a wrong document.
>
> ### Round 5 — S168 Fuel, the last two plus three enhancements
>
> - **Gap 7** — correct `S168_Fuel_Domain_And_State_Model.md` to describe `RESUBMITTED`. Document
>   defect; the code is right.
> - **Gap 8** — decide whether `VALIDATING`, `MATCHED` and `REJECTED` are implemented or removed.
>   Removing an enum value breaks stored data, so if they stay, say why in the register.
> - A spend/volume **time-series endpoint**, so the dashboard stops bucketing by day in the browser.
> - An **anomaly count-by-type endpoint**, so the by-type chart stops reading a page of records.
> - **`GET /fuel/imports/{id}/rows`, paged** — the detail read currently returns every row.
>
> ### Round 6 — cross-cutting
>
> 1. **`npm run lint` does not work.** There is no `eslint.config.js`, so the script fails repo-wide
>    and has never run. Add a flat config for React 19 + TypeScript strict, fix what it finds, and
>    make it pass with `--max-warnings 0`. Several files already carry
>    `eslint-disable-next-line react-hooks/exhaustive-deps` comments written against a linter that
>    was never executed — verify each is still justified.
> 2. **Four legacy consoles are still served and now duplicate the React modules** —
>    `/fleet`, `/fuel`, `/dispatch` under `sfl-fleet-logistics-service/src/main/resources/static/`
>    and `/emergency` under the emergency service. Two front ends over one service will drift, and
>    the vanilla-JS ones have none of the state guards, permission awareness or derived-figure
>    captioning. **Recommend a decision and say what removing them would break** — do not delete them
>    unilaterally.
> 3. **The browser walkthrough.** Thirty-nine screens have been verified call-path by call-path
>    against live services and **not one has been observed rendering.** If the Chrome extension is
>    connected, walk every screen: each register, each detail, each dialog opened and cancelled, the
>    empty states, the error states, and the programme filtering (drop the SSEMP roles from
>    `VITE_SFL_ROLES` and confirm the emergency group disappears and `/emergency` is refused). If it
>    is not connected, say so plainly and do not claim otherwise.
>
> ### Verification — the bar
>
> - `npm run build` passes; `npm run lint` passes once round 6 lands.
> - The **full** backend suite passes for both services, with **no tests skipped**. Testcontainers
>   cannot reach the Docker pipe from Git Bash — pass the e2e database explicitly, e.g.
>   `-DSFL_EMERGENCY_NOTIFICATION_TEST_DB_URL=jdbc:postgresql://localhost:55445/sfl_emergency_notification_service_e2e -DSFL_TEST_DB_USERNAME=sfl -DSFL_TEST_DB_PASSWORD=sfl`.
>   A skipped test is not a passing test; report the count either way.
> - **Every new or changed endpoint is driven against the running service** with the exact headers
>   the client sends. Two defects were found this way and by no other means: a CSV report answering
>   406 to `Accept: application/json`, and an audit search returning 500 on every call because
>   Postgres cannot type a bare `? IS NULL` parameter. Neither is visible from the source.
> - Drive the **refusals** too, not only the happy paths. A guard that is never exercised is a guard
>   that is assumed.
>
> ### Deliverables
>
> 1. Working code on `fix/phase1-gap-closure`, with each round a separate commit so the history is
>    reviewable.
> 2. **All four gap registers updated** — every closed gap marked closed with what closed it; every
>    gap that stays open carrying a stated reason.
> 3. The playbook updated with whatever this round learns, in the numbered style of §9 and §11.
> 4. A PR into `main` that states, per system, how many gaps closed and how many remain and why.
> 5. **Report honestly.** If something cannot be closed, say which, why, and what it would take.
>    Scaling the work down is the user's call, not yours.

---

## Why this order

Round 1 first because it closes six gaps with one piece of work and changes the shape everything
else is built on — doing S171 and S174 detail work before pagination lands would mean touching the
same call sites twice.

Round 2 before round 3 because S174 gap 3 is the only gap that makes a screen unable to answer a
question an operator will actually ask, and gap 11 is the only one with a compliance consequence.

Rounds 4 and 5 after, because S166's gaps are mostly documentation drift and S168 has two
deliberate open items and three enhancements — real work, but none of it blocking.

Round 6 last only because it is independent, not because it is optional. The lint config in
particular has never run in this repository.

---

## Outcome — corrections to the prompt itself

The prompt above is kept verbatim, including where it turned out to be wrong.

- **Round 6.2 said four legacy consoles. There are five**, and the one it missed is the one that
  matters: `sfl-facilities-service` serves the only user interface for the whole of SFL.IFIMP at
  `http://localhost:8091/`. It cannot be retired with the other four, because nothing replaces it.
- **They are not consoles.** Everything a user opens is a dashboard. The word has been retired from
  page titles, headings, READMEs, code comments and documents.

Both are recorded in
[ADR 0006 — One dashboard, and the retirement of the per-service pages](adr/0006-one-dashboard-and-the-retirement-of-the-per-service-pages.md),
which carries the recommendation round 6.2 asked for. Nothing has been deleted.

