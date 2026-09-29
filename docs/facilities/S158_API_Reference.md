# S158 Space Planning & Move Management - API Reference

Base path: `/api/v1/facilities/space-planning`. Every endpoint requires an authenticated actor with a
site scope covering `siteCode` (or the record's site); a request naming a site outside the actor's
scope is refused `UNAUTHORIZED_SCOPE` (403); an actor with no site scope at all is refused `NO_SCOPE`
(403). OpenAPI: `GET /v3/api-docs`, tag `S158 Space Planning`.

## Scenarios - `SpaceScenarioController` - SRS-SFL-S158-01

| Method & path | Permission | Purpose |
|---|---|---|
| `POST /scenarios` | `FACILITIES_SPACE_PLAN_MANAGE` | Create a draft scenario (a new plan, version 1) |
| `POST /scenarios/{id}/revisions` | `FACILITIES_SPACE_PLAN_MANAGE` | Revise a plan as the next draft version, copying its allocations |
| `PATCH /scenarios/{id}` | `FACILITIES_SPACE_PLAN_MANAGE` | Rename or redescribe a draft |
| `PUT /scenarios/{id}/rooms/{roomId}` | `FACILITIES_SPACE_PLAN_MANAGE` | Set (or, empty, vacate) a room's allocation in the scenario; returns its computed compliance |
| `DELETE /scenarios/{id}/rooms/{roomId}` | `FACILITIES_SPACE_PLAN_MANAGE` | Remove a room from the scenario |
| `POST /scenarios/{id}/commit` | `FACILITIES_SPACE_PLAN_COMMIT` | Commit - the explicit, named, audited act. `LIKE_FOR_LIKE` applies to S152 in this call; `PHYSICAL_WORKS` proposes an S176 project and waits |
| `POST /scenarios/{id}/discard` | `FACILITIES_SPACE_PLAN_MANAGE` | Discard a draft, with a reason |
| `POST /scenarios/{id}/apply` | `FACILITIES_SPACE_PLAN_COMMIT` | Apply a committed scenario's allocations to the S152 register (refused `SPACE_SCENARIO_UNCOMMITTED` on a draft) |
| `GET /scenarios?siteCode=&status=` | `FACILITIES_SPACE_PLAN_READ` | Search scenarios |
| `GET /scenarios/{id}` | `FACILITIES_SPACE_PLAN_READ` | Read one scenario |
| `GET /scenarios/{id}/lines` | `FACILITIES_SPACE_PLAN_READ` | The scenario's allocation lines |
| `GET /scenarios/{id}/compliance` | `FACILITIES_SPACE_PLAN_READ` | Per-room compliance, computed now against the active standards |
| `GET /scenarios/compare?scenarioIds=&scenarioIds=` | `FACILITIES_SPACE_PLAN_READ` | Compare one to ten scenarios room by room, against the current register and each other |

## The S152 allocation register - `SpaceAllocationRegisterController`

| Method & path | Permission | Purpose |
|---|---|---|
| `GET /allocations?siteCode=&scenarioId=` | `FACILITIES_SPACE_PLAN_READ` | The current register, or (with `scenarioId`) what one committed scenario put there. A draft `scenarioId` is refused `SPACE_SCENARIO_UNCOMMITTED` |

## Occupancy standards and overrides - `OccupancyStandardController` - SRS-SFL-S158-02

| Method & path | Permission | Purpose |
|---|---|---|
| `POST /standards` | `FACILITIES_OCCUPANCY_STANDARD_MANAGE` | Define the next version of a space type's standard (supersedes the active one) |
| `GET /standards?siteCode=` | `FACILITIES_SPACE_PLAN_READ` | Every standard version at a site |
| `POST /scenarios/{id}/overrides` | `FACILITIES_SPACE_PLAN_MANAGE` | Request an override for a non-compliant room (first step; carries the reason) |
| `POST /overrides/{id}/approve` | `FACILITIES_OCCUPANCY_OVERRIDE_APPROVE` | Approve (second step; refused `SPACE_OVERRIDE_INCOMPLETE` if the approver is the requester or lacks the permission) |
| `GET /scenarios/{id}/overrides` | `FACILITIES_SPACE_PLAN_READ` | Overrides recorded against a scenario |

## Utilisation - `UtilisationController` - SRS-SFL-S158-03

| Method & path | Permission | Purpose |
|---|---|---|
| `POST /utilisation/reconcile` | `FACILITIES_SPACE_PLAN_MANAGE` | Run reconciliation for a site now (normally scheduled - see the runbook) |
| `GET /utilisation/signals?siteCode=&activeOnly=` | `FACILITIES_SPACE_PLAN_READ` | Planning signals (`activeOnly` defaults `true`) |
| `GET /utilisation/snapshots?siteCode=` | `FACILITIES_SPACE_PLAN_READ` | The latest utilisation snapshot for every room at the site |

## Space-change requests - `SpaceChangeRequestController` - SRS-SFL-S158-04

| Method & path | Permission | Purpose |
|---|---|---|
| `POST /requests` | `FACILITIES_SPACE_CHANGE_REQUEST` | Submit a request |
| `PATCH /requests/{id}/decision` | `FACILITIES_SPACE_CHANGE_DECIDE` | Approve or decline (refused if the decider is the submitter) |
| `POST /requests/{id}/link-scenario` | `FACILITIES_SPACE_CHANGE_DECIDE` or `FACILITIES_SPACE_PLAN_MANAGE` | Link an approved request to a like-for-like scenario |
| `POST /requests/{id}/hand-to-construction` | `FACILITIES_SPACE_CHANGE_DECIDE` or `FACILITIES_SPACE_PLAN_MANAGE` | Hand an approved request needing physical works to S176; links the resulting project back |
| `POST /requests/{id}/resolve` | `FACILITIES_SPACE_CHANGE_DECIDE` or `FACILITIES_SPACE_PLAN_MANAGE` | Resolve (refused `SPACE_CHANGE_UNLINKED_RESOLUTION` with no linked outcome) |
| `GET /requests?siteCode=&status=` | narrowed per record | Search the pipeline - a requester with neither `FACILITIES_SPACE_PLAN_READ` nor `_DECIDE` sees only their own submissions |
| `GET /requests/{id}` | narrowed per record | Read one request |

## Dashboard - `SpacePlanningDashboardController`

| Method & path | Permission | Purpose |
|---|---|---|
| `GET /dashboard?siteCode=` | `FACILITIES_SPACE_PLAN_READ` | Current-versus-planned utilisation, draft comparison, request pipeline, compliance by unit, active signal counts |

## Error codes this module adds

Beyond the S158 block already seeded in `FacilitiesErrorCode` (`SPACE_SCENARIO_UNCOMMITTED`,
`SPACE_STANDARD_NOT_DEFINED`, `SPACE_OVERRIDE_INCOMPLETE`, `SPACE_CHANGE_UNLINKED_RESOLUTION`), this
build appended two at the end of the S158 block for the SRS-SFL-S158-03 "standard
integration-unavailable handling" and the S158-01/-04 hand-off:

| Code | HTTP (unmapped - see gap report) | Meaning |
|---|---|---|
| `SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE` | 400 (should be 503) | S176's intake could not be reached; nothing was committed or linked |
| `SPACE_UTILISATION_SOURCE_UNAVAILABLE` | 400 (should be 503) | S159 could not be read; existing snapshots and signals are unchanged |
