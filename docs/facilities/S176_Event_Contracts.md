# S176 Construction Project Management - event contracts

All names follow the single catalogue convention (SRS CORR-04):
`^sfl\.[a-z0-9]+\.[a-z0-9-]+\.v\d+$`. Payloads carry references and classifications, never free text a
person typed (a variation's justification, a defect's description, a contractor's name) and never
secrets.

## Published

Every event below is written to the outbox in the same transaction as the state change it reports and
carries `siteCode`, `correlationId` and `causationId` (the actor) through the standard envelope.

### `sfl.ifimp.project-registered.v1`

Aggregate: `ConstructionProject`. Published on registration, whether direct or via the S158 intake.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "status": "REGISTERED", "origin": "DIRECT",
  "workTypes": ["FIT_OUT"], "budgetBaseline": "500000.00", "currency": "GHS",
  "fundingSourceReference": "FUND-CAPEX-2026-014", "spaceChangeRequestId": null, "committedScenarioId": null
}
```

For a PROPOSED project from S158, `status` is `PROPOSED`, `budgetBaseline`/`currency` are absent, and
`spaceChangeRequestId` is set.

### `sfl.ifimp.project-approved.v1`

Aggregate: `ConstructionProject`. Published when the accountable sign-off is recorded.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "approvalId": "uuid", "approverId": "director.1",
  "baselineRevision": 1, "baselineAmount": "500000.00", "currency": "GHS"
}
```

### `sfl.ifimp.project-started.v1`

Aggregate: `ConstructionProject`. Published when the S176-01 gate is passed and works begin.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "workTypes": ["HOT_WORK"],
  "approvalId": "uuid", "startedAt": "2026-10-01T08:00:00Z"
}
```

### `sfl.ifimp.project-variation-approved.v1`

Aggregate: `VariationOrder`. Published on both an ordinary and an escalated approval; `escalated`
distinguishes them.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "variationId": "uuid",
  "variationReference": "VO-MAIN-00001", "costDelta": "20000.00", "currency": "GHS", "escalated": false,
  "currentBudget": "520000.00", "cumulativePercent": "4.0000"
}
```

### `sfl.ifimp.project-variation-escalation-required.v1`

Aggregate: `VariationOrder`. Published when a variation is held for escalated approval, at submission
or at a decision attempt that discovers it now crosses the threshold.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "variationId": "uuid",
  "variationReference": "VO-MAIN-00002", "costDelta": "60000.00", "currency": "GHS",
  "cumulativePercentAfter": "12.0000", "thresholdPercent": 10, "blockedByPending": false
}
```

### `sfl.ifimp.contractor-site-access-requested.v1`

Aggregate: `SiteAccessGrant`. Published when a compliant request is recorded. Not consumed by any
S160a/S160 system in this workspace - see the runbook and gap report.

```json
{
  "grantId": "uuid", "contractorId": "uuid", "contractorCode": "ACME-01", "vendorReference": "V-1001",
  "projectId": "uuid", "accessScope": "Site gate 2, working hours", "validFrom": "2026-10-01T00:00:00Z",
  "validTo": "2026-11-01T00:00:00Z", "enforcement": "RECORDED_NOT_ENFORCED"
}
```

### `sfl.ifimp.contractor-site-access-suspended.v1`

Aggregate: `SiteAccessGrant`. Published by the compliance sweep or by an insurance/competency update
that lapses immediately. Also forwarded to the SIEM (recorded, not delivered - S208 has no adapter).

```json
{
  "grantId": "uuid", "contractorId": "uuid", "contractorCode": "ACME-01", "vendorReference": "V-1001",
  "projectId": "uuid", "reasonCode": "CONTRACTOR_COMPLIANCE_LAPSED", "suspendedAt": "2026-10-15T08:00:00Z",
  "enforcement": "RECORDED_NOT_ENFORCED"
}
```

### `sfl.ifimp.project-handed-over.v1`

Aggregate: `ConstructionProject`. Published on a complete handover (never on a flagged-incomplete one).

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "handoverId": "uuid",
  "handoverDate": "2026-12-05", "roomIds": ["uuid"], "committedScenarioId": null,
  "scenarioConfirmation": "NOT_APPLICABLE", "defectsLiabilityEndsOn": "2027-12-05"
}
```

### `sfl.ifimp.project-defect-raised.v1`

Aggregate: `DefectItem`. Published when a defects-liability item raises its S153 work order.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "defectId": "uuid",
  "defectReference": "DL-MAIN-00001", "contractorId": "uuid", "contractorCode": "ACME-01",
  "priority": "MEDIUM", "workOrderId": "uuid", "workOrderNumber": "WO-MAIN-000123",
  "category": "CONSTRUCTION_DEFECT"
}
```

### `sfl.ifimp.project-closed.v1`

Aggregate: `ConstructionProject`. Published when every defects-liability item is closed or deferred and
the project closes.

```json
{
  "projectId": "uuid", "projectReference": "CP-MAIN-00001", "closedAt": "2027-12-10T09:00:00Z",
  "defectsClosed": 3, "defectsDeferred": 1
}
```

## Consumed

### `sfl.ssemp.permit-issued.v1` / `-suspended.v1` / `-extended.v1` / `-closed.v1`

Reserved names for S164 (Permit-to-Work), consumed by `PermitEventHandler`
(`gh.edu.clet.sfl.facilities.construction.infrastructure.integration.PermitEventHandler`) via the
`IntegrationEventHandler` contract; the inbound queue's `ssemp.#` binding already routes them.

**S164 is not built anywhere in this workspace, so nothing publishes these events.** Every S176
project whose work type requires a permit is therefore refused the start - correctly, fail-closed
(SRS-SFL-S176-01) - until S164 ships and a real publisher exists. See the runbook for how to tell that
state apart from a broken consumer.

Expected payload, one flattened object per event (`occurredAt` is required on every kind; `permitId`
falls back to the envelope's `aggregateId` if absent; `siteCode` falls back to the envelope header):

| Field | Required | Issued | Suspended | Extended | Closed |
|---|---|---|---|---|---|
| `permitId` | always | | | | |
| `siteCode` | always | | | | |
| `occurredAt` | always | | | | |
| `workType` | issued only | required | | | |
| `validFrom` | issued only | required | | | |
| `validTo` | issued/extended | required | | required | |
| `permitReference` | optional | | | | |
| `contractorReference` | optional | | | | |
| `originReference` | optional | | | | |

A payload missing a field required for its kind fails S176's own schema validation and is logged at
ERROR and dropped - not applied, not retried (retrying will not make the field appear, and a permit
with no validity window cannot be treated as current on a guess). This is S176's stated expectation of
the S164 payload; S164's own build is authoritative for what it actually publishes.

**Ordering.** A notice older than the last one applied to the same permit (`occurredAt` before the
stored `lastEventAt`) is ignored - a redelivered `issued` cannot resurrect a permit S164 has since
suspended. `CLOSED` is terminal: nothing reopens a closed permit in the projection, matching S164's own
rule that resumption is a fresh issue, not an un-suspend.

### S152, S153, S158 (in-process / same-service contracts, not broker events)

Not events - documented here because they are the other three cross-module edges this system has:

- **S152** (`gh.edu.clet.sfl.facilities.masterdata`) - handover calls
  `FacilitiesMasterDataService.createRoom`/`updateRoom` directly (same deployable), through
  `EstateRegisterPort` / `S152EstateRegisterAdapter`. S152 raises its own `sfl.ifimp.room-created.v1` /
  `room-updated.v1` on the same change; S176 does not duplicate them.
- **S153** (`gh.edu.clet.sfl.facilities.maintenance`) - defects raise work orders through
  `AutomatedWorkOrderIntake` (same deployable), via `DefectWorkOrderPort` / `S153DefectWorkOrderAdapter`.
  S153 audits and events the work order itself; S176 additionally publishes
  `project-defect-raised.v1` above, which S153's own events do not carry (project/contractor tagging).
- **S158** (`gh.edu.clet.sfl.facilities.spaceplanning`) - two directions, both interfaces only (S158 is
  built in a separate worktree):
  - **Inbound**: S176 provides `ConstructionProjectIntake` (`construction.application.contract`),
    called by S158 when an approved space-change request needs physical works.
  - **Outbound**: S176 consumes `ScenarioHandover` (`spaceplanning.application.contract`) at handover,
    via `ScenarioConfirmationPort` / `S158ScenarioConfirmationAdapter`.
