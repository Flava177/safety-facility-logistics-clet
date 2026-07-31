# S153 CMMS — API reference

**37 paths** under `/api/v1/facilities`, on `sfl-facilities-service` (port 8091). Every response is
the platform envelope `{data, error}`; every error carries an SRS-worded code and a correlation ID.

Actor headers (`X-SFL-User`, `X-SFL-Roles`, `X-SFL-Sites`, `X-SFL-Source-Channel`) are resolved by
`FacilitiesActorResolver` while `sfl.security.enabled=false`; in production the same `ActorContext`
comes from the OIDC principal and the headers are ignored.

`Idempotency-Key` is honoured on the **four state-creating POSTs** marked below and nowhere else. A
PATCH transition is guarded by the record's version and its state machine, so a repeat is either a
no-op or an invalid-transition error.

`expectedVersion` on a PATCH body is optional. Supplying it turns a lost update into
`VERSION_CONFLICT`; omitting it accepts last-write-wins.

---

## Faults — `/api/v1/facilities/faults`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `POST` | `/faults` | `FACILITIES_FAULT_REPORT` | **Idempotent.** Needs a `roomId` or a `locationCode` |
| `GET` | `/faults` | `FACILITIES_FAULT_READ` | `siteCode`, `roomId`, `status`, `openOnly`, `limit`. A requester sees only their own |
| `GET` | `/faults/{faultId}` | `FACILITIES_FAULT_READ` | |
| `PATCH` | `/faults/{faultId}/triage` | `FACILITIES_FAULT_TRIAGE` | Confirms priority and computes the SLA. The only place priority changes |
| `PATCH` | `/faults/{faultId}/dismissal` | `FACILITIES_FAULT_TRIAGE` | `REJECTED`, `DUPLICATE` or `CANCELLED`. Reason required |
| `PATCH` | `/faults/{faultId}/lifecycle` | `FACILITIES_FAULT_TRIAGE` | Record lifecycle, not workflow status |
| `GET` | `/faults/rooms/{roomId}` | `FACILITIES_FAULT_READ` | Open faults on one space, for the S152 space screen |

## Work orders — `/api/v1/facilities/work-orders`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `POST` | `/work-orders/from-fault` | `FACILITIES_WORK_ORDER_CREATE` | **Idempotent.** SLA from priority, mode and vendor contract |
| `GET` | `/work-orders` | `FACILITIES_WORK_ORDER_READ` | `siteCode`, `roomId`, `assetId`, `status`, `assignedTo`, `vendorId`, `openOnly`, `limit` |
| `GET` | `/work-orders/{id}` | `FACILITIES_WORK_ORDER_READ` | |
| `PATCH` | `/work-orders/{id}/assignment` | `FACILITIES_WORK_ORDER_ASSIGN` | Assign or reassign. Releases a hold |
| `PATCH` | `/work-orders/{id}/start` | `FACILITIES_WORK_ORDER_UPDATE` | |
| `PATCH` | `/work-orders/{id}/hold` | `FACILITIES_WORK_ORDER_UPDATE` | Reason required. Does not stop the SLA clock |
| `PATCH` | `/work-orders/{id}/completion` | `FACILITIES_WORK_ORDER_UPDATE` | The assignee says it is done |
| `PATCH` | `/work-orders/{id}/reopen` | `FACILITIES_WORK_ORDER_CLOSE` | Reverses a completion, so it takes the closing permission |
| `PATCH` | `/work-orders/{id}/closure` | `FACILITIES_WORK_ORDER_CLOSE` | Refused without a reason or the required evidence |
| `PATCH` | `/work-orders/{id}/cancellation` | `FACILITIES_WORK_ORDER_CANCEL` | Reason required |
| `GET` | `/work-orders/{id}/parts` | `FACILITIES_WORK_ORDER_READ` | |
| `POST` | `/work-orders/{id}/parts` | `FACILITIES_WORK_ORDER_UPDATE` | |
| `DELETE` | `/work-orders/{id}/parts/{partId}` | `FACILITIES_WORK_ORDER_UPDATE` | |
| `GET` | `/work-orders/{id}/evidence` | `FACILITIES_EVIDENCE_READ` | |
| `POST` | `/work-orders/{id}/evidence` | `FACILITIES_EVIDENCE_ATTACH` | **Idempotent.** By reference; `contentHash` is a hex SHA-256 |

A vendor technician sees and touches **only work orders assigned to them**, on every one of these.

## Evidence — `/api/v1/facilities/maintenance-evidence`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `GET` | `/maintenance-evidence/{id}` | `FACILITIES_EVIDENCE_READ` | Metadata only |
| `POST` | `/maintenance-evidence/{id}/exports` | `FACILITIES_EVIDENCE_EXPORT` | Reason and recipient required; audited |
| `PATCH` | `/maintenance-evidence/{id}/legal-hold` | `FACILITIES_EVIDENCE_EXPORT` | Suspends disposal, keeps the class |

## Planning — `/api/v1/facilities/maintenance`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `POST` | `/maintenance/vendors` | `FACILITIES_VENDOR_MANAGE` | **Idempotent** |
| `GET` | `/maintenance/vendors` | `FACILITIES_VENDOR_READ` | |
| `GET` | `/maintenance/vendors/{id}` | `FACILITIES_VENDOR_READ` | Carries `assignable` and `unassignableReason` |
| `PATCH` | `/maintenance/vendors/{id}` | `FACILITIES_VENDOR_MANAGE` | |
| `PATCH` | `/maintenance/vendors/{id}/lifecycle` | `FACILITIES_VENDOR_MANAGE` | |
| `POST` | `/maintenance/schedules` | `FACILITIES_PM_SCHEDULE_MANAGE` | **Idempotent.** Lead time must be shorter than the interval |
| `GET` | `/maintenance/schedules` | `FACILITIES_PM_SCHEDULE_READ` | `siteCode`, `assetId` |
| `GET` | `/maintenance/schedules/{id}` | `FACILITIES_PM_SCHEDULE_READ` | Carries `generateOn` and `dueForGeneration` |
| `PATCH` | `/maintenance/schedules/{id}` | `FACILITIES_PM_SCHEDULE_MANAGE` | |
| `PATCH` | `/maintenance/schedules/{id}/lifecycle` | `FACILITIES_PM_SCHEDULE_MANAGE` | |
| `POST` | `/maintenance/schedules/runs` | `FACILITIES_PM_SCHEDULE_MANAGE` | Generation on demand. Idempotent by cycle |
| `POST` | `/maintenance/escalations/runs` | *(sweep)* | Escalation on demand. Idempotent |

## Error codes

| Code | Status | When |
| --- | --- | --- |
| `CLOSURE_EVIDENCE_MISSING` | 422 | Closing with fewer evidence items than required. Names the counts |
| `UNAUTHORIZED_APPROVAL` | 403 | Reopening without the closing permission |
| `RETENTION_CLASS_MISSING` | 400 | Evidence saved without a retention class |
| `EXPORT_NOT_APPROVED` | 403 | Export without a recorded reason or a named recipient |
| `INVALID_STATE_TRANSITION` | 409 | A move the state machine does not have |
| `VERSION_CONFLICT` | 409 | `expectedVersion` is behind the stored version |
| `UNAUTHORIZED_SCOPE` | 403 | Wrong site, or a vendor/requester reaching past their own records |
| `DUPLICATE_IDENTIFIER` | 409 | A second active vendor or schedule code at one site |
| `RECORD_NOT_FOUND` | 404 | |
| `INVALID_PARENT_REFERENCE` | 400 | Unknown room, asset or vendor |
| `VALIDATION_FAILED` | 400 | Domain validation, including an expired vendor contract |

## Events

Published through `ServiceOutbox`; nothing here delivers to a person.

```
ifimp.facility-fault.reported | .triaged | .dismissed | .resolved | .escalated
ifimp.work-order.created | .assigned | .start | .hold | .complete | .reopen | .closed
                         | .cancelled | .escalated
ifimp.maintenance-evidence.attached | .exported
ifimp.maintenance-vendor.registered
ifimp.preventive-schedule.created
ifimp.facility-asset.serviced          # closing a preventive order
ifimp.readiness-blocker.created | .resolved   # a fault blocking or clearing a space
```

## Scheduled jobs

| Job | Default | Property |
| --- | --- | --- |
| Escalation sweep | every 15 min | `sfl.maintenance.escalation.interval-ms` |
| Preventive generation | hourly | `sfl.maintenance.preventive.interval-ms` |
| Both off | — | `sfl.maintenance.scheduling.enabled=false` |
