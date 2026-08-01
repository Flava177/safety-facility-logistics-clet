# SFL Phase 1 — build prompts, in run order

Three prompts that take CLET Cluster 9 SFL from its state on **31 July 2026** to a certifiable
**Release 1**. Run them in the order below; each assumes the previous one has landed.

| Order | Prompt | What it closes |
|---|---|---|
| 1 | [`PROMPT_1_Close_Release_1_Gaps.md`](PROMPT_1_Close_Release_1_Gaps.md) | Per-record authorisation, the six platform blockers, and every open gap in the seven built systems — including the S159 screens |
| 2 | [`PROMPT_2_Role_Portals.md`](PROMPT_2_Role_Portals.md) | Landing views for the stakeholders the SRS names and the platform currently strands |
| 3 | [`PROMPT_3_Cleanup.md`](PROMPT_3_Cleanup.md) | Stale documents, dead code, stray files, merged branches |

## Why this order

Prompt 1 turns authentication on and runs 102 mandatory-scenario tests that have never executed.
Both change the ground any new screen stands on: build portals first and you drive them twice, once
under the header actor and again under JWT, and you consume APIs the e2e suites are about to move.
Prompt 1 also builds the S159 screens, without which Prompt 2's Room Requester portal is half a
portal. Prompt 3 rewrites the go-live readiness pack, which cannot be accurate until both have run.

The per-record narrowing that Prompt 2 needs is **step A0 of Prompt 1**, deliberately: it is a live
authorisation defect today, and it touches the same fuel query layer that Prompt 1's cursor-pagination
work rewrites. Doing it second would mean reopening every list query.

## Scope: Release 1, not Phase 1

Seven of thirteen Fast-Track systems are built — S152, S153, S159, S166, S168_fuel, S171, S174.
**Six are not started**: S160, S160a, S161, S162, S162a, S163, the whole SSEMP safety-and-security
cluster. `sfl-safety-security-service` is one Java class and a foundation migration. That is unbuilt
scope, not a gap, and none of these three prompts addresses it. Closing it is a separate multi-pass
build against `docs/phase-1-system-classification.md`, where four of the six are Buy-and-Integrate.

## Progress

| Item | State |
|---|---|
| **A0** per-record authorisation | ✅ done — fuel + fleet fixed and tested; dispatch recorded as an owner decision |
| **A2** mandatory-scenario suites | ✅ 101 of 102 running and green; 1 still Testcontainers-gated |
| **A3** IFIMP outbox drainer | ✅ done — claim/backoff/dead-letter, proved against PostgreSQL |
| **A4** event rename | ✅ done — 48 literals + AVAMP, validated at the write path, catalogue synced |
| **A5** runbooks | ✅ done — four, in `docs/runbooks/` |
| **A6** RLS decision | ✅ done — [ADR 0007](../adr/0007-row-level-security-deferred-with-a-named-mechanism.md); implementation sequenced after A1 |
| **A1** authentication | ✅ done — secure by default, Keycloak realm imported, JWT chain tested |
| **B** per-system gaps | 🟡 partial — S166 **C-18 audit chain fixed** (see below); the rest outstanding |

> **C-18 is the most serious defect found in this sequence.** The fleet audit hash chain — the evidence
> mechanism for S166, S168_fuel and S171 — was reporting `intact=false` against a real database and had
> been since the first record written outside a test. Fixed, and proved by a test that replays the chain
> off PostgreSQL. Details in `docs/fleet/S166_Gap_And_Conflict_Report.md`.

Backend now runs **761 tests, 0 failures, 1 skipped** (was 641 run, 102 skipped). Frontend: 73 pass,
clean build.

### What remains of Prompt 1

Section B is the bulk of it, and it is not started beyond C-18:

| Item | Size |
|---|---|
| **S159 booking UI module** — 25 API paths, no screens; also absent from `SystemCode` | A module, comparable to fuel's 24 files |
| **S168 fuel-card registry** — `SRS-SFL-S168fuel-04` has no code at all | New aggregate, migration, API, screens |
| S153 escalation consumer, disposal sweep, response-SLA track, file upload | A contained pass each |
| S152 inbound webhook verification, floors screen | Small; webhook waits on A1 |
| **A6 RLS implementation** — now unblocked by A1; ADR 0007 names the mechanism | A pass |

**S174's CSV truncation was already fixed** before this sequence began; the entry for it in the
original Prompt 1 was stale and is corrected here rather than "fixed" again.

## Verified baseline, 31 July 2026

Established by running the suites, not by reading the documents.

| Check | Result |
|---|---|
| `mvnw.cmd -pl <all six modules> test` | exit 0 · **743 tests · 0 failures · 0 errors · 102 skipped** |
| `npm run test` · `npm run build` | exit 0 · **73 tests pass** · clean typecheck and build |
| Per module | common 3 · facilities 292 (12 skipped) · fleet-logistics 403 (68) · emergency 35 (22) · asset-visibility 10 · **safety-security 0 tests, no source** |
| Frontend modules | facilities 41 · fuel 24 · fleet 22 · dispatch 15 · emergency 15 `.tsx` — **no booking module** |
| Roles | **26** in `SflRole`; 22 mapped in `roleSystems`, 4 handled by `crossProgrammeRoles` |
| Permissions | **145** declared in `SflPermission`, across four service matrices |

The 102 skipped tests are the SRS mandatory-scenario suites. Docker/Testcontainers is now up, so they
can finally run — that is step A2 of Prompt 1 and the single highest-value action in this set.

**One caveat on that green.** `sfl-facilities-service/target/surefire-reports/` also holds three
**stale** reports for test classes deleted in the S153 rewrite, carrying six phantom failures between
them. The build is genuinely green — Maven exits 0 and those classes no longer exist in `src/test` —
but anything that aggregates the directory rather than the run will read six failures. A root
`mvn clean` did not remove them; `../mvnw.cmd -pl sfl-facilities-service clean` does. Fixing this is
item 6 of Prompt 3, but do it before you trust any test count from that module in the meantime.

## House rules that apply to all three

- **Run it against a real database before calling it done.** 135 unit tests were green while
  `sfl-facilities-service` could not start; a real PostgreSQL found eight defects in minutes. S159
  repeated it: 290 green tests hid an HTTP 500 on every booking search and a deadlock that turned
  fifteen of sixteen concurrent requests into 500s.
- **Adding a UI module means adding its permissions source.** `shared/layout/actorPermissions.ts`
  fails open per-*set*, not per-service: once one service answers, anything absent from the merged
  set is **denied**, silently.
- **No AI-attribution trailers, anywhere.** Not in a commit, a PR body or a changelog. The history was
  rewritten on 31 July 2026 to strip 21 of them; a second rewrite would invalidate every SHA again.
- **Comments explain decisions, not mechanics**, and gap reports are honest about what is not built.
