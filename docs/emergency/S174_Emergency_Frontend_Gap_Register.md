# S174 Emergency Mass Notification — Frontend Gap Register

**Status (29 July 2026): ten of twelve gaps closed.**

| # | Gap | Status |
| --- | --- | --- |
| 1 | No pagination on any collection | **Closed** — `EmergencyPageResponse<T>` on activations, all four record registers and drills |
| 2 | Activations filtered on site + status only | **Closed** — mode, priority, incident reference, `openOnly`, `liveOnly`, `afterActionOutstanding`, scenario, template, date range |
| 3 | **No read of the inbound provider feed** | **Closed** — `GET /integrations/inbox`, with processed, rejected and dead-lettered counts and recent envelopes |
| 4 | History written on every transition, never readable | **Closed** — `GET /activations/{id}/history` off `activation_history` |
| 5 | Only templates had a detail endpoint | **Closed** — scenario, audience group and recipient zone detail added |
| 6 | Master data could not be corrected or retired | **Closed** — `PATCH /audience-groups/{id}` and `PATCH /{resource}/{id}/lifecycle` |
| 7 | `cancel`, `escalate`, `reopen`, `withDegradedFallback` have no endpoint | **Open** — see below |
| 8 | No per-recipient delivery or acknowledgement read | **Closed** — `GET /activations/{id}/delivery` |
| 9 | `RecordMetadata` differs between services | **Open by design** — recorded so nobody "fixes" it into a shared type |
| 10 | The dashboards address two origins | **Closed in the previous round** |
| 11 | CSV export truncated silently at 500 | **Closed** — the export drains its pages |
| 12 | Dashboard published totals with no breakdown | **Closed** — `GET /dashboard/breakdown` by status, priority, mode and channel |

### Gap 3 — why it mattered most

The inbound feed is the **only** thing that ever writes `delivered`, `failed` and `acknowledged`. An
activation showing 480 sent and 0 delivered could not be told apart from one whose provider was
posting and being rejected on every callback. Both counts are on the provider integration screen
now, and its empty state says exactly why those figures read zero when no provider has posted.

Payloads are deliberately **not** shown: a callback names recipients, and an integration-health
screen has no business displaying contact detail. Replay stays outbound-only, also deliberately — a
rejected inbound message failed signature or schema validation, so the sending system has to correct
and re-send it.

### Gap 6 — the sharp edge

`AudienceGroup.recipientCount` is what the service fans out to and the denominator every delivery and
acknowledgement percentage is read against, and it could not be corrected through any endpoint. A
group sized at zero sent to nobody and reported a completely successful broadcast. It is correctable
now. The **name** deliberately is not: closed activations cite this group, and renaming it would
rewrite what they say they were sent to.

### Gap 7 — still open, and why

`cancel`, `reopen` and `withDegradedFallback` remain unexposed. Adding them is not the hard part;
deciding what they mean operationally is. A cancelled activation and a rejected one are different
records to an auditor, and `reopen` on a closed activation raises a question about closure evidence
that nobody has answered yet. The register keeps this rather than the endpoints being added on a
guess. `escalate` is reachable through the scheduled SLA sweep and does not need a manual door.

### Gap 9 — open by design

`auditCorrelationId` in fleet-logistics, `correlationId` here, and non-null fields where fleet's are
optional. Recorded so nobody consolidates them into one shared type: re-using the fuel type
type-checked and would have rendered an empty correlation id on every emergency screen.

## Still client-side, and captioned as such

The **templates** and **audiences** register screens still search over loaded records. They read
through `useSiteRecords`, which fetches all four registers for the composer dialogs, so converting
them means giving each register its own paged query separate from the composer's. Their
"Filters the loaded records." captions are still there because they are still true.

---

## The original findings, kept for the evidence

## Gap 1 — No pagination on any collection

**What the service does.** Every list endpoint returns a bare `List<T>`. There is no `page`, no
`size` parameter, no `totalElements` and no `PageResponse` envelope. The limit is fixed inside the
application service and cannot be influenced from the client:

| Endpoint | Limit | Where |
| --- | --- | --- |
| `GET /activations` | 200 | `ActivationService.list` |
| `GET /templates`, `/scenarios`, `/audience-groups`, `/recipient-zones` | service-set | `EmergencyRecordsService` |
| `GET /drills` | service-set | `DrillService.list` |
| `GET /reports/activations.csv` | 500 | `EmergencyDashboardService.activationsReportCsv` |

**Why it matters.** A site that has run more than two hundred activations has a register that
silently stops at two hundred, and there is no parameter that would fetch the rest.

**What the dashboards do.** `useClientWindow` pages over the returned window and reports a total
that is honestly the size of *the window*. When the response comes back at exactly the limit,
`WindowNotice` says so above the table and tells the operator to narrow the filter.

**How to close it.** Exactly as S168 was closed: `FuelPageResponse`, `FuelRepository.FuelPage<T>`
and the `Where`/`Order`/`page` helpers in `JdbcFuelRepository` transfer almost directly. Every
ordering must end in `id` so a page boundary cannot skip or repeat a record.

*Also affects S171 — see gap 1 of the dispatch register. The `useClientWindow` hook and
`WindowNotice` banner are now shared between both modules for this reason.*

---

## Gap 2 — Activations can only be filtered by site and status

**What the service does.** `GET /activations` takes `siteCode` (required) and `status` (optional).
That is all.

**What an operator needs.** Mode (was this break-glass?), priority, incident reference, a date
range, and the four queue views the register offers — open, live now, awaiting approval, after-action
due.

**What the dashboards do.** All of it client-side over the returned window, and **every one of
those controls carries the helper text "Filters the loaded records."** The status filter, which does
reach the service, says "Reaches the service." so the two are never confused.

**The consequence, stated plainly.** With more than two hundred activations at a site, "after-action
due" is the outstanding break-glass sends *in the window*, not at the site. That is the sort of thing
an operator discovers when an audit asks why one was never accounted for, so `WindowNotice` is on
the same screen.

---

## Gap 3 — No read of the inbound provider feed

**What the service does.** Provider callbacks arrive at
`POST /provider-callbacks/{provider}/delivery-status` and `/acknowledgements`, and both pass a
secure inbox — HMAC signature, source allowlist, schema validation and idempotency — before any
domain effect. **None of that is readable.** There is no inbox endpoint, no processed count, no
rejected count and no recent-message list.

**Why this is the most consequential gap in the module.** Fleet, fuel and dispatch all publish an
inbound health read. This service does not, and it is the one service where the inbound feed carries
the numbers that matter: `delivered`, `failed` and `acknowledged` are written *only* by a provider
callback. So when an activation shows 480 sent, 0 delivered and 0 acknowledged, there is no way from
this dashboard to distinguish "no provider is configured" from "the provider is posting and every
message is being rejected for a bad signature".

**What the dashboards do.** The provider integration screen states this outright rather than leaving
a blank panel, and the activation detail screen explains why `delivered` is zero when `sent` is not,
so a clean broadcast is not read as a failed one.

**How to close it.** The same `IntegrationInboxPort` read the fleet-logistics service exposes:
processed count, rejected count and a recent-message list with source, event type, attempt count and
status.

---

## Gap 4 — No transition-history endpoint

**What the service does.** `ActivationService.transition` calls
`repository.saveActivationHistory(activationId, fromStatus, toStatus, action, actor, comment,
occurredAt, correlationId)` on **every** state change. Nothing reads it back. There is no
`GET /activations/{id}/history`.

**What the dashboards do.** The activation detail timeline is reconstructed from the timestamps the
record itself still carries: composition, approval, rejection, the send, after-action approval, the
all-clear and closure. It is captioned as reconstructed, and it says explicitly that a transition
which left no field behind does not appear.

**What is actually lost.** The send has no timestamp of its own — the record keeps `fastLaneMillis`
(the elapsed measure) but not when it happened, so the timeline anchors that entry to the record's
creation and says so. Escalations, partial-delivery transitions and any intermediate state are
invisible.

*Fuel had this gap and it was closed by `FuelApplicationService.history(resourceType, id, actor)`.
The same shape works here.*

---

## Gap 5 — Only templates have a detail endpoint

**What the service does.** `GET /templates/{id}` exists. `GET /scenarios/{id}`,
`/audience-groups/{id}`, `/recipient-zones/{id}` and `/drills/{id}` do not — even though
`DrillService.get(id, actor)` is written and unused.

**What the dashboards do.** Only the template has a route of its own and can be linked to. Scenarios,
audience groups, zones and drills are read out of their site's list, so they cannot be deep-linked,
bookmarked or opened from a correlation id in a log.

---

## Gap 6 — Master data cannot be edited, corrected or retired

**What the service does.** Templates, scenarios, audience groups and recipient zones are
**create-and-read only**. Every one of them carries a `withLifecycle(next, changed)` method on the
domain record and there is no endpoint that calls it. There is no update endpoint either.

**The sharp edge.** `AudienceGroup.recipientCount` is what the service fans out to and the
denominator every delivery and acknowledgement percentage is read against. A group created with the
wrong count — or one that was right two years ago — cannot be corrected. Worse, a group sized at
**zero** sends to nobody, produces a channel record with `targetCount = 0`, and reports a completely
successful broadcast.

**What the dashboards do.** The audiences screen counts zero-sized groups, names them in a warning,
and says the count cannot be corrected through any endpoint. Both record screens state that the
registers are create-and-read, so an obsolete record stays visible and selectable rather than
appearing to have been missed.

---

## Gap 7 — Five domain transitions have no endpoint

`NotificationActivation` implements these and nothing exposes them:

| Transition | Effect | Reachable? |
| --- | --- | --- |
| `cancel(reason)` | `DRAFT`/`PENDING_APPROVAL`/`APPROVED` → `CANCELLED` | No |
| `escalate(reason)` | live → `ESCALATED`, increments `escalationLevel` | Only via the scheduled acknowledgement sweep |
| `reopen(reason)` | `CLOSED` → `REOPENED` | No |
| `withDegradedFallback(path)` | sets `DEGRADED` mode and the fallback path | No |
| `markPartiallyDelivered()` | → `PARTIALLY_DELIVERED` | Only via a provider delivery callback |

`ACTIVATING` and `FAILED` are declared on the status enum and set by nothing at all.

**The practical consequence.** A draft composed in error cannot be cancelled — it stays in the
register forever as an open activation, because `activationOpen()` counts anything not closed,
cancelled or rejected. Submitting it and rejecting it is the only way to dispose of one, which puts
a broadcast in front of an approver purely to throw it away.

**What the dashboards do.** The status filter offers the whole enum, because a stored record can
hold any of these and a filter that could not find one would be worse than useless. Statuses that
this dashboard cannot produce are labelled "(set elsewhere)" in the dropdown, and
`OPERATOR_REACHABLE_STATUSES` in `enums.ts` records which is which.

---

## Gap 8 — No per-recipient delivery or acknowledgement read

**What the service does.** `EmergencyRepository` has `findReceipts(activationId)` and
`findAcknowledgement(activationId, recipientRef)`. Neither is exposed.
`GET /activations/{id}/status` returns per-**channel** counters and one total acknowledgement count.

**What is lost.** When `failedRecipientCount` on the dashboard reads 47, there is no way to find out
*which* 47, on which channel, with what provider reason. `DeliveryReceipt` holds `recipientRef`,
`provider`, `providerMessageId`, `status` and `reason` — everything needed to chase it — and none of
it is readable.

**What the dashboards do.** Show the channel-level counters, which is all there is, and do not
pretend to a drill-down that does not exist.

---

## Gap 9 — `RecordMetadata` is named differently across the two services

Not a defect; a trap worth writing down, because it compiles either way.

| | fleet-logistics services | emergency notification service |
| --- | --- | --- |
| Correlation field | `auditCorrelationId` | `correlationId` |
| `createdBy`, `createdAt`, … | nullable on the wire | non-null |

The emergency module declares its own `RecordMetadata` in `modules/emergency/api/dto.ts` for this
reason. Re-using the fuel type — which was the first thing tried — type-checked and would have
rendered an empty correlation id on every emergency screen.

`SiteCode` **is** identical (`{ value }`) and is re-used.

---

## Gap 10 — The dashboards now address two origins

S174 is a separate service on port 8095. Fleet, fuel and dispatch are three modules of one service
on 8093. This is the first time the dashboards talk to two.

**What changed.**

- `shared/api/config.ts` gained `emergencyApiBaseUrl`, driven by `VITE_EMERGENCY_API_BASE_URL`.
- `shared/api/client.ts` takes a `service: 'fleet' | 'emergency'` per call. Named rather than a raw
  URL, so a module cannot quietly point at something that is not an SFL service. The transport error
  now names the service and its port correctly instead of always saying "Fleet service … 8093".
- **Backend change:** `EmergencyWebConfiguration` did not allow `http://localhost:5005`, the dev
  dashboard origin, so every call from `npm run dev` failed CORS preflight. Added. `8093` — the
  bundled dashboard's origin — was already allowed.

**Deployment note.** `.env.production` cannot leave `VITE_EMERGENCY_API_BASE_URL` empty the way the
fleet one is: the bundle is served by the fleet service, so an empty base is *that* origin. Behind a
gateway fronting both services, set it to the path prefix routed to S174 and it becomes same-origin
like the rest.

---

## Gap 11 — The CSV export truncates silently at 500

`activationsReportCsv` builds the file from `findActivations(sites, null, 500)`. There is nothing in
the file, the headers or the response to say it was cut off.

**What the dashboards do.** Nothing — there is nothing to detect. The export button says the service
exports the site's register rather than the filtered view, which is true, but a site with more than
five hundred activations gets a quietly incomplete compliance export and neither the screen nor the
file can tell.

**This is the one gap here with a compliance consequence rather than an operational one**, and it is
a two-line fix on the service: page the query, or state the truncation in a trailing row.

---

## Gap 12 — The dashboard publishes totals with no breakdown

`GET /dashboard` returns seven counts, `sourceUpdatedAt`, `stale` and `generatedAt`. There is no
split by channel, priority, scenario or mode, and no time series.

**What the dashboards do.** The live/pending activation list under the counts is built from the
activation register and captioned as derived, because the dashboard endpoint returns totals and not
the records behind them. The drill performance chart is bucketed from the drill records the same
way and says so.

---

## Not gaps — recorded so nobody re-investigates them

**Break-glass eligibility is an OR, not an AND.** `BreakGlassPolicy.eligible(templateEligible,
scenarioEligible)` returns `templateEligible || scenarioEligible`. A template marked *not* eligible
still sends when the scenario cited is eligible. Verified live: a break-glass send with an ineligible
template and an eligible scenario returned `201 BREAK_GLASS_ACTIVE`; the same template with no
scenario was refused. Both record screens and the break-glass page state the rule, because reading
"not eligible" beside a template and giving up would be wrong.

**The provider callbacks are deliberately not called from the browser.** Both require an
`X-SFL-Integration-Signature` HMAC over the raw body from a registered shared secret. A browser
cannot hold that secret, and a dashboard that could post delivery facts would be fabricating them.
They belong to the provider. That is why 28 of 30 operations are integrated rather than 30.

**`delivered` and `acknowledged` staying at zero is not a failed broadcast.** `sent` means the
message was handed to the gateway. The other two are only ever written by a provider callback. On a
system with no live provider they stay at zero permanently. The activation detail screen says this
where the zero appears.

**Closure summaries are composed by the service, not supplied.** `ActivationService.close` builds
`deliverySummary` and `acknowledgementSummary` from the channel counters at the moment of closure.
The close dialog previews exactly that arithmetic and offers no field for either, because there is
nothing for an operator to enter.

**Approval is a genuinely separate permission.** `EMERGENCY_ACTIVATION_APPROVE` and
`EMERGENCY_AFTER_ACTION_APPROVE` are held by the command role, compliance officer, security director
and admin — **not** by the emergency coordinator, SOC operator or security officer who compose and
send. Verified live: approving as `EMERGENCY_COORDINATOR` alone returned
`403 EMERGENCY_UNAUTHORIZED_APPROVAL`. The dev actor in `.env` holds all of them so the workflow can
be exercised by one person; in production these are different people, and the screens are built on
that assumption — the approve button simply does not appear for an actor who cannot use it, and a
refusal is rendered with the service's own wording.

**`EMERGENCY_REPORT_EXPORT` is narrower still** — auditor, compliance officer, security director and
admin only. The coordinator cannot export. The export button handles the refusal rather than failing
silently; verified live at `403`.

---

## Where these screens currently live — a placement note, not a gap

S174 is **SFL.SSEMP**. Its screens are currently built into `frontend/sfl-operations-ui`, which is the
**SFL.FTLMP** portal: the fleet service serves it from `/ui/`, and its other three modules are fleet,
fuel and dispatch.

The module itself does not cross the boundary. It addresses `sfl-emergency-notification-service` on its
own port through the client's `service: 'emergency'` routing, reads no fleet data and shares no schema.
What crosses is the **navigation entry**: every user of that bundle currently sees "Emergency
notifications" in the sidebar, including a driver or a head of fleet who has no business in it.

It was built here because the shared component kit, design system and API client live here, and a second
application for one module would have forked the design system — which the module playbook forbids, and
which would have been a worse outcome than a misplaced nav group.

Recorded in [ADR 0005](../adr/0005-programme-scoped-portals-and-navigation-entitlement.md) together with
the two candidate fixes. Both wait on IAM, because there is no authenticated identity to scope a portal
against yet.

---

## Follow-up decision, not a gap

The emergency service still serves its own standalone front end at
`src/main/resources/static/emergency/` (`index.html`, `emergency-console.js`,
`emergency-console.css`), reachable at `http://localhost:8095/emergency`. **Retired on 30 July 2026 by
ADR 0006**: the two asset files are deleted and `index.html` is a notice page that redirects to the
dashboard's emergency screens. It predated these
dashboards and now duplicates them.

It has been left in place — removing a working page is a decision for whoever owns that service, not
a side effect of building this module. Worth deciding on before go-live: two front ends over one
service will drift, and the vanilla-JS one has none of the state guards, none of the permission
awareness and none of the derived-figure captioning built here.

---

## Round 6 — the record registers stopped filtering in the browser

Added 29 July 2026, alongside the S166 and S168 screen work.

The templates, scenarios, audience-group and recipient-zone tables were loading two hundred records per
site through `useSiteRecords` and filtering them in the browser. The search box was captioned "Filters
the loaded records" — true, and useless: it narrowed the first two hundred records the site happened to
return and said nothing about the rest.

No service change was needed. `search`, `lifecycle` and `breakGlassEligible` have been accepted by
`/templates`, `/scenarios`, `/audience-groups` and `/recipient-zones` since those endpoints were
written. The screens simply were not asking.

- All four tables are server-searched, server-filtered and server-paged, with a lifecycle filter beside
  the search box. Confirmed live: searching `muster` on the templates register returned the one template
  whose **body** contains it, with the total, the tab count and the pager all coming from the service's
  own page metadata.
- The break-glass banner counts with two filtered `size=1` reads and takes `totalElements`. It is a
  statement about the site's exposure, so tallying a page would have understated it the moment either
  register ran past one page. Confirmed live: one template and one scenario, matching the eligibility
  chips in the table.
- `useSiteRecords` is still loaded on both screens, for what a page cannot answer: a scenario row names
  a default template that may sit on any page of the template register, the create-scenario dialog needs
  that same list, and the audiences screen's reach total and zero-sized warning are a sum and a roll-call
  that no endpoint aggregates.
- Those two audience figures now say what they cover — "Summed here across up to 200 groups", and
  "Checked across up to 200 groups at this site" on the zero-sized warning. An aggregate for site reach
  would remove the caveat. That is a missing query, not a broken screen.
