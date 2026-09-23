# Building a new backend module

A checklist and prompt template for adding a new system (a new SRS module, e.g. an S1xx) to one of
the three services. Written after building S160a, S161, S162 and S162a on `sfl-safety-security-service`
back to back - it is the distilled version of what worked, not a plan written in advance of doing it.

Read this before starting a new module. Point a fresh session at this file first.

## Before writing any code

1. **Find the SRS requirements for the system.** They live in `docs/srs/CLET_Cluster9_SFL_Phase1_SRS_v1.0.docx`.
   Convert it once with `textutil -convert txt -output /tmp/srs.txt "docs/srs/CLET_Cluster9_SFL_Phase1_SRS_v1.0.docx"`
   (macOS; `pandoc` if available) and `grep`/`sed` the module's `SRS-SFL-S1xx-NN` sections out of the
   text rather than re-reading the whole document. Each requirement gives you: a user story, an
   acceptance criteria list, a workflow, and an error state with its exact user-facing message. That
   error-state wording belongs in the error code / exception message, verbatim where practical - it is
   already written for the person who will read it.
2. **Read `docs/planning/phase-1-system-classification.md`** for the system's Delivery Decision
   (Build / Buy and Integrate / Hybrid) and its **SFL Ownership** column. That column is the actual
   scope: for a Buy-and-Integrate system, SFL owns the workflow/audit/dashboard layer, not the device
   control. Do not build a command-and-control port to the vendor device unless the classification
   says SFL governs actuation (it almost never does - S162a is the extreme case: no outbound
   vendor-command port at all, by design, and an ArchUnit rule enforces that).
3. **Check `docs/integration/event-catalog.md` for a pre-reserved event name.** The catalog is written
   ahead of the builds that satisfy it - S161's `sfl.ssemp.camera-health-changed.v1` and
   `sfl.ssemp.cctv-evidence-requested.v1` existed in the catalog before any CCTV code did. Reuse the
   reserved name; don't invent a parallel one. If genuinely nothing is reserved, add it to the catalog
   in the same style as the existing block for that platform.
4. **Check `incident/domain/model/IncidentSource.java`** (SSEMP) or the equivalent shared discriminator
   for the platform you're on. Sibling modules often reserve an enum slot for a system before it's
   built (`CCTV_SEED`, `ACCESS_SEED`, `INTRUSION_SEED`, `FIRE_SEED` were all reserved from the S163
   build, before S160a/S161/S162/S162a existed to use them). Use the reserved slot rather than adding
   a new one.
5. **Pick the closest already-built module as your literal template** and read it in full before
   writing anything. Prefer the most recently built module in the same service over an older one - it
   has already absorbed the previous module's fixes. When this file was written, `accesscontrol`
   (S160a) was the best template for a new SSEMP module; check what's newest when you read this.

## The package shape (every module, every service)

```text
<service>/src/main/java/.../<module>/
  api/                    controllers, an actor resolver, an API exception handler
  application/
    port/                 repository port(s) + outbound integration port(s)
    service/               one service class per aggregate/workflow area, plus an access-policy class
  domain/
    event/                 the module's event-type enum
    exception/              error-code enum + a single module exception class
    model/                  the domain model - framework-free
    policy/                 the module's PermissionMatrix
  infrastructure/
    integration/            recorded/simulated adapters for every external system; the one adapter
                             class that's allowed to import a sibling module's package, if any
    persistence/            JPA entities, Spring Data repositories, a repository adapter, search-page
                             adapter if the module needs filtered listing
```

Mirror this exactly. Don't reorganize it "better" - every module in the codebase looks like this, and
an ArchUnit test enforces the dependency direction (`api -> application -> domain`,
`infrastructure -> application/domain`, nothing points into `infrastructure`, `domain` imports no
framework).

## Vendor and cross-module integration

- **Every external vendor system is a recorded/simulated adapter behind a port**, never a real network
  call - no real vendor connection exists anywhere in this codebase yet (fuel providers, SMS/voice,
  telematics, door controllers, VMS, intrusion panels are all stood in for). Name the port for what it
  does (`AccessControlVendorGatewayPort`), name the adapter `RecordedXxxGateway` or similar, and have
  it log/return a realistic response rather than throwing `UnsupportedOperationException`.
- **Inbound vendor events go through an authenticated inbox**, not straight into a domain service.
  Mirror `AccessControlIntegrationInbox` / `CctvIntegrationInbox` / `IntrusionIntegrationInbox` /
  `LifeSafetyIntegrationInbox`: HMAC signature over the raw payload, a timestamp window (5 minutes is
  the established default), a source allowlist, schema validation, and an idempotency key checked
  against a dedicated inbox table - all before any domain action. Check
  `sfl-service-common`'s `security` package for shared HMAC/signature helpers before writing a new one.
- **A cross-module reference within the same service goes through a port + a single adapter class**,
  never a direct import from a service class into a sibling module's package. `IncidentSeedingPort` /
  `IncidentSeedingAdapter` is the pattern: the port lives in the new module's `application/port`, the
  adapter lives in `infrastructure/integration`, and it is the *only* class in the new module allowed
  to import the sibling module's package. If two modules are in the *same deployable* (e.g. S162a
  calling S174's break-glass activation), this is a real in-process call to the real service, not a
  recorded stand-in - only cross-*service* calls (different deployable) get simulated. Check with a
  package-existence probe if the sibling module might not exist yet in your build order.
- **A cross-service reference (different deployable) is never a direct call.** Publish a domain event
  through the existing `IntegrationEventPublisher`/outbox and stop there - there is no drainer yet
  anywhere in this codebase, so the event is recorded, not delivered, and that's the correct, complete
  scope for this pass. Don't reach into another service's code or database.
- **Hold every cross-schema, cross-service or vendor-side identifier by value** (a `String`/`UUID`
  column with no foreign key), never a real FK across a schema or service boundary.

## Roles and permissions

1. Reuse an existing `SflRole` before adding one. Check `sfl-service-common/.../SflRole.java` - roles
   accumulate across every system (`SECURITY_DIRECTOR`, `SOC_OPERATOR`, `COMMAND_ROLE`,
   `INTEGRATION_ENGINEER`, `AUDITOR` cover most SSEMP user stories already). Only add a new constant
   when a user story genuinely names a role nothing existing fits, in the same commented-block style
   as every prior addition (name the SRS system it's for, note that adding an enum constant changes no
   existing behaviour).
2. Add `<MODULE>_*` permissions to `SflPermission.java`, one per distinct authorised action across the
   SRS's user stories (read, create, approve, acknowledge, manage - split by authority level, not by
   screen).
3. Write `<Module>PermissionMatrix` as a `public final class` with a static `EnumMap<SflRole,
   Set<SflPermission>> MATRIX`, a `grants(roles, permission)` method, and a comment above each role's
   entry naming which SRS user story justifies it. Copy `AccessControlPermissionMatrix`'s shape
   exactly.
4. **Wire the new matrix into both shared union points** - this is the step every prior module missed
   at least once:
   - `ActorPermissionsController` (lives in the `emergency` package for SSEMP, `shared` for the other
     services) - add the import and OR it into the `.filter(...)` chain, and update the class javadoc's
     "Also answers for ..." list.
   - `RoleMatrixDocumentTest` (same package as the controller) - same import, same OR chain.
   - Regenerate the doc after wiring both:
     `mvn -f services/pom.xml -pl <service> -am test -Dtest=RoleMatrixDocumentTest -Dsfl.roleMatrix.write=true -Dsurefire.failIfNoSpecifiedTests=false`

## Migrations

- Next free `V<n>__snake_case_name.sql` in the service's `src/main/resources/db/migration/`. Check the
  actual highest existing number on disk before picking one - don't assume from memory, and definitely
  check again after a merge, since parallel branches collide on the same number.
- Same schema the service already owns (e.g. `safety_security` for every SSEMP sub-context except
  S174's own `emergency_notification`) - a service's schema is shared by every module inside it; the
  module boundary is the table set and the code package, not a separate schema.
- Every table: `created_by`, `created_at`, `last_modified_by`, `last_modified_at`, `record_version`,
  `source_channel`, `correlation_id`. `CHECK` constraints for every enum-like column. A unique
  constraint for whatever makes ingestion idempotent (usually `(source_system, external_id)` or
  `(source_system, idempotency_key)`).

## Tests

- An ArchUnit test (`<Module>ArchitectureTest`): domain is framework-free, domain doesn't depend on
  api/infrastructure. If the module has an architectural invariant that matters more than the generic
  layering rule (S162a's "no outbound vendor-command port"), assert it explicitly and name it in the
  test so a future change that violates it fails loudly with an explanation, not a generic layering
  complaint.
- Unit tests per service class, covering the SRS's own acceptance criteria and error states directly -
  each acceptance-criteria bullet in the SRS is close to one test.
- A mandatory-scenarios end-to-end test if the service has that convention already (check for one in a
  sibling module first).

## Verification, in order, every time

```bash
# from services/
../mvnw -o compile test-compile -pl sfl-service-common,<service> -am
../mvnw -o -pl <service> -am test -Dtest='*<Module>*' -Dsurefire.failIfNoSpecifiedTests=false
../mvnw -o -pl <service> -am test               # the whole service's suite, not just the new module
```

The middle step needs `-am` even though `sfl-service-common` already compiled in step one - without it
Maven resolves the shared-kernel dependency from the local `~/.m2` repo rather than the sibling
module's freshly compiled classes, and a brand-new `SflPermission` constant reads back as
`NoSuchFieldError` even though everything actually compiles. This looks like a real bug the first time
you see it. It isn't - it's a stale classpath from skipping `-am`.

Don't stop at "my new module's tests pass" - run the whole service's suite. Wiring a new matrix into
`ActorPermissionsController`/`RoleMatrixDocumentTest` is exactly the kind of shared-file change that
breaks a sibling module's existing test if done carelessly, and the only way to catch that is running
everything.

## Housekeeping every module needs

- `solution.md`'s one-line description of the service (near the top) - add the new system to the list
  of what's built.
- `docs/planning/phase-1-system-classification.md` - do not edit; it's the decision, not the status.
- If you're building the *last* remaining system in a platform (as S162a was here), update
  `README.md`'s Release 1 scope table and `services/README.md`'s per-service system list too - both
  say "N systems" or name what remains outstanding, and both go stale the moment that's no longer true.

## A note on doing several modules in parallel

If you're building more than one sibling module at once (as S161/S162/S162a were here), isolate all
but one in a `git worktree` (the `Agent` tool's `isolation: "worktree"` option) - they all touch the
same shared-kernel files (`SflRole.java`, `SflPermission.java`, `ActorPermissionsController.java`,
`RoleMatrixDocumentTest.java`, `solution.md`) and will corrupt each other's edits in a shared working
tree. Merge them back one at a time with `git merge --no-ff`, resolve the (small, additive) conflicts
in those shared files by hand, then re-wire the union points for every module the merge brought
together and regenerate the role-matrix doc again - each worktree could only wire in the modules it
could see at the moment it branched, so the merged branch always needs one more pass.

**Before running `git add -A` to stage a merge or a module's build output, always exclude
`services/*/logs/gc.log.*` and any large media/build-artifact folder explicitly** (`git restore
--staged <path>` after a broad add, or a pathspec exclude: `git add -A -- . ':!video'
':!services/*/logs/*'`). A broad add during a merge is exactly when a stray 300+ MB folder gets swept
into a commit unnoticed.
