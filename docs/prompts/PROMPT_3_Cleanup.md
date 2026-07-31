# PROMPT 3 — Cleanup, consistency and the go-live record

> Run last, after [`PROMPT_1_Close_Release_1_Gaps.md`](PROMPT_1_Close_Release_1_Gaps.md) and
> [`PROMPT_2_Role_Portals.md`](PROMPT_2_Role_Portals.md). Item 1 cannot be accurate until both have landed.

Housekeeping and record-keeping only. **No new capability.** Every item below was verified present in
the working tree on 31 July 2026.

---

## 1. The stale go-live readiness pack — highest value in this prompt

`docs/SFL_Phase1_Workflow_Review_and_GoLive_Readiness_Pack.md` (v1.1, 29 July 2026) is the document
that carries the Go-Live recommendation to the Registrar and gate owners G1–G6. It is out of date in
two separate ways, and it is the one artefact here that would embarrass the programme in a room.

**§A.1, §A.2 and §A.3 describe a build that no longer exists.** The scoreboard still records S152 and
S153 as "thin", S159 as "not built", and totals of "3 built · 3 partial · 7 not built". Three passes
have landed since: S152 became the full IFIMP platform, S153 was rewritten on it, and S159 was built
and now — after Prompt 1 — has screens. Re-cut all three sections against the real state.

**§C.5 "The user-facing estate" is wrong in a different way.** It describes service-hosted Bootstrap
dashboards served at `/dispatch/` and `/emergency/`, and a facilities workspace at the service root.
**ADR 0006 retired all of those** into the React operations UI; the facilities static page is now a
notice page that redirects to `/ui/facilities`. Rewrite §C.5 from the actual module inventory, and
fold in the portals from Prompt 2.

While there: strike the blockers Prompt 1 closed from §A.3 and §F.1, and re-state the ones that
remain. If S174 still has no vendor gateway, that stays a blocker and must be said plainly.

## 2. Documentation defects already logged and never fixed

- **`solution.md` cites a file that does not exist.** It references
  `docs/System Mappings and SRS/CLET_Cluster9_SFL_Phase1_SRS_v1.0.docx`; the file on disk is
  `SFL_SRS.docx`. Recorded as S166 C-13 and still open.
- **Workplan §4.3's endpoint→requirement table contradicts SRS semantics** (S166 C-02) — it maps
  `/fleet/drivers` to S166-05 (Dashboards) and `/fleet/trips/{id}/inspections` to a
  requirement that does not exist. If Prompt 1 resolved the owner decision, apply it here.

## 3. The legacy root application

`src/main/java/gh/edu/clet/sfl/ifimp/**` — **48 Java files** — is a second Spring Boot application at
the repository root, designated migration/reference material by
`docs/architecture/microservices-realignment.md` and untouched since (S166 C-14). It does not
participate in the `services/` reactor.

Decide and act: delete it, or move it under `archive/` with a README saying what it is and why it is
kept. As it stands it reads as live code to anyone opening the repository for the first time, and the
root `pom.xml` still compiles it.

## 4. Stray working files at the root

- `COMMIT_MSG_ui-serving.txt` — a commit message left behind on 28 July.
- `tmp/` — `c.out`, `commitmsg.txt`, `commitmsg2.txt`, `fleet-srs-extract/`, `srs_build/`.
- `tests/` — an empty directory.

Delete or gitignore. Then confirm `tools/build_sfl_srs.py` and `tools/extract_srs_sources.py` are
still wanted; if they are, give them a one-line README, because a script that regenerates the SRS is
not obviously safe to run by accident.

## 5. Twenty merged branches

`git branch --merged main` lists twenty branches fully merged into `main`. Delete them locally and on
`origin`. **Keep** the two `archive/*` snapshots — `archive/java-migration-snapshot-2026-07-21` and
`archive/pre-java-cleanup-2026-07-21` — which are deliberate history.

## 6. Build hygiene

Confirm `.gitignore` covers every `target/` directory.

**Stale surefire XML is producing a false reading right now.** Three reports for test classes deleted
in the S153 rewrite survive in `sfl-facilities-service/target/surefire-reports/` and report **six
phantom failures** to anything parsing that directory, while the build itself is green:

| Report | Reports |
|---|---|
| `TEST-…maintenance.application.WorkOrderServiceTest.xml` | 1 error |
| `TEST-…maintenance.domain.FacilityFaultTest.xml` | 1 error, 1 failure |
| `TEST-…maintenance.domain.WorkOrderTest.xml` | 3 errors |

None of those three classes exists in `src/test` any more. A repository-root `mvn clean` on 31 July
did **not** remove them — verify with `find services -name "TEST-*WorkOrderServiceTest.xml"` before
assuming it is done, and clean the module explicitly:
`../mvnw.cmd -pl sfl-facilities-service clean`.

Then make sure CI cleans rather than trusting an incremental `target/`, and that any report parser
reads the current run rather than the whole directory.

## 7. Two environment notes worth pinning in the README

- **JDK.** `S166_Operations_And_Verification_Guide.md` records the machine default as JDK 11 while the
  reactor needs 17+; `PATH` still carries `C:\Program Files\Zulu\zulu-11\bin`. The supported JDK is at
  `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`.
- **IDE VCS mapping.** `.idea/vcs.xml` carried a stale Git root mapping for
  `frontend/sfl-operations-ui`, which is not a repository and not a submodule — the cause of the
  "Invalid VCS root mapping" warning on every project open. Fixed on 31 July 2026 by removing the
  second `<mapping>` line, leaving only the project-root mapping. `.idea/` is gitignored, so this will
  recur for anyone else who opens the project; one line in the README saves them the diagnosis.

## 8. Consistency sweep

- **One naming convention for gap reports.** Today they are `S166_Gap_And_Conflict_Report.md`,
  `S168_Fuel_Gap_And_Conflict_Report.md`, `S153_Gap_And_Conflict_Report.md` and
  `S171_Dispatch_Frontend_Gap_Register.md` — three shapes for one kind of document. Pick one, rename,
  and fix the inbound links.
- Confirm every ADR in `docs/adr/` is referenced from `solution.md`, including any added by Prompt 1.
- Confirm every `SRS-SFL-*` identifier cited in any report resolves to a requirement that exists in
  `SFL_SRS.docx`. `SRS-SFL-S166-06` is the known case: it is cited in the workplan and does not exist.

---

## What not to touch

**Do not** refactor working code, rename packages, reorganise module layout, or "tidy" comments.

This codebase's comments carry the reasoning behind its non-obvious rules — why the booking interval
is half-open `[start, end)` and what breaks either way, why `actorPermissions.ts` fails open per-set
and what that costs, why an empty state must describe what is visible to you rather than what exists,
why the readiness port is declared by readiness rather than by maintenance, why a vendor is narrowed
per person and not per firm. That prose is the most valuable thing in the repository and the least
recoverable if it is trimmed for brevity.

**No AI-attribution trailers in any commit, PR body or changelog entry.** The history was rewritten on
31 July 2026 to strip 21 of them from 21 commits across every branch; a second rewrite would
invalidate every commit SHA a second time.
