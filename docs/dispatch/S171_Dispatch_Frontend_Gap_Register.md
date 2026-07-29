# S171 dispatch — frontend gap register

What the **Courier & Dispatch** dashboards needed from `gh.edu.clet.sfl.fleetlogistics.dispatch` and
did not find.

Every entry was confirmed against the running service on port 8093 — the controllers, the domain
records, `/v3/api-docs` and a live probe — not against a design document.

Nothing here is mocked. Where an endpoint is missing the screen says so in place, and the panel that
would have used it is either absent or clearly labelled.

Companion to `docs/fuel/S168_Fuel_Frontend_Gap_Register.md` and
`docs/fleet/S166_Frontend_Gap_Register.md`, same conventions. Several of these are the **same gaps
S168 had before its backend round**, which is worth noticing: the two modules were built to the same
shape, so the fixes that closed them for fuel transfer almost directly.

---

## Summary

| # | Gap | Blocks | Severity |
| --- | --- | --- | --- |
| 1 | No pagination on any dispatch collection | Registers beyond the size window | High |
| 2 | Exceptions cannot be filtered by manifest, item, severity or assignee | A queue that tells the truth | High |
| 3 | No way to list scan import batches | Returning to a past import | Medium |
| 4 | No per-record transition history | Real audit timelines on detail screens | Medium |
| 5 | Dashboard publishes exceptions only, no volumes | Throughput at a glance | Low |
| 6 | Custody gaps are encoded strings, not structured | Parsing a wire format in the client | Low |
| 7 | No custody or receipt read across manifests | A site-wide custody or receipt view | Low |
| 8 | Manifest item lines carry no item detail | An extra fetch per line, or a bare UUID | Medium |

---

## 1. No pagination on any dispatch collection

**Found.** Every dispatch collection returns a bare `List<T>` with a `size` limit only:

| Endpoint | Paging parameters |
| --- | --- |
| `GET /items` | `size` (default 100, capped 500) — no `page`, no `sort` |
| `GET /inbound` | `size` (default 100) |
| `GET /manifests` | `size` (default 100) |
| `GET /exceptions` | `size` (default 100) |
| `GET /custody`, `/receipts`, `/returns` | none at all — every record for the consignment |

There is no `PageResponse` envelope, no `totalElements` and no stable sort key.

**What the dashboards do instead.** The four registers page **client-side** over the window the
service returned, and the footer counts that window rather than the register. When the returned count
equals the limit, a banner says the window is truncated and the filters should be narrowed. No screen
presents a page count as if it were the whole register.

**To close.** Exactly what closed the same gap for fuel: a `PageResponse<T>` with `page`, `size`,
`sort`, `totalElements`, an allow-listed sort key and an `id` tiebreak so a page boundary cannot skip
or repeat a record. `FuelPageResponse` and the `Where`/`Order`/`page` helpers in `JdbcFuelRepository`
are directly reusable.

---

## 2. The exception queue cannot be filtered by what matters

**Found.** `GET /exceptions` accepts `siteCode`, `type`, `status` and `size`. It does **not** accept
`dispatchId`, `courierItemId`, `severity`, `assignee`, `securityRelevant`, `openOnly` or `dueBefore`.

This is worse here than the equivalent fuel gap was, because of what an open case *does*: it blocks
the manifest it belongs to from closing. A case nobody can find is a consignment nobody can close.

**Consequences in the dashboards, both visible on screen:**

- The queue's four views — open, breaching SLA, security relevant, unassigned — are applied in the
  browser over the returned window, and each control says so. With more open cases than the window
  holds, "breaching SLA" means the breaches *in the window*.
- The manifest detail screen finds the cases against a consignment by fetching the **site's** cases
  and matching `dispatchId` in the client. The closure panel states that, because a case beyond the
  window would not be counted and closure would then be refused by the service for a reason the
  screen did not show.

**To close.** Add `dispatchId`, `courierItemId`, `severity`, `assignee`, `securityRelevant`,
`openOnly` and `dueBefore`. `dueBefore` already exists on the repository for the sweep scheduler, as
it did on the fuel side.

---

## 3. No way to list scan import batches

**Found.** `GET /scans/imports/{id}` and `GET /scans/imports/{id}/rows` both exist — a batch **is**
readable after the fact, unlike the fuel import batches before their fix. What is missing is any way
to *find* one: there is no `GET /scans/imports?siteCode=`.

**What the dashboards do instead.** The scan import screen holds the batches uploaded in the current
browsing session, says so plainly, and offers a field to reopen a batch by identifier. That is a
workaround, and it is only usable by someone who wrote the identifier down.

**To close.** `GET /api/v1/dispatch/scans/imports?siteCode=&sourceSystem=&dispatchId=`, paged.

---

## 4. No per-record transition history

**Found.** No dispatch aggregate exposes a history endpoint. Every state change **is** written to the
audit log through `AuditPort`, and — since the S168 round — the fleet audit search that reads it
works. What is missing is a dispatch-side read authorised against the record, of the shape S168 added
for fuel.

**What the dashboards do instead.** The item and exception detail screens show the record's own
provenance — created by and at, last changed by and at, version, source channel, correlation id —
captioned as provenance rather than as an audit trail. The custody chain is the one place where a
real, ordered history exists, because the handovers *are* that history.

**To close.** `GET /api/v1/dispatch/{items,manifests,exceptions}/{id}/history`, mirroring the fuel
endpoints added in the same shape.

---

## 5. The dashboard publishes exceptions only

**Found.** `GET /dashboard?siteCode=` returns exactly eight counts plus provenance:

```
inTransitCount, openExceptionCount, custodyGapCount, receiptVarianceCount,
outstandingReturnCount, undeliveredCount, overdueReceiptCount, slaBreachCount,
sourceUpdatedAt, stale, generatedAt
```

Every one of them counts something going wrong. That is the right emphasis for a custody system and
the dashboard leads with it — but there is no throughput figure at all: no items registered, no
manifests dispatched, nothing about volume over time.

**What the dashboards do instead.** The eight service counts fill the indicator row. The lists
beneath them — consignments in flight, items needing attention, open cases by type — are counted from
the registers and are captioned as such.

**To close.** Add `itemsRegistered`, `manifestsDispatched` and `manifestsClosed` over a window, and a
time-series endpoint if a trend is wanted. Neither is urgent; the exception emphasis is correct.

---

## 6. Custody gaps are encoded strings

**Found.** `CustodyChainPolicy.detectGaps` returns human-readable strings with structure baked into
them: `BROKEN_SEAL@DISPATCH(BROKEN)`, `COUNT_MISMATCH@TRANSIT(expected=12,verified=11)`,
`OUT_OF_ORDER@COLLECTION`. `CustodyGaps` carries them as `List<String>`.

**What the dashboards do instead.** `parseCustodyGap` in `modules/dispatch/api/workflow.ts` splits
`REASON@HOP(detail)` so the manifest screen can render the reason and the hop as separate things.
Anything that does not match the pattern is shown whole rather than mangled.

**Why it matters a little.** A client parsing a string the server formatted is a contract nobody
declared. Changing the message wording — which reads like a display concern — would silently break
the parse.

**To close.** Return `record Gap(String reason, CustodyHop hop, Map<String,Object> detail)` instead of
a formatted string.

---

## 7. Custody, receipts and returns are readable only per consignment

**Found.** `GET /custody`, `GET /receipts` and `GET /returns` all require a `dispatchId`. There is no
site-wide read of any of them.

**Consequence.** There can be no "handovers recorded today" view, no "receipts awaiting
confirmation" queue across consignments, and no way to see one custodian's activity. The dashboards
do not offer any of those, rather than faking them from a manifest-by-manifest sweep.

**Not a defect** — each of the three genuinely belongs to one consignment, which is why they are tabs
on the manifest screen rather than registers. Recorded because the absence is a deliberate shape, not
an oversight, and the next reader should not go looking.

**To close, if wanted.** `GET /custody?siteCode=&from=&to=&custodian=` and the equivalent for
receipts.

---

## 8. Manifest item lines carry no item detail

**Found.** `DispatchManifestItem` carries `courierItemId` and nothing about the item — no number, no
destination, no sensitivity. Rendering a readable manifest line means either an extra
`GET /items/{id}` per line, or showing a bare UUID.

**What the dashboards do instead.** The manifest items tab shows the shortened identifier with the
expected seal beneath it, and every row links through to the item. It does not fan out a fetch per
line: a manifest of forty items would be forty requests, and the register above it already carries
what an operator needs to recognise an item.

**To close.** Either denormalise `itemNumber`, `destination` and `sensitivity` onto the manifest item
row, or add `GET /manifests/{id}/items?expand=item`.

---

## What is not a gap

Worth recording, so nobody re-investigates:

- **Receipt outcome and return outcome are derived, not settable.** `ReceiptVariancePolicy` and
  `ReturnReconciliationPolicy` decide them from the counts, seal and recipient. The dialogs preview
  the derivation and never send an outcome.
- **`chainOfCustodyRequired` is derived** by `CourierItem` from type and sensitivity. The register
  dialog previews it; it is not a field.
- **Receipt confirmation is idempotent** on `captureCorrelationId`, which is what makes an offline
  capture at the destination safe to replay. The dialog exposes it and explains why.
- **Scanner ingest is idempotent** on its own signature, so there is deliberately no inbound replay —
  only outbound dead letters can be replayed.
