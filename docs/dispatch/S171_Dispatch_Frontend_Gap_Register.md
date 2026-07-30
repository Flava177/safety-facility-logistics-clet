# S171 Courier and Dispatch — Frontend Gap Register

**Status (29 July 2026): all eight gaps closed.** The backend round that closed them is the same
one that closed S168's, because gaps 1, 2 and 4 were literally the same three gaps — `FuelPageResponse`,
the `Where`/`Order`/`page` helpers and the history read ported across rather than being reinvented.

| # | Gap | Closed by |
| --- | --- | --- |
| 1 | No pagination on any dispatch collection | `DispatchPageResponse<T>` on items, manifests, exceptions and scan batches, with `Paging(page,size,sort)`, a per-resource sort allow-list and an `id` tiebreak. `useClientWindow` and `WindowNotice` **deleted**. |
| 2 | Exceptions could not be filtered by what matters | `severity`, `assignee`, `unassigned`, `securityRelevant`, `openOnly`, `dueBefore`, `dispatchId`, `courierItemId` all reach the service. Every "Filters the loaded records." caption removed from that screen, and the four queue counts are now their own site-wide queries. |
| 3 | No way to list scan import batches | `GET /scans/imports`, paged and filterable by source system, dispatch and status. |
| 4 | No per-record transition history | `GET /items/{id}/history`, `/manifests/{id}/history`, `/exceptions/{id}/history`, read off the audit log and authorised against the record. |
| 5 | Dashboard published exceptions only | `itemsRegistered30d`, `manifestsDispatched30d`, `manifestsClosed30d`, `itemsDelivered30d`. A rolling 30-day window — the shortest span that survives a weekend without reading as a collapse. |
| 6 | Custody gaps were encoded strings | `CustodyChainPolicy.Gap(reason, hop, handoverId, detail)`. `parseCustodyGap` and its regular expression are **deleted**. |
| 7 | Custody and receipts readable per consignment only | `GET /custody?siteCode=` and `GET /receipts?siteCode=`, paged, with custodian matched on either side of a handover. |
| 8 | Manifest lines carried no item detail | `GET /manifests/{id}/items?expand=item` resolves every line's item in **one** query, not a fetch per line. |

One thing was fixed that was not on the list: `itemType` was the last client-side filter on the item
register, so it was added to the service rather than left as the only unlabelled one. Both CSV
exports also drain their pages now — they stopped silently at 500, the same defect recorded as
emergency gap 11.

## What is not a gap

Worth recording, so nobody re-investigates:

- **Receipt outcome and return outcome are derived, not settable.** `ReceiptVariancePolicy` and
  `ReturnReconciliationPolicy` decide them from the counts, seal and recipient. The dialogs preview
  the derivation and never send an outcome.
- **`chainOfCustodyRequired` is derived** by `CourierItem` from type and sensitivity.
- **Receipt confirmation is idempotent** on `captureCorrelationId`, which is what makes an offline
  capture at the destination safe to replay.
- **Scanner ingest is idempotent** on its own signature, so there is deliberately no inbound replay —
  only outbound dead letters can be replayed.

## Verification

Every endpoint above was driven against the running service with the exact headers the client sends:
paging, page boundaries, a bad sort key falling back rather than reaching SQL, size clamping at 200,
each filter individually, and the structured custody gaps. 402 backend tests pass, 1 skipped
(`FleetPostgresEndToEndTest` is Testcontainers-only and cannot reach the Docker pipe from Git Bash).
The exception queue was walked in a browser.
