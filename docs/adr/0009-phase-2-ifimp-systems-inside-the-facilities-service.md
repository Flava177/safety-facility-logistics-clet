# ADR 0009 - The Phase 2 IFIMP systems live inside the facilities service, with RLS from their first migration

- Status: **Accepted and implemented**, 28 September 2026.
- Date: 2026-09-28
- Deciders: SFL platform / Building & Infrastructure Unit
- Relates: [0004 S174 as a separate service](0004-s174-emergency-notification-as-separate-service.md)
  (and its consolidation amendment); [0007 row-level security](0007-row-level-security-deferred-with-a-named-mechanism.md);
  SRS CLET/DTI/CL9/SFL/SRS/2026/002 §1.7 (CORR-04, -06, -07, -08, -09), §2.6, §5.2, §6.1

## Context

The Phase 2 SRS adds six systems to SFL.IFIMP - S156 BMS/IoT, S157 Energy & Sustainability, S158
Space Planning, S169 Cleaning, S173 Event Logistics, S176 Construction - all owned by the Building &
Infrastructure Unit. They were built together, so the questions below had to be answered once, before
six parallel builds each answered them differently.

## Decisions

### 1. One deployable, one schema: `sfl-facilities-service`, `facilities`

The six systems are modules of the existing service, in the existing schema, exactly as S153 and S159
were added to S152. Phase 1 consolidated to three deployables - one per owning unit - on 5 August 2026
(ADR 0004 amendment) for reasons that are unchanged: S217/S217a/S214a are still not available
(SRS OI-03) and the operating cost is per deployable and per database. All six systems belong to the
unit that owns this service.

The module boundary is the package and the table set, not a schema, which is the convention every
facilities module already follows. **This departs from the wording of the brief that commissioned the
work**, which asked for RLS "from each new schema's first migration": there are no new schemas. The
intent - no Phase 2 table ever exists without its policy - is met per table instead (decision 2), and
that is the stronger guarantee: a schema-level promise says nothing about the fifth table added to it.

### 2. RLS is applied by the migration that creates the table

V14 applied the site-scope policies with a catalogue loop - once. A table created later carried a
`site_code`, had no policy, and would have been readable across sites by `sfl_app` until somebody
noticed: ADR 0007's deferral again, one migration later.

V15 turns the loop into `facilities.apply_site_scope_policies()`, and every Phase 2 migration ends with
`SELECT facilities.apply_site_scope_policies();`, so the policy exists from the transaction that
created the table (SRS CORR-06, NFR-SEC3). It also grants sequences, which V14 did only for the
sequences that existed on the day it ran. `Phase2RowLevelSecurityCoverageTest` asks the catalogue for
every site-scoped table without row security or the policy, and fails the build naming it.

### 3. Work with no request on the thread is scoped by the thread

The RLS scope supplier returned an empty set whenever there was no HTTP request, and the policies read
empty as "no rows". Every scheduled sweep, the outbox drainer and the broker listener run with no
request. Under the owner connection - every environment to date - that is invisible; under `sfl_app`,
the ADR 0007 production target, S153 escalation would escalate nothing, the drainer would publish
nothing and the vehicle-service handler's insert would be refused. Phase 2 adds a dozen more sweeps.

Scheduler and listener threads are now created by a factory that marks them as platform threads, and
the supplier scopes a platform thread to `*`. The mark is on the thread, not the job, because the
listener is `@Transactional` and the scope must exist before the transaction begins. A request thread
is never marked. The one explicit use - recording a *rejected* vendor message, whose claimed site is
exactly what cannot be trusted - is `PlatformThreads.callAsPlatform`.

### 4. One authenticated vendor inbox for the service

S156 telemetry, S157 meter readings and the S078 event hand-off are three inbound vendor feeds. They go
through one `VendorMessageVerifier` (source allowlist, channel, timestamp window, HMAC-SHA256 over the
raw body, site, schema, idempotency) rather than three copies. A rejection is recorded on the inbox,
the hash chain (S204), the SIEM port (S208) and the outbox in a `REQUIRES_NEW` transaction before the
refusal is thrown - otherwise the refusal would roll back its own evidence, which is the one record an
investigation needs (NFR-SEC2). Every rejection answers the sender identically.

### 5. Modules meet through published contracts in the provider's package

S157 consumes S156's stream; S173 draws on S169's capacity; S158 hands work to S176 and S176 confirms
S158's scenario at handover. Each contract is an interface in the *provider's* `application/contract`
package, consumed through the consumer's own port and adapter. S156 and S176 therefore never depend on
S157 or S158 (ArchUnit holds this), and a provider's internals can change without its consumers
noticing. Shared S152/S153/S159 hooks the six systems need - an automated work-order intake, a booking
lifecycle observer and cleaning requirement, a read-only utilisation reader, a space-allocation
register - were added to those modules once, rather than six systems reaching into them.

### 6. Honest about what is not there

- **Vendors.** No vendor has passed the SRS §5.2 gate. `GET /api/v1/facilities/vendor-integrations`
  reports `SIMULATED_ADAPTER_ONLY` until an evidence reference is configured, and has no "integrated"
  value to set (CORR-07). See `docs/integration/procurement-gate/`.
- **Cross-service consumers.** S156's critical-fault fast-lane signal and S176's contractor access
  suspension are events for SSEMP, which has no inbound consumer. They are published, audited and
  forwarded to the SIEM port, and **not delivered** until SSEMP subscribes.
- **Unbuilt dependencies** (S164, S165, full S168, S078, S133, S172, S225) are held behind ports and,
  where their state matters to a gate, local projections that fail closed: a higher-risk event cannot
  be confirmed and a permit-requiring project cannot start until the SSEMP systems publish.

## Consequences

- Six new modules, migrations V16-V21, one deployable to operate; runbooks under `docs/runbooks/s1xx-*.md`.
- A Phase 2 migration that forgets the RLS call fails the build rather than a security review.
- Moving any one system to its own deployable later is a package and table-set extraction, the same
  shape as ADR 0004's reversibility argument: no cross-module foreign keys between Phase 2 modules, and
  cross-module calls go through ports.
