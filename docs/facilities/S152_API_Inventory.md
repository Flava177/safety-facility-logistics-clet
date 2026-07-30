# S152 CAFM / IWMS — API Inventory

- Service: `sfl-facilities-service` — `http://localhost:8091`
- Base path: `/api/v1/facilities`
- OpenAPI: `/v3/api-docs` · Swagger UI: `/swagger-ui.html`
- Requirements: `SRS-SFL-S152-01` … `SRS-SFL-S152-05`

Every operation authorises on **permission and site scope**. The permission column names what the
service actually checks; a denial is audited as `AUTHORIZATION_DENIED` before the 403 is returned.

## Conventions

| Concern | Convention |
|---|---|
| Actor (development) | `X-SFL-User`, `X-SFL-Display-Name`, `X-SFL-Roles`, `X-SFL-Sites` |
| Actor (production) | OIDC/JWT bearer token; `sub`, `name`, `realm_access.roles`, `site_scopes` |
| Correlation | `X-Correlation-ID` honoured if supplied, minted otherwise, **always echoed** on the response |
| Source channel | `X-SFL-Source-Channel`: `WEB` (default), `MOBILE`, `INTEGRATION`, `SCHEDULER`, `SYSTEM` |
| Idempotency | `Idempotency-Key` on state-**creating** POSTs. Same key + same payload replays the original result; same key + different payload is `409 IDEMPOTENCY_KEY_CONFLICT` |
| Optimistic locking | `expectedVersion` in the body of a PATCH. Omit to accept last-write-wins; supply it to get `409 VERSION_CONFLICT` on a stale write |
| Errors | `{status, code, message, correlationId, timestamp, fieldErrors[]}` — see the code table below |
| Paging | `?page=&size=` on search endpoints; response is `{items, totalElements, totalPages, page, size}` |

`C` in the tables below marks an operation that accepts an `Idempotency-Key`.

---

## Sites — `SRS-SFL-S152-01`, NFR 23.3

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/sites` | `FACILITIES_SITE_MANAGE` |
| | `GET` | `/sites` | `FACILITIES_SITE_READ` |
| | `GET` | `/sites/{siteId}` | `FACILITIES_SITE_READ` |
| | `PATCH` | `/sites/{siteId}` | `FACILITIES_SITE_MANAGE` |
| | `PATCH` | `/sites/{siteId}/lifecycle` | `FACILITIES_SITE_MANAGE` |
| | `PATCH` | `/sites/{siteId}/operating-mode` | `FACILITIES_OPERATING_MODE_CHANGE` |

`GET /sites` **filters** to the actor's scopes rather than refusing — asking for "all sites" is a
legitimate request that should answer with the actor's own. Asking for a *specific* site outside scope
is refused, because an empty result would misrepresent the estate.

`PATCH /operating-mode` is the Routine ↔ Examination switch. A no-op change is refused
(`422 OPERATING_MODE_TRANSITION_INVALID`) so the audit trail never records a decision nobody made.

## Buildings and floors — `SRS-SFL-S152-01`

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/buildings` | `FACILITIES_SPACE_MANAGE` |
| | `GET` | `/buildings?siteCode=` | `FACILITIES_SPACE_READ` |
| | `GET` | `/buildings/{buildingId}` | `FACILITIES_SPACE_READ` |
| `C` | `POST` | `/floors` | `FACILITIES_SPACE_MANAGE` |
| | `GET` | `/buildings/{buildingId}/floors` | `FACILITIES_SPACE_READ` |
| | `GET` | `/floors/{floorId}` | `FACILITIES_SPACE_READ` |

## Spaces — `SRS-SFL-S152-01`

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/rooms` | `FACILITIES_SPACE_MANAGE` |
| | `GET` | `/rooms?siteCode=` | `FACILITIES_SPACE_READ` |
| | `GET` | `/rooms/search` | `FACILITIES_SPACE_READ` |
| | `GET` | `/rooms/{roomId}` | `FACILITIES_SPACE_READ` |
| | `PATCH` | `/rooms/{roomId}` | `FACILITIES_SPACE_MANAGE` |
| | `PATCH` | `/rooms/{roomId}/lifecycle` | `FACILITIES_SPACE_MANAGE` |
| | `PATCH` | `/rooms/{roomId}/readiness` | `FACILITIES_READINESS_ASSESS` |

`GET /rooms/search` filters on `siteCode`, `buildingId`, `floorId`, `spaceType`, `readinessStatus`,
`bookable`, `examinationCapable`, `page`, `size`.

`GET /rooms` is kept as a plain list — the existing facilities dashboard page reads that shape, and
adding a default page size to it would break a caller that never asked for one.

A space response carries **derived** `availableForBooking` and `availableForExamination`. Both combine
three fields, and a client that recomputed them subtly wrong would offer a blocked hall for an
examination.

`PATCH /rooms/{roomId}` is refused with `422 READINESS_LOCKED` while the space's examination lock is
engaged. `PATCH /rooms/{roomId}/readiness` is the manual override and is still subject to the
critical-blocker rule — `422 READINESS_BLOCKED`.

## Zones — `SRS-SFL-S152-01`

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/zones` | `FACILITIES_ZONE_MANAGE` |
| | `GET` | `/zones?siteCode=` | `FACILITIES_ZONE_READ` |
| | `GET` | `/zones/{zoneId}` | `FACILITIES_ZONE_READ` |
| | `GET` | `/zones/{zoneId}/members` | `FACILITIES_ZONE_READ` |
| | `POST` | `/zones/{zoneId}/members` | `FACILITIES_ZONE_MANAGE` |
| | `DELETE` | `/zones/{zoneId}/members/{memberType}/{memberId}` | `FACILITIES_ZONE_MANAGE` |

A member must belong to the zone's own site. Without that rule a zone could reach across sites, and an
evacuation broadcast addressed to it would page a building three hundred kilometres from the fire.

## Device references — `SRS-SFL-S152-01`, `-04`

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/device-references` | `FACILITIES_DEVICE_REFERENCE_REGISTER` |
| | `GET` | `/device-references?siteCode=&type=&roomId=` | `FACILITIES_DEVICE_REFERENCE_READ` |
| | `GET` | `/device-references/{deviceId}` | `FACILITIES_DEVICE_REFERENCE_READ` |

## Facility assets — `SRS-SFL-S152-01`, §21.1

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/assets` | `FACILITIES_ASSET_MANAGE` |
| | `GET` | `/assets?siteCode=&roomId=&category=&criticality=&operationalStatus=` | `FACILITIES_ASSET_READ` |
| | `GET` | `/assets/{assetId}` | `FACILITIES_ASSET_READ` |
| | `PATCH` | `/assets/{assetId}` | `FACILITIES_ASSET_MANAGE` |
| | `PATCH` | `/assets/{assetId}/status` | `FACILITIES_ASSET_MANAGE` |
| | `PATCH` | `/assets/{assetId}/location` | `FACILITIES_ASSET_MANAGE` |

`PATCH /assets/{id}/status` **recomputes the readiness of the space the asset sits in**. An impaired
asset raises a blocker at a severity derived from its criticality; a recovered one resolves the blocker
it raised. `PATCH /location` recomputes both the space it left and the space it joined.

## Readiness — `SRS-SFL-S152-01`, `-02`, `-05`, NFR 23.8

| | Method | Path | Permission |
|---|---|---|---|
| `C` | `POST` | `/readiness/checklists` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| | `GET` | `/readiness/checklists?siteCode=` | `FACILITIES_READINESS_READ` |
| | `GET` | `/readiness/checklists/{checklistId}` | `FACILITIES_READINESS_READ` |
| | `PATCH` | `/readiness/checklists/{checklistId}` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| `C` | `POST` | `/readiness/assessments` | `FACILITIES_READINESS_ASSESS` |
| | `GET` | `/readiness/assessments?siteCode=&roomId=&limit=` | `FACILITIES_READINESS_READ` |
| | `GET` | `/readiness/assessments/{assessmentId}` | `FACILITIES_READINESS_READ` |
| | `POST` | `/readiness/blockers` | `FACILITIES_READINESS_ASSESS` |
| | `GET` | `/readiness/blockers?siteCode=&roomId=&severity=&open=&limit=` | `FACILITIES_READINESS_READ` |
| | `PATCH` | `/readiness/blockers/{blockerId}/resolution` | `FACILITIES_READINESS_ASSESS` |
| | `GET` | `/readiness/rooms/{roomId}` | `FACILITIES_READINESS_READ` |
| | `POST` | `/readiness/rooms/{roomId}/lock` | `FACILITIES_READINESS_OVERRIDE` |
| | `DELETE` | `/readiness/rooms/{roomId}/lock` | `FACILITIES_READINESS_OVERRIDE` |

`POST /readiness/assessments` may omit `checklistId`: the service resolves the applicable checklist
from the space's type and its site's operating mode, most-specific-first. An **unanswered item counts
as failed** — a checklist an assessor skipped half of is not a pass.

Submitting an assessment supersedes the previous assessment's checklist blockers, raises one blocker
per failed item at the item's declared severity, and re-derives the space's status in one transaction.

`GET /readiness/rooms/{roomId}` returns the status, the score and every open blocker — the S152-05
"why is this space not ready" drilldown.

## Dashboard — `SRS-SFL-S152-05`

| Method | Path | Permission |
|---|---|---|
| `GET` | `/dashboard?siteCode=` | `FACILITIES_DASHBOARD_READ` |
| `GET` | `/dashboard/blockers?siteCode=` | `FACILITIES_DASHBOARD_DRILLDOWN` |
| `GET` | `/dashboard/unavailable?siteCode=` | `FACILITIES_DASHBOARD_DRILLDOWN` |
| `GET` | `/dashboard/stale?siteCode=` | `FACILITIES_DASHBOARD_DRILLDOWN` |

The summary needs `FACILITIES_DASHBOARD_READ`; every drilldown needs `FACILITIES_DASHBOARD_DRILLDOWN`
too — which is the requirement's *Restricted Drilldown* error state. A manager may see that eleven
spaces are blocked without being entitled to see which.

The dashboard sets `stale` and `staleWarning` when any space's readiness is older than the configured
freshness threshold (`facilities.readiness.staleness-threshold`, or the examination variant when the
site is in examination mode). It is computed live from the source records so the counts always
reconcile.

## Governance — `SRS-SFL-S152-02`, `-03`

| Method | Path | Permission |
|---|---|---|
| `GET` | `/audit?siteCode=&resourceType=&resourceId=&actorId=&action=&from=&to=&limit=` | `FACILITIES_AUDIT_READ` |
| `GET` | `/audit/integrity` | `FACILITIES_AUDIT_INTEGRITY_CHECK` |
| `GET` | `/configuration?siteCode=` | `FACILITIES_CONFIG_READ` |
| `PUT` | `/configuration/{key}` | `FACILITIES_CONFIG_MANAGE` |
| `GET` | `/actor/permissions` | authenticated |

`GET /audit/integrity` replays the whole chain and reports `intact`, `recordsVerified` and, when
broken, the sequence number it broke at with what was expected against what was found. **Running the
check is itself audited** — a report nobody can prove was run is not evidence.

`PUT /configuration/{key}` supersedes rather than overwrites: the previous value is closed with an
effective-to date, so a past escalation can still be reconciled against the threshold that was actually
active when it fired.

`GET /actor/permissions` is what the operations dashboard reads to decide which screens to offer. It
mirrors the fleet and emergency services' equivalents, so one client asks every service the same
question.

## S153 maintenance (pre-existing, unchanged)

| Method | Path |
|---|---|
| `POST` `GET` | `/faults`, `/faults/{id}` |
| `POST` | `/work-orders/from-fault` |
| `GET` | `/work-orders`, `/work-orders/{workOrderId}` |
| `PATCH` | `/work-orders/{workOrderId}/assignment`, `/closure` |

Untouched by this pass except for the nullable `facility_asset_id` and `room_id` columns V6 adds for
S153 to populate.

---

## Error codes

| Code | HTTP | SRS error state |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | field-level validation; `fieldErrors[]` names the offending fields |
| `INVALID_PARENT_REFERENCE` | 400 | the parent named by the request does not exist |
| `MISSING_SITE_SCOPE` | 400 | *"Select a valid CLET site before saving this record."* |
| `UNAUTHORIZED_SCOPE` | 403 | *"You are not authorised to access this site or record."* |
| `NO_SCOPE` | 403 | *"No site scope is assigned to your user profile."* |
| `RESTRICTED_DRILLDOWN` | 403 | *"You do not have permission to view the underlying record."* |
| `RECORD_NOT_FOUND` | 404 | the record does not exist |
| `DUPLICATE_IDENTIFIER` | 409 | *"An active record with this identifier already exists for this site."* |
| `VERSION_CONFLICT` | 409 | the record moved on since the caller read it |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | the key was reused with a different payload |
| `INVALID_STATE_TRANSITION` | 422 | the state machine forbids the move |
| `READINESS_BLOCKED` | 422 | READY refused while a critical blocker is open |
| `READINESS_LOCKED` | 422 | the space is locked for examination use |
| `OPERATING_MODE_TRANSITION_INVALID` | 422 | the site is already in the requested mode |
| `AUDIT_CHAIN_FAILURE` | 500 | *"Audit integrity check failed. Escalate to compliance and security."* |

**422 rather than 400** for the domain-rule refusals is deliberate: the request was well-formed and
understood — it is the estate's current state that forbids it. A client retrying a 400 after fixing its
payload would retry a 422 forever.
