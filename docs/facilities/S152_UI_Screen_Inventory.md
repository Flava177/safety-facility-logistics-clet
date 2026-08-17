# S152 CAFM/IWMS - dashboard screen inventory

What was built, where it lives, what it calls and who may see it. The permissions column is the
gate on the **navigation item**; every screen is additionally gated by S152 system entitlement
(`SystemRoutes system="S152"`), and every request is authorised again by the service, which is the
only enforcement point that counts.

- Module: `frontend/sfl-operations-ui/src/modules/facilities`
- Route base: `/facilities/estate` for the registers, `/facilities` for the dashboard
- Served at `/home` by every service that carries the bundle; the facilities service is
  `http://localhost:8091/home/facilities`
- Service: `sfl-facilities-service` on 8091, `VITE_FACILITIES_API_BASE_URL`
- Programme: SFL.IFIMP · System: S152

## Screens

**Corrected 17 August 2026.** The Writes column below used to list `POST /sites`, `POST /rooms`,
`POST /assets`, `POST /zones`, `POST /device-references` and `PUT /configuration/{key}` as though the
screens called them. None of them did. Every register was a read-only list, no screen in the module
had an edit at all, and the sixteen write functions in `facilitiesApi.ts` had no call site. The
column now records what the source does, and the writes it claimed have been built rather than
merely re-documented.

| # | Screen | Route | File | Reads | Writes | Permission |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Facilities dashboard | `/facilities` | `pages/FacilitiesDashboardPage.tsx` | `GET /dashboard` | - | `FACILITIES_DASHBOARD_READ` |
| 2 | Sites | `/facilities/estate/sites` | `pages/SiteRegisterPage.tsx` | `GET /sites` | `POST /sites`, `PATCH /sites/{id}`, `PATCH /sites/{id}/lifecycle` | `FACILITIES_SITE_READ` |
| 3 | Site detail | `/facilities/estate/sites/:siteId` | `pages/SiteDetailPage.tsx` | `GET /sites/{id}`, `GET /buildings` | `PATCH /sites/{id}`, `PATCH /sites/{id}/lifecycle`, `PATCH /sites/{id}/operating-mode`, `POST /buildings` | `FACILITIES_SITE_READ` |
| 4 | Spaces | `/facilities/estate/spaces` | `pages/SpaceRegisterPage.tsx` | `GET /rooms/search` | `POST /rooms`, `PATCH /rooms/{id}`, `PATCH /rooms/{id}/lifecycle` | `FACILITIES_SPACE_READ` |
| 5 | Space detail | `/facilities/spaces/:roomId` | `pages/SpaceDetailPage.tsx` | `GET /rooms/{id}`, `GET /readiness/rooms/{id}`, `GET /assets`, `GET /readiness/assessments` | `PATCH /rooms/{id}/readiness`, `PATCH /readiness/blockers/{id}/resolution`, `POST /rooms/{id}/readiness/lock` and `/unlock` | `FACILITIES_SPACE_READ` |
| 6 | Facility assets | `/facilities/estate/assets` | `pages/AssetRegisterPage.tsx` | `GET /assets` | `POST /assets`, `PATCH /assets/{id}`, `/location`, `/lifecycle` | `FACILITIES_ASSET_READ` |
| 7 | Asset detail | `/facilities/estate/assets/:assetId` | `pages/AssetDetailPage.tsx` | `GET /assets/{id}` | `PATCH /assets/{id}`, `/status`, `/location` | `FACILITIES_ASSET_READ` |
| 8 | Zones | `/facilities/estate/zones` | `pages/ZonesPage.tsx` | `GET /zones`, `GET /zones/{id}/members`, plus the space, building and device registers to name each member | `POST /zones`, `PATCH /zones/{id}/lifecycle`, `POST`/`DELETE` members | `FACILITIES_ZONE_READ` |
| 9 | Device references | `/facilities/estate/devices` | `pages/DeviceReferencesPage.tsx` | `GET /device-references` | `POST /device-references`, `PATCH /device-references/{id}`, `/lifecycle` | `FACILITIES_DEVICE_REFERENCE_READ` |
| 10 | Readiness assessments | `/facilities/assessments` | `pages/ReadinessAssessmentsPage.tsx` | `GET /readiness/assessments` | `POST /readiness/assessments` | `FACILITIES_READINESS_READ` |
| 11 | Assessment detail | `/facilities/assessments/:assessmentId` | `pages/ReadinessAssessmentDetailPage.tsx` | `GET /readiness/assessments/{id}` | - | `FACILITIES_READINESS_READ` |
| 12 | Readiness checklists | `/facilities/estate/checklists` | `pages/ReadinessChecklistsPage.tsx` | `GET /readiness/checklists` | `POST /readiness/checklists`, `PATCH /readiness/checklists/{id}` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| 13 | Checklist detail | `/facilities/estate/checklists/:checklistId` | `pages/ReadinessChecklistDetailPage.tsx` | `GET /readiness/checklists/{id}` | `PATCH /readiness/checklists/{id}` - **not** `PUT`, which this row claimed and the service has never had | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| 14 | Audit & integrity | `/facilities/estate/audit` | `pages/FacilitiesAuditPage.tsx` | `GET /audit`, `GET /audit/integrity` | - | `FACILITIES_AUDIT_READ` |
| 15 | Configuration | `/facilities/estate/configuration` | `pages/FacilitiesConfigurationPage.tsx` | `GET /configuration` | `PUT /configuration/{key}` | `FACILITIES_CONFIG_READ`, and `FACILITIES_CONFIG_MANAGE` to change one |

## Dialogs

| Dialog | Used by | What it guards |
| --- | --- | --- |
| `SubmitAssessmentDialog` | 10 | Every item must be answered - an unanswered item counts as failed, so the dialog says so and disables submit rather than letting a partial answer become a result. |
| `SetReadinessDialog` | 5 | The manual override. READY is disabled with the open critical count before it is submitted; the service refuses it independently. |
| `ResolveBlockerDialog` | 5 | The resolution note is required, because a blocker cleared without one cannot be told from a dismissal at a post-mortem. |
| `AssetStatusDialog` | 7 | Previews the readiness consequence - criticality plus target status - before the change is committed. Mirrors `ReadinessApplicationService.severityFor`. |
| `OperatingModeDialog` | 3 | Declaring or standing down examination mode. Withheld from `FACILITIES_MANAGER` by the matrix; a centre manager, command role or facilities director carries it. |
| `siteDialogs` | 2, 3 | Add and edit. The site code is shown and not offered - `UpdateSite` has no field for it, and every other record refers to the site by it. |
| `spaceDialogs` | 4, 5 | Add and edit. Creating walks site → building → floor, because `CreateRoom` takes a `floorId`; the dependent selects clear when the site changes so a floor cannot be submitted from another centre. |
| `assetDialogs` | 6, 7 | Register, edit and move. Moving is separate because it re-derives the readiness of both the space left and the space joined. |
| `deviceDialogs` | 9 | Register and edit. Neither offers a status: that belongs to the vendor feed, and a field for it would let this service assert an observation it has not made. |
| `zoneDialogs` | 8 | Add a zone, add a member. The member picker is scoped to the zone's own site, so the service's same-site refusal cannot be reached from the screen. |
| `checklistDialogs` | 12, 13 | Add and edit, with the item editor. Editing the items replaces every one and bumps the version, so the dialog tracks whether they were touched and omits the list when they were not. |
| `configurationDialogs` | 15 | Change a threshold, as a platform default or a site override. States the version being superseded and the one about to be written. |
| `common.LifecycleDialog` | 2, 4, 6, 8, 9 | Retire. Archiving is terminal and asks twice; inactive and suspended are reversible and do not. |

## Navigation

**Reordered 17 August 2026** to the sequence the work happens in, because nothing in this programme
can be done out of order: `Facility operations` (the dashboard, which is the landing) → `Estate
registers` → `Readiness` → `Maintenance` → `Room booking` → `Governance`. Readiness checklists moved
out of the old `Facility assurance` section to sit directly above assessments, since an assessment
against a site with no checklist records no answers.

Sections, all `programme: 'IFIMP', system: 'S152'` unless noted, in `shared/layout/navigation.ts`:

- **Facility operations** - dashboard, readiness assessments
- **Estate registers** - sites, spaces, facility assets, zones, device references
- **Facility assurance** - readiness checklists, audit & integrity, configuration

Each item carries the real service permission, so the sidebar narrows with the actor. A
`FACILITIES_MANAGER` sees ten of the twelve items (no audit, no configuration management); an
`IFIMP_TECHNICIAN` sees nine; a `FLEET_MANAGER` sees none of the three sections at all.

## Tests

`npm run test` - 44 tests, 4 files.

| File | Covers |
| --- | --- |
| `shared/layout/programmeModel.test.ts` | S152 entitlement per role, including the roles that must **not** get it |
| `modules/facilities/api/workflow.test.ts` | The critical-blocker rule, the examination lock, asset-to-blocker severity |
| `modules/facilities/components/facilitiesFormat.test.ts` | Tone mapping and the date/relative-time helpers |
| `modules/facilities/pages/FacilitiesDashboardPage.test.tsx` | Loading, error, stale-data warning, examination mode, restricted drilldown, empty states |
