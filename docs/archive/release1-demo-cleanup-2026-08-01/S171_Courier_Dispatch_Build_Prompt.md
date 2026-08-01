# Build Prompt — CLET Cluster 9 SFL Phase 1: S171 Mailroom / Courier & Dispatch Tracking

> Paste everything below the line into the build agent. It is written to match the house style of the
> S166 Fleet and S168 Fuel prompts and is grounded in the SRS (pages 62–65, SRS-SFL-S171-01…06),
> the FTLMP/PLAT framing, the event catalog, and CT-05 "Secure Dispatch" from the architecture doc.
> Branch from `main` **after** S166 + S168 have merged to `main` (S171 depends on S166).

---

You are implementing CLET Cluster 9 SFL Phase 1 — S171 Mailroom / Courier & Dispatch Tracking.

This is a complete production-quality implementation task, not a prototype or partial vertical slice.
Complete every SRS requirement and deliver the backend, operational UI, integrations, audit/evidence,
dashboards, tests, Swagger documentation and operational documentation.

Do not commit or push until the implementation has been reviewed by the user.

======================================================================
1. AUTHORITATIVE REFERENCES AND PRECEDENCE
======================================================================
Use these references in the following order:

1. MAIN REQUIREMENTS REFERENCE
   REQUIREMENT DOC/CLET_Cluster9_SFL_Phase1_SRS_v1.0.pdf — the S171 section (pages 62–65).
   Implement all six requirements:
   - SRS-SFL-S171-01 — Mailroom/Courier Register and Item Tracking
   - SRS-SFL-S171-02 — Dispatch Manifest and Unbroken Chain-of-Custody
   - SRS-SFL-S171-03 — Destination Receipt Confirmation and Variance Handling
   - SRS-SFL-S171-04 — Optional Scanner/Carrier Integration and Immutable Custody Evidence
   - SRS-SFL-S171-05 — Inbound Mail Registration and Internal Distribution
   - SRS-SFL-S171-06 — Return-Leg / Reverse-Logistics Reconciliation

2. IMPLEMENTATION AND ARCHITECTURE REFERENCES
   - REQUIREMENT DOC/CLET_Cluster9_FSL_System_Architecture_Document_FULLY_INTEGRATED.docx
     (contains CT-01…CT-10, including CT-05 "Secure Dispatch")
   - docs/architecture/microservices-realignment.md
   - docs/phase-1-system-classification.md
   - docs/integration/event-catalog.md

3. EXISTING IMPLEMENTATION PATTERNS (follow these exactly — S171 is a sibling of S166/S168 in the
   same deployable service)
   - services/sfl-fleet-logistics-service (S166 Fleet is the reference implementation)
   - services/sfl-fleet-logistics-service/.../fuel (S168 shows the extraction-ready feature layout,
     the workflow/anomaly/evidence/integration reuse, the E2E suite and the operational console)
   - services/sfl-service-common
   - The S166/S168 tests, architecture, audit, evidence, workflow, integration, dashboard, OpenAPI
     and UI conventions

S171 belongs INSIDE the existing deployable services/sfl-fleet-logistics-service boundary. Do not
create another deployable service. The human-facing system name must be
“Mailroom / Courier & Dispatch Tracking”. Do not confuse it with S168 Fuel or S166 Fleet.

Keep existing technical artifact, schema and package names:
- Artifact: sfl-fleet-logistics-service
- Database/schema ownership: fleet_logistics
- Existing base package: gh.edu.clet.sfl.fleetlogistics — add a cohesive new feature package
  `gh.edu.clet.sfl.fleetlogistics.dispatch`
- Application port: 8093
- PostgreSQL host port: 5443
- Database: sfl__fleet_vehicle_service
- Canonical platform token for events: `ftlmp` (e.g. `sfl.ftlmp.dispatch-created.v1`)

Delivery type for S171 is **Build (Fast-Track)**: SFL owns the courier register, manifest,
chain-of-custody and reconciliation. Carrier APIs and barcode/label scanners are **optional additive
integrations** — the full chain-of-custody, receipt confirmation and return reconciliation MUST work
with no scanner and no carrier connected. GPS/telematics and RFID are Phase-2; provide the integration
seams only (provider-neutral ports + recorded/simulator adapters), do not build the full systems.

======================================================================
2. MANDATORY PRE-IMPLEMENTATION GAP ANALYSIS
======================================================================
Before changing code:

1. Read the complete S171 SRS requirements: user stories, requirements, system-managed fields,
   validation, workflows, error states and acceptance criteria (pages 62–65).
2. Inspect the current S166/S168 implementation and identify what can safely be reused: site-scoped
   authorization; actor context; vehicle/driver/trip references; the workflow & SLA engine; the
   tamper-evident audit hash chain; evidence references and export governance; runtime configuration;
   the secure integration inbox; the transactional outbox + drainer + dead-letter/replay; the
   notification port; dashboard snapshots; the SFL error envelope; the OpenAPI configuration; the
   operational console shell.
3. Create these documents before implementation:
   - docs/dispatch/S171_Gap_And_Conflict_Report.md
   - docs/dispatch/S171_Requirement_Traceability_Matrix.md
   - docs/dispatch/S171_Domain_And_State_Model.md
   - docs/dispatch/S171_API_Inventory.md
   - docs/dispatch/S171_Migration_Plan.md
   - docs/dispatch/S171_Test_Plan.md
   - docs/dispatch/S171_Event_Contracts.md
4. Record every ambiguity and resolve it explicitly. Do not silently invent permanent business rules.
   Every SLA/threshold in S171 is runtime-configurable per the SRS (e.g. the outstanding-return
   escalation window, undelivered-item window) — implement them as versioned runtime configuration
   with documented defaults, not hard-coded constants.
5. Confirm the relationship to S166: a sensitive dispatch is carried by an S166 trip (vehicle, driver,
   movement history). Reference S166 vehicle/driver/trip only through an explicit application
   port/contract — never write to S166 persistence directly.

======================================================================
3. REQUIRED BUSINESS CAPABILITIES
======================================================================
Implement the complete S171 scope.

A. Mailroom / courier item register (S171-01, S171-05)
- Inbound and outbound items: confidential correspondence, certificates, sealed materials,
  examination papers, sealed bags, examination devices, ordinary mail.
- Fields: origin, destination, item type, sensitivity classification, sender, recipient, assigned
  handler, current status, site scope, source channel, correlation ID.
- Confidential items are flagged for chain-of-custody handling.
- Inbound registration + internal distribution with a recorded acknowledgement (signature or scan)
  that closes the item; undelivered/unclaimed items flagged and escalated after a configurable window;
  misrouted items re-routed with a recorded reason.
- Item status lifecycle (use these exact states):
  RECEIVED → STAGED → DISPATCHED → IN_TRANSIT → DELIVERED → RETURNED, with EXCEPTION as a controlled
  branch and closure only on a clean outcome. Every transition records actor + time and is auditable.

B. Dispatch manifest & unbroken chain-of-custody (S171-02)
- A dispatch/manifest lists items, seal IDs, item counts, route, assigned handler, and the carrying
  S166 trip reference where applicable.
- Chain-of-custody handover chain (model each hop explicitly):
  WAREHOUSE_STAGING → DISPATCH → TRANSIT → CENTRE_RECEIPT → HALL_DEPLOYMENT → COLLECTION → RETURN.
  Each handover records transferring custodian, receiving custodian, time and seal state.
- A gap or mismatch (missing handover, broken seal, count mismatch) raises an exception and BLOCKS
  closure until resolved.
- Signed dispatch/receipt/scan records preserved as immutable, hashed evidence (reuse the governed
  evidence + audit-hash-chain foundations).

C. Destination receipt confirmation & variance handling (S171-03)
- Confirm receipt by verifying seal integrity, item count and recipient signature against the manifest.
- Any variance (broken seal, wrong/short count, wrong recipient) opens an exception case and escalates
  to logistics and security (SSEMP), preserving evidence.
- A clean receipt closes the dispatch and completes the chain-of-custody.
- Receipt confirmation must be edge-resilient: usable during WAN loss and reconciled on restore
  (support an idempotent, offline-capture-then-reconcile path with client-supplied capture time +
  correlation ID; do not lose or double-apply a receipt on replay).

D. Return-leg / reverse-logistics reconciliation (S171-06)
- Manage the return leg: collection at the centre, transit, receipt at HQ/warehouse under continued
  chain-of-custody.
- Reconcile returned items against the original dispatch manifest. Shortfalls, extras or broken seals
  raise an exception and BLOCK custody closure until resolved.
- Outstanding (not-yet-returned) items tracked and escalated after a configurable window.

E. Exception / case workflow
- Exception types (at minimum): UNREGISTERED_ITEM, CUSTODY_GAP, RECEIPT_VARIANCE, SCAN_MISMATCH,
  UNDELIVERED_ITEM, RETURN_DISCREPANCY.
- Each exception creates an accountable workflow case with: number, type, severity, status, related
  item/dispatch/handover/trip, assignee, SLA due date, explanation, evidence, decision, escalation,
  closure reason, timestamps and correlation ID.
- Reuse the S166/S168 workflow lifecycle: DETECTED → ASSIGNED → UNDER_REVIEW → (AWAITING_EXPLANATION →
  EXPLANATION_RECEIVED) → APPROVED/REJECTED/ESCALATED → CLOSED, plus HOLD/RESUME, REASSIGN, CANCEL and
  authorised REOPEN. A case cannot close without the required explanation, decision, closure reason and
  evidence. Custody/dispatch closure is blocked while an open exception exists.
- Security-relevant variances (seal/tamper) surface to SSEMP through a secure outbound port/event. Do
  not write to a security database directly.

F. Optional scanner/carrier integration (S171-04)
- Provider-neutral ports for barcode/label scanner events and courier-carrier status updates; recorded
  simulator adapters; documented CSV/file import contract for scan batches. Vendor DTOs stay in
  adapters, never in the domain.
- A scanned item that does not match the manifest entry is flagged (SCAN_MISMATCH) and routed to
  variance handling. Where connected, scan/carrier events attach to the custody record.

G. Scheduled processing
- Configurable, multi-execution-safe scheduled jobs for: undelivered-item detection, outstanding-return
  escalation, exception SLA escalation, dashboard snapshot refresh and stale-integration detection.
  Jobs must not duplicate exception cases, notifications, audit events or outbox messages.

======================================================================
4. DOMAIN AND CODE STRUCTURE
======================================================================
Create an extraction-ready feature under the existing service, following the repository’s Clean
Architecture conventions (mirror services/.../fuel):

gh.edu.clet.sfl.fleetlogistics.dispatch
  api
  application  ( command, query, port, service, workflow )
  domain       ( model, policy, exception )
  infrastructure ( persistence, integration, audit, reporting, scheduling )

- No business rules in controllers, JPA entities, repository adapters, configuration classes,
  JavaScript or SQL triggers. Keep domain code independent of Spring, JPA, RabbitMQ, vendor SDKs, HTTP.
- Optimistic locking on mutable operational records. Explicit state-transition policies; no
  unrestricted status-update endpoints.
- Reuse the existing audit, evidence, workflow, notification and integration foundations. Extend them;
  do not create parallel duplicates. Add an ArchUnit test mirroring the fuel one (domain is
  framework-free; domain must not depend on api/infrastructure).

======================================================================
5. DATABASE AND MIGRATIONS
======================================================================
Use Flyway migrations beginning at the next free version after the fuel migrations (V16+). Do not edit
previously applied S166/S168 migrations. Schema is fleet_logistics; no cross-schema foreign keys.

Expected S171 persistence (subject to gap analysis):
- courier_items (register)
- dispatches / dispatch_manifests
- dispatch_manifest_items (item counts, seal IDs)
- custody_handovers (the chain-of-custody hops)
- dispatch_receipts (seal/count/signature confirmation, incl. edge-captured)
- return_reconciliations
- dispatch_exception_cases (+ rule/variance results)
- scan_import_batches / rows (optional scanner ingestion)
- required dashboard/read-model tables

Every operational table: UUID/ULID id; site scope; created/modified actor + timestamp (TIMESTAMPTZ,
UTC); source channel; correlation ID; lifecycle/status; optimistic-lock version where mutable. Add
unique/check constraints, reference and status indexes, site/date indexes, idempotency constraints
(edge-receipt + scan + provider dedup), and queue indexes. Migrations must apply cleanly on a fresh DB
and on a DB already carrying S166 + S168 migrations.

======================================================================
6. SECURITY AND AUTHORIZATION
======================================================================
Extend the additive role/permission model with explicit dispatch permissions, e.g.:
- DISPATCH_ITEM_READ, DISPATCH_ITEM_REGISTER, DISPATCH_ITEM_MANAGE
- DISPATCH_MANIFEST_READ, DISPATCH_MANIFEST_CREATE
- DISPATCH_CUSTODY_RECORD (record handovers)
- DISPATCH_RECEIPT_CONFIRM
- DISPATCH_RETURN_RECONCILE
- DISPATCH_INBOUND_REGISTER, DISPATCH_INBOUND_DISTRIBUTE
- DISPATCH_EXCEPTION_READ, DISPATCH_EXCEPTION_MANAGE, DISPATCH_EXCEPTION_APPROVE, DISPATCH_EXCEPTION_ESCALATE
- DISPATCH_REPORT_READ, DISPATCH_REPORT_EXPORT
- DISPATCH_INTEGRATION_INGEST, DISPATCH_INTEGRATION_REPLAY

Map permissions deliberately to the SRS roles: DISPATCH_CONTROLLER / LOGISTICS_COORDINATOR (owner of
register, manifest, custody, return), CENTRE_MANAGER (receipt confirmation), MAILROOM_OFFICER (inbound
registration/distribution), plus AUDITOR, COMPLIANCE_OFFICER, SSEMP/security escalation target,
DTI_ADMIN, INTEGRATION_ENGINEER, SERVICE_INTEGRATION. Add any missing SflRole values additively in
sfl-service-common and map them in a DispatchPermissionMatrix (mirror FuelPermissionMatrix). Preserve
site-scoped authorization on every list/detail/update/workflow/dashboard/evidence/export operation.
Must work with the dev-header actor mechanism and be compatible with SFL_SECURITY_ENABLED=false
locally; never weaken production security to make Swagger or the UI work.

======================================================================
7. API AND OPENAPI
======================================================================
Expose versioned REST APIs under /api/v1/dispatch, e.g.:
/api/v1/dispatch/items
/api/v1/dispatch/manifests
/api/v1/dispatch/custody          (record handovers)
/api/v1/dispatch/receipts         (destination receipt confirmation, edge-capable)
/api/v1/dispatch/returns          (return reconciliation)
/api/v1/dispatch/inbound          (inbound mail + distribution/acknowledgement)
/api/v1/dispatch/exceptions
/api/v1/dispatch/scans            (optional scan ingestion)
/api/v1/dispatch/dashboard
/api/v1/dispatch/reports
/api/v1/dispatch/integrations

Provide create/detail/filtered-list; controlled updates; explicit workflow-transition endpoints (no
generic PATCH-status); pagination + stable sorting; site/date/status/sensitivity/handler/trip filters;
idempotency for state-changing requests (especially edge receipts and scan imports); correlation IDs;
optimistic-lock conflict handling; and the established SFL error envelope. Add OpenAPI tags: Dispatch
Items, Dispatch Manifests, Chain of Custody, Dispatch Receipts, Return Reconciliation, Inbound Mail,
Dispatch Exceptions, Dispatch Integrations, Dispatch Dashboards and Reports. Document DTO fields, enums,
validation, dev headers, error responses and example requests. Swagger must remain available at
http://localhost:8093/swagger-ui.html and /v3/api-docs.

======================================================================
8. INTEGRATIONS AND EVENTS
======================================================================
Implement ports and adapters for: S166 vehicle/driver/trip references; optional barcode/scanner and
carrier-API ingestion; SSEMP/security visibility for tamper/seal variances; evidence/object-storage
references; notifications; reporting; and Phase-2 GPS/telematics + RFID seams (provider-neutral ports +
recorded adapters only). Inbound provider/scan messages MUST reuse the secure integration inbox: HMAC
or mTLS verification abstraction, source allowlisting, schema validation, idempotency, inbox
persistence before domain processing, and retry/dead-letter visibility.

Follow the canonical event name rule `sfl.{platform}.{event-name}.v{version}` with platform `ftlmp`.
Existing catalog events to reuse:
- sfl.ftlmp.dispatch-created.v1
- sfl.ftlmp.dispatch-received.v1
Add only justified lifecycle events needed for full S171 traceability, for example:
- dispatch-manifest-created; dispatch-dispatched; custody-handover-recorded; custody-gap-detected;
  dispatch-receipt-confirmed; dispatch-receipt-variance; dispatch-return-reconciled;
  dispatch-return-discrepancy; dispatch-exception-assigned/approved/rejected/escalated;
  inbound-item-registered; inbound-item-undelivered.
Document every event and update docs/integration/event-catalog.md. State changes and outbox records
must commit atomically; reuse the outbox drainer, dead-letter and privileged replay pattern.

======================================================================
9. EVIDENCE, AUDIT AND REPORTING
======================================================================
Every state-changing action writes an append-only audit entry (actor, timestamp, source channel,
correlation ID, before/after, reason, affected record, site, previous hash + record hash) — reuse the
tamper-evident chain. Dispatch/receipt/scan/custody evidence must be governed evidence references
(hash, uploader/source, retention class, related workflow, access history, legal hold, export
approval) — never ungoverned binaries in domain columns. The chain-of-custody record itself must be
reconstructable for audit.

Dashboards for: in-transit items; items by status/sensitivity; open exceptions by type/severity;
custody gaps; receipt variances; outstanding (not-yet-returned) items; undelivered inbound items;
on-time vs late receipts; return reconciliation status; top exception routes/handlers/centres;
integration health (inbox + outbox dead-letters); SLA breaches; data freshness. Filters: site, date
range, sensitivity, handler, centre, status, severity, trip, operating mode. CSV export where
authorised. Dashboard totals must reconcile to source records and show stale-data warnings.

======================================================================
10. OPERATIONAL UI
======================================================================
Implement a responsive Mailroom / Courier & Dispatch Tracking console that calls the real APIs, at
http://localhost:8093/dispatch/ , following the existing Fleet/Fuel visual language and console
structure. Provide at minimum: dispatch overview/dashboard; item register + inbound registration;
manifest builder (items, seal IDs, counts, route, trip link); chain-of-custody handover view/record;
destination receipt confirmation (seal/count/signature, with an offline-capable path); return
reconciliation screen; exception queue + investigation/detail with the full manager action set;
optional scan-import/batch-results screen; integration-health view (inbox + outbox with replay);
reports/export. Every screen renders loading/empty/success/validation/server-error states, obeys
role/site scope (hide/disable unauthorised actions), preserves partially entered custody/receipt data
where practical, is accessible (labels, keyboard nav, contrast), surfaces correlation IDs, links
dashboard exceptions to their source records, and never computes authoritative custody/variance
decisions only in JavaScript.

======================================================================
11. TESTING AND VERIFICATION
======================================================================
Implement domain unit tests; application-service tests; policy and state-transition tests;
authorization and site-scope tests; controller/API tests; persistence/integration tests; Flyway
migration tests; ArchUnit tests; event-contract tests; idempotency tests (edge receipt + scan +
provider dedup); audit-chain tests; scheduled-job tests; Testcontainers PostgreSQL tests; and E2E
critical scenarios.

Mandatory E2E scenarios:
1. Register an item and track it through received → staged → dispatched → in transit → delivered.
2. Reject a dispatch of an unregistered item (Unregistered Item).
3. Build a manifest with seal IDs/counts and record a full custody handover chain.
4. Detect a custody gap (missing handover / broken seal / count mismatch) and block closure.
5. Confirm a clean destination receipt (seal + count + signature) and complete chain-of-custody.
6. Open and escalate a receipt variance to logistics and security (SSEMP).
7. Capture a receipt offline (WAN loss) and reconcile idempotently on restore (no double-apply).
8. Reconcile a matched return leg and close custody.
9. Raise a return discrepancy (shortfall/extra/broken seal) and block custody closure.
10. Register inbound mail and distribute with a recorded acknowledgement.
11. Flag and escalate an undelivered inbound item after its configurable window.
12. Escalate an outstanding (not-returned) item after its configurable window.
13. Flag a scan mismatch from optional scanner ingestion and route it to variance handling.
14. Reject unsigned/schema-invalid integration input via the secure inbox.
15. Retry and surface a failed outbound integration (outbox dead-letter + privileged replay).
16. Prevent exception closure without explanation, decision and evidence.
17. Verify audit-chain integrity and detect tampering.
18. Verify dashboard counts against source records.
19. Run the complete CT-05 "Secure Dispatch" flow: generate an examination dispatch manifest, seal,
    assign an S166 vehicle/trip, dispatch, receive at centre and reconcile all items with no
    unexplained variance (GPS/RFID optional/mocked in Phase 1).

Run at minimum: `mvn -pl sfl-fleet-logistics-service -am test`. Also verify the app starts on 8093,
PostgreSQL connects on localhost:5443, /actuator/health = UP, /v3/api-docs returns OpenAPI JSON,
Swagger UI loads, /dispatch/ loads, and existing S166 + S168 tests still pass.

======================================================================
12. DOCUMENTATION AND FINAL REPORT
======================================================================
Update services/sfl-fleet-logistics-service/README.md, docs/integration/event-catalog.md, the
Swagger/OpenAPI metadata, and the local Docker/PostgreSQL + IntelliJ run instructions. Create
docs/dispatch/S171_Operations_And_Verification_Guide.md and
docs/dispatch/S171_Final_Implementation_Report.md. The final report must contain: requirement-by-
requirement status; implemented API inventory; domain/state model; database migrations; security
matrix; integrations and events; UI deliverables; tests and exact results; Docker/PostgreSQL setup;
Swagger and UI URLs; known limitations; deferred Phase-2 (GPS/RFID/carrier) work; evidence that S166 +
S168 regression tests remain green; and explicit remaining gaps with owners.

======================================================================
13. DELIVERY SEQUENCE
======================================================================
Deliver in professional reviewable slices:
1. Gap analysis, traceability, domain/state model and migration plan.
2. Item register + inbound registration/distribution domain.
3. Dispatch manifest + chain-of-custody handovers.
4. Destination receipt confirmation (incl. edge/offline path) + variance handling.
5. Return-leg reconciliation.
6. Exception workflow, SLA, notifications and scheduled sweeps.
7. Evidence and tamper-evident audit.
8. Secure scanner/carrier + SSEMP integrations and events.
9. Dashboards, reports and exports.
10. Swagger/API documentation.
11. Operational UI.
12. Integration, architecture, Testcontainers and E2E verification.
13. Operations guide and final implementation report.
Complete all slices; do not stop after the first feature. Do not commit or push until the user reviews.
When approval to commit is given: small, professional feature-oriented commits, authored and committed
only as `Flava177 <33349874+Flava177@users.noreply.github.com>`; no AI/agent/co-author attribution in
source, docs, commits or PR text. Open a PR targeting the branch the team is integrating Phase-1 work
into (branch S171 from `main` once S166+S168 are on `main`).

======================================================================
14. DEFINITION OF DONE
======================================================================
S171 is complete only when: all six SRS requirements are traced to code and tests; the item register
and inbound distribution work with recorded acknowledgement; manifests carry seal IDs/counts and an
unbroken chain-of-custody; custody gaps, receipt variances, scan mismatches and return discrepancies
block closure and route to accountable SLA-controlled exception workflow; destination receipts confirm
seal/count/signature and work offline with idempotent reconciliation; the return leg reconciles against
the original manifest and outstanding items escalate; security-relevant variances reach SSEMP through a
secure outbound port/event; evidence and chain-of-custody are governed, immutable and reconstructable;
role and site authorization is enforced; optional scanner/carrier integrations are authenticated and
observable and the system works without them; dashboards reconcile to source; Swagger and the
operational UI work; scheduled sweeps are safe and tested; PostgreSQL/Testcontainers/E2E (incl. CT-05)
pass; existing S166 + S168 behaviour remains intact; operations docs and the final report are complete;
and no unresolved critical or high-severity gap remains. Do not declare the module complete based only
on compilation or controller tests.
